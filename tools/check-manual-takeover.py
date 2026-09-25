#!/usr/bin/env python3
"""Exercise shipped SQL migration, selective takeover and durable request identity."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import sqlite3
import tempfile

root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/com/contentfoundry/replypilot'
store = (java / 'Store.java').read_text()
takeover = (java / 'ManualTakeover.java').read_text()
assert 'super(c,"pilot.db",null,18)' in store
assert 'if(oldVersion<17)createManualTakeover(db);' in store
assert 'createManualTakeover(db);' in store.split('private static void createPilotTraining', 1)[0]
block = store.split('private static void createManualTakeover(SQLiteDatabase db){', 1)[1].split('\n    }', 1)[0]
migration = re.findall(r'db.execSQL\("([^"]+)"\)', block)
assert len(migration) == 3
# Preserve the actual previous tables while adding only the new v17 tables.
names = ('jobs', 'drafts', 'relationships', 'attention_actions')
old_schema = [re.search(r'db.execSQL\("(CREATE TABLE ' + name + r'\s*\([^"\n]+)"\)', store).group(1) for name in names]
job_where = re.search(r'update\("jobs",stopped,"([^"]+)"', takeover).group(1)
action_where = re.search(r'update\("attention_actions",stale,"([^"]+)"', takeover).group(1)
draft_where = re.search(r'delete\("drafts","([^"]+)"', takeover).group(1)

with tempfile.TemporaryDirectory(prefix='pilot-manual-sql-') as tmp:
    path = Path(tmp) / 'fixture.db'
    db = sqlite3.connect(path)
    for sql in old_schema:
        db.execute(sql)
    db.execute("INSERT INTO relationships(thread,body,revision) VALUES(1,'Keep private guidance',8)")
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(1,40,'Pending AI','[]','OpenAI draft')")
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(2,50,'Keep owner text','[]','Edited by you')")
    # Scheduled/awaiting AI and plan replies stop; manual timers, another contact,
    # and carrier-submitted/terminal jobs must keep their truthful states.
    jobs = [(1,1,'scheduled',1,''),(2,1,'awaiting_alert',1,''),(3,1,'sending',1,''),
            (4,1,'sent',1,''),(5,1,'scheduled',0,''),(6,2,'scheduled',1,''),
            (7,1,'scheduled',0,'delay')]
    for ident, thread, status, automatic, attention in jobs:
        db.execute("INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,created,auto_send,attention_kind) VALUES(?,?,'+12025550100','Example',40,1,1000,1,?,1,?,?)", (ident,thread,status,automatic,attention))
    before = {table: db.execute(f'SELECT * FROM {table}').fetchall() for table in names}
    for sql in migration:
        db.execute(sql)
    assert before == {table: db.execute(f'SELECT * FROM {table}').fetchall() for table in names}
    for token, thread, state, job in [('queued',1,'queued',0),('running',1,'running',0),('submitted',1,'running',3),('other',2,'queued',0)]:
        db.execute("INSERT INTO attention_actions(token,thread,base,action,reason,profile_revision,config_revision,fingerprint,burst,state,created,job_id) VALUES(?,?,40,?,'plans_need_input',8,'config','fingerprint',1,?,1,?)",(token,thread,token,state,job))
    db.execute('INSERT INTO manual_takeovers(thread,revision,sms_id,sms_date,sms_signature,mms_id,mms_date,mms_signature,claimed_at) VALUES(1,1,40,1000,\'sms-signature\',12,900,\'mms-signature\',2000)')
    db.execute('INSERT INTO manual_takeovers(thread,revision,sms_id,sms_date,sms_signature,mms_id,mms_date,mms_signature,claimed_at) VALUES(2,4,50,1500,\'other-sms\',0,0,\'\',2000)')
    db.execute('UPDATE manual_takeovers SET sms_receipt=8,mms_receipt=12 WHERE thread=1')
    db.commit()
    # Failed transactions cannot partially pause sends or erase the owner's draft.
    db.execute('BEGIN IMMEDIATE')
    db.execute(f"UPDATE jobs SET status='paused',approved=0 WHERE {job_where}",(1,))
    db.execute(f'DELETE FROM drafts WHERE {draft_where}',(1,))
    db.rollback()
    assert db.execute('SELECT status FROM jobs WHERE _id=1').fetchone()==('scheduled',)
    assert db.execute('SELECT body FROM drafts WHERE thread=1').fetchone()==('Pending AI',)
    db.execute('BEGIN IMMEDIATE')
    db.execute(f"UPDATE jobs SET status='paused',approved=0 WHERE {job_where}",(1,))
    db.execute(f"UPDATE attention_actions SET state='stale' WHERE {action_where}",(1,))
    db.execute(f'DELETE FROM drafts WHERE {draft_where}',(1,))
    db.commit()
    assert db.execute('SELECT _id,status FROM jobs ORDER BY _id').fetchall()==[(1,'paused'),(2,'paused'),(3,'sending'),(4,'sent'),(5,'scheduled'),(6,'scheduled'),(7,'paused')]
    assert dict(db.execute('SELECT token,state FROM attention_actions'))==dict(queued='stale',running='stale',submitted='running',other='queued')
    assert db.execute('SELECT thread,body FROM drafts').fetchall()==[(2,'Keep owner text')]
    db.close()
    # Parallel reuse of one tap identity cannot create multiple durable requests.
    request = '00000000-0000-4000-8000-000000000001'
    def reserve(_):
        conn = sqlite3.connect(path,timeout=10)
        try:
            conn.execute('BEGIN IMMEDIATE')
            conn.execute("INSERT INTO manual_sends(request_id,thread,address,body,sub,job_id,created) VALUES(?,1,'+12025550100','Manual reply',1,5,2000)",(request,))
            conn.commit()
            return 1
        except sqlite3.IntegrityError:
            conn.rollback()
            return 0
        finally:
            conn.close()
    with ThreadPoolExecutor(max_workers=6) as pool:
        assert sum(pool.map(reserve,range(6)))==1
    db = sqlite3.connect(path)
    assert db.execute('SELECT revision,sms_id,mms_id FROM manual_takeovers WHERE thread=1').fetchone()==(1,40,12)
    assert db.execute('SELECT sms_receipt,mms_receipt FROM manual_takeovers WHERE thread=1').fetchone()==(8,12)
    assert db.execute('SELECT revision FROM manual_takeovers WHERE thread=2').fetchone()==(4,)
    assert db.execute('SELECT job_id,body FROM manual_sends WHERE request_id=?',(request,)).fetchone()==(5,'Manual reply')
    # Identical text is allowed when it is a separate deliberate tap identity.
    db.execute("INSERT INTO manual_sends(request_id,thread,address,body,sub,job_id,created) SELECT '00000000-0000-4000-8000-000000000002',thread,address,body,sub,6,created FROM manual_sends WHERE request_id=?",(request,))
    assert db.execute('SELECT COUNT(*) FROM manual_sends').fetchone()==(2,)
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    db.close()
print('PASS: v16→17 preserves existing data; manual takeover cancels only unsent AI/plan work; rollback, restart, per-contact state and concurrent request identity persist correctly.')
