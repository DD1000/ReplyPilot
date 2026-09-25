#!/usr/bin/env python3
"""Real v12→v14 migration and profile persistence with fictional contact records."""
from pathlib import Path
import re
import sqlite3
import tempfile
from store_migrations import upgrade_v14, upgrade_v11, upgrade_v12, upgrade_v13, assert_v13_defaults, original_rows

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
assert 'super(c,"pilot.db",null,14)' in source

def statements(text):
    return re.findall(r'db\.execSQL\("([^"]+)"\)', text)

def helper(name):
    return statements(source.split(f'private static void {name}(SQLiteDatabase db){{',1)[1].split('\n    }',1)[0])

def schema(db):
    tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return ({name:db.execute(f'PRAGMA table_info({name})').fetchall() for name in tables},
            db.execute("SELECT name,tbl_name,sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall())

save_profile = re.search(r'execSQL\("(INSERT INTO relationships\(thread,body,revision,samples[^"]+)"',source).group(1)
legacy = source.split('DelayPolicy.Choice choice,String engagement,boolean shareLocation) throws JSONException {',1)[1].split('\n    }',1)[0]
assert 'synchronized(PilotApp.SEND_LOCK)' in legacy
assert 'ContactHumor.level(prior.opt("humorLevel")),ContactHumor.notes(prior.opt("insideJokes"))' in legacy, 'Old call sites must preserve both saved preferences'
full = source.split('boolean shareLocation,int humorLevel,String insideJokes) throws JSONException {',1)[1].split('\n    }',1)[0]
assert full.index('ContactHumor.level(humorLevel)') < full.index('execSQL(')
assert full.index('ContactHumor.notes(insideJokes)') < full.index('execSQL(')
assert 'Sender.cancelAutomaticForThread' in full and 'revision=relationships.revision+1' in save_profile

with tempfile.TemporaryDirectory(prefix='reply-pilot-humor-sql-') as folder:
    path = Path(folder)/'fictional.db'
    db = sqlite3.connect(path)
    # Build the shipped v12 schema from a fixed historical fixture and actual
    # old migrations, never by deleting columns from today's CREATE schema.
    db.executescript((ROOT/'tools/fixtures/store-v5-schema.sql').read_text())
    v6 = source.split('if(oldVersion<6){',1)[1].split('\n',1)[0]
    v8 = re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}',source).group(1)
    v9 = re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}',source).group(1)
    for sql in statements(v6)+helper('createDeliveryReports')+helper('createReplyDecisions')+statements(v8)+statements(v9)+helper('createReplyHolds'):
        db.execute(sql)
    upgrade_v11(db,source,10)
    upgrade_v12(db,source)
    db.execute('PRAGMA user_version=12')
    assert 'humor_level' not in [row[1] for row in db.execute('PRAGMA table_info(relationships)')]
    for thread,enabled,engagement in [(1,1,'girlfriend'),(2,0,'natural')]:
        db.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay,auto_delay_mode,auto_delay_min,auto_delay_max,engagement,share_location) VALUES(?, 'Fictional relationship',9,'Me: fictional sample',?,?,'Friend','Natural',?,300,'range',300,1800,?,?)",(thread,enabled,enabled,enabled,engagement,enabled))
        db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine,location_revision,location_expires) VALUES(?,?,'Preserve draft','[]','Edited by you',42,1700001500000)",(thread,100*thread))
    db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,approved,status,created,auto_send,profile_revision,config_revision,uri,parts,delivery_status,delivered_at,sms_date,sleep_revision,auto_delay,location_revision,location_expires,attention_kind) VALUES(1,'+12025550100','Preserve exact automatic text',100,1,1700000600000,0,'scheduled',1700000000000,1,9,'fixture-config','',0,'none',0,0,7,721,42,1700001500000,'')")
    db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,approved,status,created,attention_kind,uri,parts,sms_date,delivery_status,delivered_at) VALUES(1,'+12025550100','Fixed acknowledgment',100,1,1700000600000,1,'sent',1700000000000,'delay','content://sms/101',1,1700000600000,'delivered',1700000700000)")
    db.execute('INSERT INTO receipts VALUES(2,0,-1)')
    db.execute("INSERT INTO delivery_reports VALUES(2,0,'delivered',0,'3gpp',1700000700000)")
    db.execute("INSERT INTO reply_decisions VALUES(1,100,'plans_need_input',1700000000000)")
    db.execute('INSERT INTO reply_holds VALUES(1,99,100,1700000000000)')
    for token,action,state,job in [('11111111-1111-1111-1111-111111111111','joke','offered',0),('22222222-2222-2222-2222-222222222222','delay','complete',2)]:
        db.execute("INSERT INTO attention_actions(token,thread,base,action,reason,profile_revision,config_revision,fingerprint,burst,state,created,job_id) VALUES(?,1,100,?,'plans_need_input',9,'fixture-config','fixture-fingerprint',12,?,1700000000000,?)",(token,action,state,job))
    tables = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
    columns = {name:[row[1] for row in db.execute(f'PRAGMA table_info({name})')] for name in tables}
    before = {name:original_rows(db,name,columns[name]) for name in tables}
    with db:
        upgrade_v13(db,source,12)
        assert_v13_defaults(db)
        upgrade_v14(db,source)
        db.execute('PRAGMA user_version=14')
    for name in tables:
        assert original_rows(db,name,columns[name]) == before[name],name
    fresh=sqlite3.connect(':memory:')
    for sql in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)',source):fresh.execute(sql)
    assert schema(db)==schema(fresh),'Fresh and upgraded v14 schemas differ'
    fresh.close()
    # Exercise the exact native upsert with distinct preferences, including the
    # full accepted note limit. No preference edit grants automatic-send consent.
    note='🦆\n'+('x'*1997)
    assert len(note.encode('utf-16-le'))//2==2000
    db.execute(save_profile,(1,'Fictional relationship','Me: fictional sample',1,1,'Friend','Natural',1,300,'range',300,1800,'girlfriend',1,4,note))
    db.execute(save_profile,(2,'Fictional relationship','Me: other sample',0,0,'Friend','Natural',0,300,'fixed',300,1800,'natural',0,1,'Different private joke'))
    assert db.execute('SELECT humor_level,inside_jokes,revision FROM relationships WHERE thread=1').fetchone()==(4,note,10)
    assert db.execute('SELECT humor_level,inside_jokes,cloud_enabled,auto_draft,auto_send FROM relationships WHERE thread=2').fetchone()==(1,'Different private joke',0,0,0)
    assert db.execute('SELECT profile_revision FROM attention_actions WHERE action=\'joke\'').fetchone()==(9,),'Preferences do not reauthorize an old action token'
    assert db.execute('SELECT profile_revision FROM jobs WHERE _id=1').fetchone()==(9,),'Pending jobs retain their old, now-invalid revision'
    for name in tables:
        if name!='relationships':assert original_rows(db,name,columns[name])==before[name],name
    db.commit();db.close()
    db=sqlite3.connect(path)
    assert db.execute('PRAGMA user_version').fetchone()==(14,)
    assert db.execute('SELECT humor_level,inside_jokes FROM relationships WHERE thread=1').fetchone()==(4,note)
    assert db.execute('SELECT humor_level,inside_jokes FROM relationships WHERE thread=2').fetchone()==(1,'Different private joke')
    # Explicitly clearing one person's preferences preserves the other person's.
    db.execute(save_profile,(1,'Fictional relationship','Me: fictional sample',1,1,'Friend','Natural',1,300,'range',300,1800,'girlfriend',1,0,''))
    assert db.execute('SELECT humor_level,inside_jokes,revision FROM relationships WHERE thread=1').fetchone()==(0,'',11)
    assert db.execute('SELECT humor_level,inside_jokes FROM relationships WHERE thread=2').fetchone()==(1,'Different private joke')
    db.close()
print('PASS: real v12→v14 preserves all contacts, opt-ins, drafts, jobs, attention tokens, receipts and holds; defaults are off/empty; schema matches fresh creation; actual profile saves persist bounded per-contact notes, advance only that revision and preserve consent, job snapshots and unrelated contacts across reopen. v1 guard is covered by historical profile checks; strict input types/bounds by ContactHumorTest.')
