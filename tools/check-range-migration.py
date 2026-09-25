#!/usr/bin/env python3
"""Exercise the v8→current profile migration and persisted chosen-delay snapshots.

Only fictional SQLite data is used. Java policy tests cover range validation,
random endpoint selection and Sleep rejection; this test never samples a timer.
"""
from pathlib import Path
from store_migrations import upgrade_v14, upgrade_v11, assert_v11_defaults, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows
import re
import sqlite3
import tempfile

ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
version=int(re.search(r'super\(c,"pilot.db",null,(\d+)\)',source).group(1))
assert version in (9,10,11,12,13,14), 'Review any newer schema before extending this check'


def statements(text):
    return re.findall(r'db\.execSQL\("([^"]+)"\)',text)


def helper(name):
    return statements(source.split(f'private static void {name}(SQLiteDatabase db){{',1)[1].split('\n    }',1)[0])


def schema(db):
    tables=[row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return ({table:db.execute(f'PRAGMA table_info({table})').fetchall() for table in tables},
            db.execute("SELECT name,tbl_name,sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall())


guard=re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}',source)
assert guard, 'v1 already creates the latest relationship table; v9 ALTER must skip it'
upgrade=statements(guard.group(1))
assert upgrade==[
    "ALTER TABLE relationships ADD COLUMN auto_delay_mode TEXT NOT NULL DEFAULT 'fixed'",
    'ALTER TABLE relationships ADD COLUMN auto_delay_min INTEGER NOT NULL DEFAULT 300',
    'ALTER TABLE relationships ADD COLUMN auto_delay_max INTEGER NOT NULL DEFAULT 1800',
]
save_profile=re.search(r'execSQL\("(INSERT INTO relationships\(thread,body,revision,samples[^"]+)"',source).group(1)

with tempfile.TemporaryDirectory(prefix='reply-pilot-range-sql-') as folder:
    path=Path(folder)/'fictional.db'
    db=sqlite3.connect(path)
    db.executescript((ROOT/'tools/fixtures/store-v5-schema.sql').read_text())
    v6=source.split('if(oldVersion<6){',1)[1].split('\n',1)[0]
    v8=re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}',source)
    for statement in statements(v6)+helper('createDeliveryReports')+helper('createReplyDecisions')+statements(v8.group(1)):
        db.execute(statement)
    assert 'auto_delay_mode' not in [row[1] for row in db.execute('PRAGMA table_info(relationships)')]
    for thread,delay,enabled in [(1,300,1),(2,0,1),(3,900,1),(4,600,0)]:
        db.execute('INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay) VALUES(?,?,?,?,?,?,?,?,?,?)',
                   (thread,f'Fictional profile {thread}',7,f'Me: Voice sample {thread}',enabled,enabled,'Friend','Natural',enabled,delay))
    db.execute("INSERT INTO drafts VALUES(1,101,'Preserve exact typed text','[]','Edited by you')")
    db.execute("INSERT INTO drafts VALUES(2,202,'Preserve AI draft','[]','OpenAI · awaiting your review')")
    for index,(thread,automatic,delay,status,approved) in enumerate([
        (1,0,-1,'scheduled',1),(2,1,0,'scheduled',0),(3,1,900,'scheduled',0),
        (1,1,300,'awaiting_alert',0),(3,1,600,'sent',0),(2,0,-1,'sent',1),
    ],1):
        actual=600 if delay<0 else delay
        db.execute('INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,created,auto_send,profile_revision,config_revision,original,uri,parts,delivery_status,delivered_at,sms_date,sleep_revision,auto_delay) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                   (index,thread,'+12025550100',f'Exact queued body {index}',thread*101,1,1700000000000+actual*1000,approved,status,1700000000000,automatic,7,'fictional-config','Fictional incoming',f'content://sms/{index}' if status=='sent' else '',1 if status=='sent' else 0,'delivered' if status=='sent' else 'none',1700001000000 if status=='sent' else 0,1700000000000 if status=='sent' else 0,12 if automatic else 0,delay))
    db.execute('INSERT INTO receipts VALUES(5,0,-1)')
    db.execute("INSERT INTO delivery_reports VALUES(5,0,'delivered',0,'3gpp',1700001000000)")
    db.execute("INSERT INTO reply_decisions VALUES(4,403,'insufficient_history',1700000000000)")
    tables=['jobs','relationships','drafts','receipts','delivery_reports','reply_decisions']
    columns={table:[row[1] for row in db.execute(f'PRAGMA table_info({table})')] for table in tables}
    before={table:db.execute(f'SELECT * FROM {table} ORDER BY 1').fetchall() for table in tables}
    with db:
        for statement in upgrade:db.execute(statement)
        if version>=10:
            assert 'if(oldVersion<10)createReplyHolds(db);' in source
            for statement in helper('createReplyHolds'):db.execute(statement)
            assert db.execute('SELECT COUNT(*) FROM reply_holds').fetchone()==(0,)
        if version>=11:
            upgrade_v11(db,source,8)
            assert_v11_defaults(db)
        if version>=12:
            upgrade_v12(db,source)
            assert_v12_defaults(db)
            if version>=13:
                upgrade_v13(db,source,8)
                assert_v13_defaults(db)
                upgrade_v14(db,source)
        db.execute(f'PRAGMA user_version={version}')
    for table in tables:
        assert db.execute(f'SELECT {",".join(columns[table])} FROM {table} ORDER BY 1').fetchall()==before[table],table
    assert db.execute('SELECT auto_delay_mode,auto_delay_min,auto_delay_max FROM relationships ORDER BY thread').fetchall()==[('fixed',300,1800)]*4
    assert db.execute('SELECT auto_delay FROM relationships WHERE thread=2').fetchone()==(0,), 'Autopilot must remain immediate'
    fresh=sqlite3.connect(':memory:')
    for statement in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)',source):fresh.execute(statement)
    assert schema(db)==schema(fresh), f'Fresh and upgraded v{version} schemas differ'
    fresh.close()

    # Exercise the actual profile upsert: range changes belong only to the
    # selected person and advance its revision, invalidating old queued work.
    db.execute(save_profile,(1,'Fictional profile 1','Me: Voice sample 1',1,1,'Friend','Natural',1,300,'range',300,1800)+(('natural',0) if version>=11 else ()) + ((0,'') if version>=13 else ()))
    assert db.execute('SELECT revision,auto_delay_mode,auto_delay_min,auto_delay_max FROM relationships WHERE thread=1').fetchone()==(8,'range',300,1800)
    assert db.execute('SELECT revision,auto_delay,auto_delay_mode FROM relationships WHERE thread=2').fetchone()==(7,0,'fixed')
    assert original_rows(db,'jobs',columns['jobs'])==before['jobs'], 'Saving a range must not reroll existing jobs'
    assert db.execute('SELECT profile_revision FROM jobs WHERE _id=4').fetchone()==(7,), 'Old queue revision must remain stale'

    # The picker is tested in Java. Here its illustrative 721-second outcome is
    # stored as an exact snapshot, not rounded back to a whole-minute endpoint.
    chosen=721
    queued=db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,status,created,auto_send,profile_revision,sleep_revision,auto_delay) VALUES(1,'+12025550100','One picked reply',101,1,?,'scheduled',1700000000000,1,8,12,?)",(1700000000000+chosen*1000,chosen)).lastrowid
    expected=db.execute('SELECT due,auto_delay,profile_revision,sleep_revision FROM jobs WHERE _id=?',(queued,)).fetchone()
    assert expected==(1700000721000,721,8,12)
    db.execute(save_profile,(1,'Fictional profile 1','Me: Voice sample 1',1,1,'Friend','Natural',1,300,'range',60,600)+(('natural',0) if version>=11 else ()) + ((0,'') if version>=13 else ()))
    assert db.execute('SELECT revision FROM relationships WHERE thread=1').fetchone()==(9,)
    assert db.execute('SELECT due,auto_delay,profile_revision,sleep_revision FROM jobs WHERE _id=?',(queued,)).fetchone()==expected
    # Returning to fixed mode preserves the configured range for later, while a
    # saved Autopilot profile and unrelated manual timers remain unchanged.
    db.execute(save_profile,(1,'Fictional profile 1','Me: Voice sample 1',1,1,'Friend','Natural',1,900,'fixed',60,600)+(('natural',0) if version>=11 else ()) + ((0,'') if version>=13 else ()))
    assert db.execute('SELECT revision,auto_delay,auto_delay_mode FROM relationships WHERE thread=1').fetchone()==(10,900,'fixed')
    if version>=11:
        # Location provenance is an immutable queue snapshot like picked delay.
        # Its expiry can predate due: native Sender/LocationPolicy must then hold
        # this job rather than rewrite the timer or send stale location details.
        location_revision,location_expires=42,1700000300000
        db.execute('UPDATE drafts SET location_revision=?,location_expires=? WHERE thread=1',(location_revision,location_expires))
        db.execute('UPDATE jobs SET location_revision=?,location_expires=? WHERE _id=?',(location_revision,location_expires,queued))
        provenance=db.execute('SELECT due,auto_delay,location_revision,location_expires FROM jobs WHERE _id=?',(queued,)).fetchone()
        assert provenance==(expected[0],chosen,42,1700000300000) and provenance[-1]<provenance[0]
        # Fresh drafts and personal preference edits cannot re-authorize an
        # already queued location response under another location revision.
        db.execute('UPDATE drafts SET location_revision=43,location_expires=1700000400000 WHERE thread=1')
        db.execute(save_profile,(1,'Fictional profile 1','Me: Voice sample 1',1,1,'Friend','Myself (beta)',1,900,'fixed',60,600,'always_reply',1)+((0,'') if version>=13 else ()))
        assert db.execute('SELECT engagement,share_location,tone FROM relationships WHERE thread=1').fetchone()==('always_reply',1,'Myself (beta)')
        assert db.execute('SELECT engagement,share_location FROM relationships WHERE thread=2').fetchone()==('natural',0)
        assert db.execute('SELECT due,auto_delay,location_revision,location_expires FROM jobs WHERE _id=?',(queued,)).fetchone()==provenance
        # Store's two queue constructors copy this metadata only for the exact
        # stored draft body; this source contract complements the SQL persistence
        # check, without pretending Python executed Android ContentValues.
        for signature in ['public long queue(', 'public long queueAutomatic(']:
            method=source.split(signature,1)[1].split('\n    }',1)[0]
            assert 'if(source!=null&&body.equals(source.optString("body")))' in method
            assert 'v.put("location_revision",source.optLong("location_revision"))' in method
            assert 'v.put("location_expires",source.optLong("location_expires"))' in method
    db.commit();db.close()
    db=sqlite3.connect(path)
    assert db.execute('PRAGMA user_version').fetchone()==(version,)
    assert db.execute('SELECT due,auto_delay,profile_revision,sleep_revision FROM jobs WHERE _id=?',(queued,)).fetchone()==expected
    assert original_rows(db,'jobs',columns['jobs'])[:6]==before['jobs']
    for table in ['drafts','receipts','delivery_reports','reply_decisions']:
        assert original_rows(db,table,columns[table])==before[table],table
    if version>=11:
        assert db.execute('SELECT due,auto_delay,location_revision,location_expires FROM jobs WHERE _id=?',(queued,)).fetchone()==provenance
        assert db.execute('SELECT location_revision,location_expires FROM drafts WHERE thread=1').fetchone()==(43,1700000400000)
        assert db.execute('SELECT engagement,share_location,tone FROM relationships WHERE thread=1').fetchone()==('always_reply',1,'Myself (beta)')
        assert db.execute('SELECT location_revision,location_expires FROM jobs WHERE _id=1').fetchone()==(0,0), 'Old manual timers never acquire location authority'
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    db.close()

print(f'PASS: actual v8→v{version} preserves fixed/Autopilot profiles, exact manual/automatic jobs, drafts and delivery state; new opt-ins default off; profile changes advance revisions; chosen delay/due and location revision/expiry snapshots never change after save or reopen. Native policies separately enforce expiry.')
