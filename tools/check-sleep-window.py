#!/usr/bin/env python3
"""Exercise real v7→current Sleep schema changes with fictional SQLite data.

Timing/revision decisions belong to SleepPolicyTest; this checks the persistent
job snapshots they consume, and that upgrading never changes existing messages.
"""
from pathlib import Path
from store_migrations import upgrade_v14, upgrade_v11, assert_v11_defaults, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows
import re
import sqlite3
import tempfile

ROOT=Path(__file__).resolve().parents[1]
source=(ROOT/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
version=int(re.search(r'super\(c,"pilot.db",null,(\d+)\)',source).group(1))
assert version in (8,9,10,11,12,13,14), 'Review new schema changes before extending this test'


def sql_statements(text):
    return re.findall(r'db\.execSQL\("([^"]+)"\)',text)


def helper(name):
    return sql_statements(source.split(f'private static void {name}(SQLiteDatabase db){{',1)[1].split('\n    }',1)[0])


def schema(db):
    names=[row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return ({name:db.execute(f'PRAGMA table_info({name})').fetchall() for name in names},
            db.execute("SELECT name,tbl_name,sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall())


v8=re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}',source)
assert v8, 'Missing v8 migration'
additions=sql_statements(v8.group(1))
assert additions==[
    'ALTER TABLE jobs ADD COLUMN sleep_revision INTEGER NOT NULL DEFAULT 0',
    'ALTER TABLE jobs ADD COLUMN auto_delay INTEGER NOT NULL DEFAULT -1',
]

with tempfile.TemporaryDirectory(prefix='reply-pilot-sleep-sql-') as folder:
    path=Path(folder)/'fictional.db'
    db=sqlite3.connect(path)
    # Build shipped v7 from the fixed v5 fixture and the real intervening SQL.
    # Deriving the input from current CREATE TABLE would hide missing migrations.
    db.executescript((ROOT/'tools/fixtures/store-v5-schema.sql').read_text())
    v6=source.split('if(oldVersion<6){',1)[1].split('\n',1)[0]
    for statement in sql_statements(v6)+helper('createDeliveryReports')+helper('createReplyDecisions'):
        db.execute(statement)
    columns=[row[1] for row in db.execute('PRAGMA table_info(jobs)')]
    assert 'sleep_revision' not in columns and 'auto_delay' not in columns

    db.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay) VALUES(1,'Fictional friend',9,'Me: Fictional voice sample',1,1,'Friend','Natural',1,0)")
    db.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay) VALUES(2,'Fictional colleague',4,'Me: Different sample',1,1,'Coworker','Professional',1,900)")
    db.execute("INSERT INTO drafts VALUES(1,101,'Keep my manual edit','[]','Edited by you')")
    db.execute("INSERT INTO drafts VALUES(2,202,'Keep the reviewed suggestion','[\"Keep the reviewed suggestion\"]','OpenAI · awaiting your review')")
    for index,(automatic,status,delivery) in enumerate([
        (0,'scheduled','none'),(0,'sending','pending'),(0,'sent','delivered'),
        (1,'scheduled','none'),(1,'awaiting_alert','none'),(1,'sending','pending'),
        (1,'sent','delivered'),(1,'failed','failed'),(1,'unknown','unknown'),
        (1,'paused','none'),(1,'cancelled','none'),
    ],1):
        uri=f'content://sms/{1000+index}' if delivery!='none' else ''
        db.execute('INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,note,uri,parts,created,auto_send,profile_revision,config_revision,original,delivery_status,delivered_at,sms_date) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                   (index,1 if index%2 else 2,'+12025550100',f'Exact fictional text {index}',101 if index%2 else 202,1,1700000000000+index,1-automatic,status,f'Existing note {index}',uri,2 if uri else 0,1699999999999,automatic,9,'fictional-config','Fictional incoming',delivery,1700000000200 if delivery=='delivered' else 0,1700000000000+index if uri else 0))
    db.execute('INSERT INTO receipts VALUES(3,0,-1)')
    db.execute('INSERT INTO receipts VALUES(3,1,-1)')
    db.execute("INSERT INTO delivery_reports VALUES(3,0,'delivered',0,'3gpp',1700000000200)")
    db.execute("INSERT INTO delivery_reports VALUES(3,1,'delivered',0,'3gpp',1700000000200)")
    db.execute("INSERT INTO reply_decisions VALUES(2,201,'conversation_complete',1700000000000)")
    tables=['jobs','relationships','drafts','receipts','delivery_reports','reply_decisions']
    before_columns={name:[row[1] for row in db.execute(f'PRAGMA table_info({name})')] for name in tables}
    before={name:db.execute(f'SELECT * FROM {name} ORDER BY 1').fetchall() for name in tables}

    with db:
        for statement in additions:db.execute(statement)
        if version>=9:
            ranges=re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}',source)
            assert ranges, 'Expected guarded v9 relationship migration'
            for statement in sql_statements(ranges.group(1)):db.execute(statement)
        if version>=10:
            assert 'if(oldVersion<10)createReplyHolds(db);' in source
            for statement in helper('createReplyHolds'):db.execute(statement)
            assert db.execute('SELECT COUNT(*) FROM reply_holds').fetchone()==(0,)
        if version>=11:
            upgrade_v11(db,source,7)
            assert_v11_defaults(db)
        if version>=12:
            upgrade_v12(db,source)
            assert_v12_defaults(db)
            if version>=13:
                upgrade_v13(db,source,7)
                assert_v13_defaults(db)
                upgrade_v14(db,source)
        db.execute(f'PRAGMA user_version={version}')
    for name in tables:
        assert db.execute(f'SELECT {",".join(before_columns[name])} FROM {name} ORDER BY 1').fetchall()==before[name],name
    assert all(row==(0,-1) for row in db.execute('SELECT sleep_revision,auto_delay FROM jobs'))
    fresh=sqlite3.connect(':memory:')
    for statement in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)',source):fresh.execute(statement)
    assert schema(db)==schema(fresh), f'Fresh and upgraded v{version} schemas differ'
    fresh.close()

    # New manual jobs never acquire a Sleep revision. Each automatic job stores
    # both its exact session revision and the effective delay used when queued.
    insert='INSERT INTO jobs(thread,address,body,base,sub,due,status,created,auto_send,sleep_revision,auto_delay) VALUES(2,\'+12025550101\',?,202,1,1700001000000,\'scheduled\',1700000000000,?,?,?)'
    new_ids=[]
    for text,automatic,revision,delay in [('Manual default',0,0,-1),('Sleep timer',1,42,600),('Previous session timer',1,41,300),('Outside Sleep instant',1,43,0)]:
        new_ids.append(db.execute(insert,(text,automatic,revision,delay)).lastrowid)
    manual=db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,status,created,approved) VALUES(1,'+12025550102','Explicit manual timer',101,1,1700002000000,'scheduled',1700000000000,1)").lastrowid
    assert db.execute('SELECT auto_send,sleep_revision,auto_delay,approved FROM jobs WHERE _id=?',(manual,)).fetchone()==(0,0,-1,1)
    # Changing a person's default must not rewrite already captured job delays.
    db.execute('UPDATE relationships SET auto_delay=1800 WHERE thread=2')
    persisted=db.execute('SELECT _id,body,auto_send,sleep_revision,auto_delay FROM jobs WHERE _id IN (?,?,?,?) ORDER BY _id',new_ids).fetchall()
    assert [row[3:] for row in persisted]==[(0,-1),(42,600),(41,300),(43,0)]
    db.commit();db.close()
    db=sqlite3.connect(path)
    assert db.execute('PRAGMA user_version').fetchone()==(version,)
    assert db.execute('SELECT _id,body,auto_send,sleep_revision,auto_delay FROM jobs WHERE _id IN (?,?,?,?) ORDER BY _id',new_ids).fetchall()==persisted
    # Existing profiles were untouched by migration; this one deliberate test
    # update is distinct from the immutable queued-job effective delay above.
    assert db.execute('SELECT body,revision,samples,tone,auto_send,auto_delay FROM relationships WHERE thread=1').fetchone()==('Fictional friend',9,'Me: Fictional voice sample','Natural',1,0)
    assert original_rows(db,'drafts',before_columns['drafts'])==before['drafts']
    assert db.execute('SELECT * FROM delivery_reports ORDER BY 1').fetchall()==before['delivery_reports']
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    db.close()

print(f'PASS: real v7→v{version} preserves profiles, exact manual timers, drafts, delivery/decision state; old/manual jobs retain revision0 and delay-1; automatic revision/delay snapshots survive profile changes and reopen; fresh schema agrees. Timing rejection is covered separately by SleepPolicyTest.')
