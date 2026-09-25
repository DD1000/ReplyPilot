"""Exercise shipped v5 data through the current delivery/decision/sleep migrations."""
from pathlib import Path
from store_migrations import upgrade_v14, upgrade_v11, assert_v11_defaults, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows
import re, sqlite3, zipfile
root=Path(__file__).resolve().parents[1]
previous=root/'dist/Reply-Pilot-source.zip'
# Keep a test fixture of the actual previous schema, not a recreated approximation.
fixture=root/'tools/fixtures/store-v5-schema.sql'
if not fixture.exists():
    with zipfile.ZipFile(previous) as archive:
        old=archive.read('reply-pilot/app/src/main/java/com/contentfoundry/replypilot/Store.java').decode()
    assert 'super(c,"pilot.db",null,5)' in old, 'Need the preceding v5 source archive'
    statements=re.findall(r'db\.execSQL\("(CREATE TABLE [^"\n]+)"\)',old)
    assert len(statements)==4
    fixture.parent.mkdir(exist_ok=True)
    fixture.write_text('\n'.join(statement+';' for statement in statements)+'\n')
source=(root/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
version=int(re.search(r'super\(c,"pilot.db",null,(\d+)\)',source).group(1))
assert version in (6,7,8,9,10,11,12,13,14), 'Add checks for any newer migration before changing this assertion'
migration=source.split('if(oldVersion<6){',1)[1].split('\n',1)[0]
alters=re.findall(r'db\.execSQL\("([^"]+)"\)',migration)
helper=source.split('private static void createDeliveryReports(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
creates=re.findall(r'db\.execSQL\("([^"]+)"\)',helper)
assert len(alters)>=2 and any('delivery_reports' in statement for statement in creates)
connection=sqlite3.connect(':memory:')
connection.executescript(fixture.read_text())
for index,status in enumerate(['scheduled','sent','sending','failed','unknown'],1):
    connection.execute("INSERT INTO jobs(thread,address,body,base,sub,due,approved,status,created,auto_send,profile_revision,config_revision,original) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",(index,'+12025550100','Fictional reply',10,1,5000,1,status,1000,1,2,'fixture','Fictional incoming'))
connection.execute("INSERT INTO drafts VALUES(1,10,'Keep this draft','[]','Edited by you')")
connection.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay) VALUES(1,'Fictional friend',2,'Me: hi',1,1,'Friend','Natural',1,300)")
connection.execute('INSERT INTO receipts VALUES(2,0,-1)')
tables=['jobs','drafts','relationships','receipts']
columns={table:[column[1] for column in connection.execute(f'PRAGMA table_info({table})')] for table in tables}
before={table:connection.execute(f'SELECT {",".join(columns[table])} FROM {table}').fetchall() for table in tables}
with connection:
    for statement in alters+creates:connection.execute(statement)
    if version>=7:
        decisions=source.split('private static void createReplyDecisions(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
        for statement in re.findall(r'db\.execSQL\("([^"]+)"\)',decisions):connection.execute(statement)
    if version>=8:
        sleep=re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}',source)
        assert sleep, 'Expected explicit v8 migration block'
        additions=re.findall(r'db\.execSQL\("([^"]+)"\)',sleep.group(1))
        assert additions==['ALTER TABLE jobs ADD COLUMN sleep_revision INTEGER NOT NULL DEFAULT 0','ALTER TABLE jobs ADD COLUMN auto_delay INTEGER NOT NULL DEFAULT -1']
        for statement in additions:connection.execute(statement)
    if version>=9:
        ranges=re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}',source)
        assert ranges, 'Expected guarded v9 relationship migration'
        additions=re.findall(r'db\.execSQL\("([^"]+)"\)',ranges.group(1))
        assert len(additions)==3
        for statement in additions:connection.execute(statement)
    if version>=10:
        assert 'if(oldVersion<10)createReplyHolds(db);' in source
        holds=source.split('private static void createReplyHolds(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
        for statement in re.findall(r'db\.execSQL\("([^"]+)"\)',holds):connection.execute(statement)
        assert connection.execute('SELECT COUNT(*) FROM reply_holds').fetchone()==(0,)
    if version>=11:
        upgrade_v11(connection,source,5)
        assert_v11_defaults(connection)
    if version>=12:
        upgrade_v12(connection,source)
        assert_v12_defaults(connection)
        if version>=13:
            upgrade_v13(connection,source,5)
            assert_v13_defaults(connection)
            upgrade_v14(connection,source)
for table in tables:
    assert connection.execute(f'SELECT {",".join(columns[table])} FROM {table}').fetchall()==before[table],table
assert all(status=='none' and when==0 for status,when in connection.execute('SELECT delivery_status,delivered_at FROM jobs'))
if 'sms_date' in [column[1] for column in connection.execute('PRAGMA table_info(jobs)')]:
    assert all(row[0]==0 for row in connection.execute('SELECT sms_date FROM jobs'))
if version>=8:
    assert all(row==(0,-1) for row in connection.execute('SELECT sleep_revision,auto_delay FROM jobs'))
if version>=9:
    assert connection.execute('SELECT auto_delay_mode,auto_delay_min,auto_delay_max FROM relationships').fetchone()==('fixed',300,1800)
connection.execute("INSERT INTO delivery_reports VALUES(2,0,'pending',32,'3gpp',6000)")
try:
    connection.execute("INSERT INTO delivery_reports VALUES(2,0,'delivered',0,'3gpp',7000)")
except sqlite3.IntegrityError:pass
else:raise AssertionError('Part reports must be unique per job')
connection.execute("INSERT OR REPLACE INTO delivery_reports VALUES(2,0,'delivered',0,'3gpp',7000)")
assert connection.execute('SELECT COUNT(*) FROM delivery_reports WHERE job=2').fetchone()[0]==1
assert connection.execute('SELECT status FROM jobs WHERE _id=2').fetchone()[0]=='sent'
fresh=sqlite3.connect(':memory:')
for statement in re.findall(r'db\.execSQL\("(CREATE (?:TABLE|INDEX) [^"\n]+)"\)',source):fresh.execute(statement)
for table in tables+['delivery_reports']+(['reply_decisions'] if version>=7 else [])+(['reply_holds'] if version>=10 else [])+(['attention_actions'] if version>=12 else []):
    assert connection.execute(f'PRAGMA table_info({table})').fetchall()==fresh.execute(f'PRAGMA table_info({table})').fetchall(),table
assert connection.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
print(f'PASS: actual v5→v{version} SQL preserves drafts, profiles, send states and receipts; old jobs have no fabricated delivery status; part identity is unique; fresh and upgraded schemas agree.')
