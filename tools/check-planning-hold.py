#!/usr/bin/env python3
"""Exercise actual v9→v14 migration/hold SQL with fictional SQLite records.

This is a persistence test, not an Android SMS simulation. The short Store
release guard is translated from its Java expression through a deliberately
restricted adapter; unexpected syntax fails closed. Native burst and multipart
policy tests cover callback freshness and aggregate carrier results separately.
"""
from pathlib import Path
from store_migrations import upgrade_v14, upgrade_v11, assert_v11_defaults, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows
import re
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/contentfoundry/replypilot'
source = (JAVA / 'Store.java').read_text()
sender = (JAVA / 'Sender.java').read_text()
assert 'super(c,"pilot.db",null,14)' in source, 'Review newer schema changes before extending this test'
assert 'if(oldVersion<10)createReplyHolds(db);' in source


def statements(text):
    return re.findall(r'(?:db|getWritableDatabase\(\))\.execSQL\("([^"]+)"', text)


def helper(name):
    return statements(source.split(f'private static void {name}(SQLiteDatabase db){{', 1)[1].split('\n    }', 1)[0])


def method(signature):
    return source.split(signature + '{', 1)[1].split('\n    }', 1)[0]


def schema(db):
    names = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return ({name: db.execute(f'PRAGMA table_info({name})').fetchall() for name in names},
            db.execute("SELECT name,tbl_name,sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall())


hold_sql = statements(method('public boolean holdReply(long thread,long base)'))
extend_sql = statements(method('public void extendReplyHold(long thread,long base)'))
release = method('public void clearReplyHoldForManual(JSONObject job)')
delete_sql = re.findall(r'getWritableDatabase\(\)\.delete\("(\w+)","([^"]+)"', release)
assert len(hold_sql) == len(extend_sql) == 1
assert [table for table, _ in delete_sql] == ['reply_holds', 'reply_decisions']
assert 'new Object[]{thread,base,base,System.currentTimeMillis()}' in source
assert 'new Object[]{base,thread}' in source
assert release.count('new String[]{Long.toString(thread),Long.toString(base)}') == 2

# Translate only the constructs used by the native early-return guard. Bind
# values instead of interpolating data; no Python/Java source is evaluated.
guard = re.search(r'if\((.+)\)return;', release).group(1)
guard = guard.replace('job==null', '(:missing=1)')
guard_values = {}


def string_equality(match):
    value, field = match.groups()
    assert field in ('status', 'delivery_status', 'attention_kind')
    name = f'literal_{len(guard_values)}'
    guard_values[name] = value
    return f'(:{name}=:{field})'


guard = re.sub(r'"([^"]+)"\.equals\(job\.optString\("(\w+)"\)\)', string_equality, guard)
guard = re.sub(r'job\.optInt\("(auto_send|approved)"\)', lambda match: ':' + match.group(1), guard)
guard = guard.replace('||', ' OR ').replace('&&', ' AND ')
guard = re.sub(r'!(?!=)', ' NOT ', guard)
remaining = re.sub(r':(?:missing|auto_send|approved|status|delivery_status|attention_kind|literal_\d+)\b|\b(?:AND|OR|NOT)\b|[\d\s()=!<>]', '', guard)
assert remaining == '', f'Unsupported release guard syntax: {remaining!r}'

# Both carrier entry points must still check the incoming-message boundary
# before even reaching the Store guard. Python supplies this gate explicitly;
# BurstPolicyTest exercises its real native timing/latest-base policy, including
# a successful retry after an older failed outgoing provider row.
calls = [line.strip() for line in sender.splitlines() if '.clearReplyHoldForManual(' in line]
assert len(calls) == 2
assert all(re.fullmatch(r'if\(IncomingBurst\.manualCoversLatest\(c,(?:j|job)\.optLong\("thread"\),(?:j|job)\.optLong\("base"\),(?:j|job)\.optString\("address"\)\)\).+\.clearReplyHoldForManual\(.+\);', line) for line in calls)

with tempfile.TemporaryDirectory(prefix='reply-pilot-planning-sql-') as folder:
    path = Path(folder) / 'fictional.db'
    db = sqlite3.connect(path)
    # Start with the shipped v5 fixture, then apply the real intervening SQL.
    # Today's CREATE statements cannot accidentally prepopulate the v10 table.
    db.executescript((ROOT / 'tools/fixtures/store-v5-schema.sql').read_text())
    v6 = source.split('if(oldVersion<6){', 1)[1].split('\n', 1)[0]
    v8 = re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}', source).group(1)
    v9 = re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}', source).group(1)
    for sql in statements(v6) + helper('createDeliveryReports') + helper('createReplyDecisions') + statements(v8) + statements(v9):
        db.execute(sql)
    db.execute('PRAGMA user_version=9')
    assert db.execute("SELECT name FROM sqlite_master WHERE name='reply_holds'").fetchone() is None
    db.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,auto_send,auto_delay,auto_delay_mode,auto_delay_min,auto_delay_max) VALUES(1,'Fictional friend',7,'Me: Sample voice',1,1,1,300,'range',300,1800)")
    db.execute("INSERT INTO drafts VALUES(1,101,'Preserve my manual draft','[]','Edited by you')")
    for index, (automatic, status, delivery, approved) in enumerate([
        (0, 'scheduled', 'none', 1), (1, 'scheduled', 'none', 0),
        (0, 'sent', 'delivered', 1), (1, 'failed', 'failed', 0),
    ], 1):
        db.execute('INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,created,auto_send,profile_revision,config_revision,original,uri,parts,delivery_status,delivered_at,sms_date,sleep_revision,auto_delay) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                   (index, 1, '+12025550100', f'Exact fictional reply {index}', 101, 1, 1700000600000, approved, status, 1700000000000, automatic, 7, 'fixture-config', 'Fictional incoming', f'content://sms/{index}' if status in ('sent', 'failed') else '', 1, delivery, 1700000610000 if delivery == 'delivered' else 0, 1700000000000, 8 if automatic else 0, 600 if automatic else -1))
    db.execute('INSERT INTO receipts VALUES(3,0,-1)')
    db.execute("INSERT INTO delivery_reports VALUES(3,0,'delivered',0,'3gpp',1700000610000)")
    db.execute("INSERT INTO reply_decisions VALUES(1,100,'conversation_complete',1700000000000)")
    tables = ['jobs', 'relationships', 'drafts', 'receipts', 'delivery_reports', 'reply_decisions']
    columns = {table:[row[1] for row in db.execute(f'PRAGMA table_info({table})')] for table in tables}
    before = {table: db.execute(f'SELECT * FROM {table} ORDER BY 1').fetchall() for table in tables}
    with db:
        for sql in helper('createReplyHolds'):
            db.execute(sql)
        db.execute('INSERT INTO reply_holds VALUES(77,700,705,1234)')
        upgrade_v11(db,source,9)
        assert_v11_defaults(db)
        assert db.execute('SELECT * FROM reply_holds').fetchall()==[(77,700,705,1234)], 'v11 preserves existing v10 holds'
        db.execute('DELETE FROM reply_holds WHERE thread=77')
        upgrade_v12(db,source)
        assert_v12_defaults(db)
        upgrade_v13(db,source,9)
        assert_v13_defaults(db)
        upgrade_v14(db,source)
        db.execute('PRAGMA user_version=14')
    assert db.execute('SELECT * FROM reply_holds').fetchall() == [], 'Migration must not invent holds'
    for table in tables:
        assert original_rows(db,table,columns[table]) == before[table], table
    fresh = sqlite3.connect(':memory:')
    for sql in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)', source):
        fresh.execute(sql)
    assert schema(db) == schema(fresh), 'Fresh and upgraded v14 schemas differ'
    fresh.close()

    def hold(thread=1):
        return db.execute('SELECT base,latest_base,created FROM reply_holds WHERE thread=?', (thread,)).fetchone()

    def add_hold(thread, base, created):
        db.execute(hold_sql[0], (thread, base, base, created))

    def release_callback(*, thread=1, base=105, automatic=0, approved=1, status='sent', delivery='none', current=True, missing=False, attention=''):
        values = {**guard_values, 'missing': int(missing), 'auto_send': automatic, 'approved': approved, 'status': status, 'delivery_status': delivery, 'attention_kind': attention}
        if not current or db.execute('SELECT (' + guard + ')', values).fetchone()[0]:
            return
        for table, where in delete_sql:
            db.execute(f'DELETE FROM {table} WHERE {where}', (thread, base))

    add_hold(1, 101, 1000)
    add_hold(2, 201, 2000)
    add_hold(1, 104, 3000)
    add_hold(1, 99, 4000)
    assert hold() == (101, 104, 1000), 'First planning message/time survive newer and stale planning decisions'
    db.execute(extend_sql[0], (105, 1))
    db.execute(extend_sql[0], (102, 1))
    db.execute(extend_sql[0], (999, 3))
    assert hold() == (101, 105, 1000) and hold(2) == (201, 201, 2000)
    assert hold(3) is None, 'A normal incoming message cannot create a hold'
    for thread, base, reason in [(1, 101, 'plans_need_input'), (1, 105, 'plans_need_input'), (1, 106, 'plans_need_input'), (1, 104, 'automatic_limit'), (2, 201, 'plans_need_input')]:
        db.execute('INSERT INTO reply_decisions VALUES(?,?,?,5000)', (thread, base, reason))
    db.commit()
    db.close()
    db = sqlite3.connect(path)
    assert db.execute('PRAGMA user_version').fetchone() == (14,)
    assert hold() == (101, 105, 1000), 'Hold and watermark must survive app/database reopen'

    # Failed, queued, unapproved, automatic, unrelated, unknown and stale
    # callback outcomes cannot release a person's hold or alter their draft.
    retained = [
        {'missing': True}, {'current': False}, {'automatic': 1},
        {'attention': 'delay'}, {'attention': 'delay', 'delivery': 'delivered'},
        {'automatic': 1, 'delivery': 'delivered'}, {'approved': 0},
        {'approved': 0, 'delivery': 'delivered'}, {'thread': 3},
        {'status': 'scheduled'}, {'status': 'sending', 'delivery': 'pending'},
        {'status': 'unknown', 'delivery': 'unknown'},
        {'status': 'failed', 'delivery': 'failed'}, {'status': 'failed', 'delivery': 'none'},
        {'base': 101}, {'base': 104}, {'base': 104, 'status': 'failed', 'delivery': 'delivered'},
    ]
    for case in retained:
        release_callback(**case)
        assert hold() == (101, 105, 1000), case
        assert hold(2) == (201, 201, 2000), case
        assert original_rows(db,'drafts',columns['drafts']) == before['drafts'], case
    # Stale callbacks may clear only stale planning decisions. The independent
    # contact hold and the newer decision remain authoritative.
    assert db.execute("SELECT reason FROM reply_decisions WHERE thread=1 AND base=105").fetchone() == ('plans_need_input',)
    release_callback()
    assert hold() is None, 'Approved manual carrier acceptance through latest_base releases the hold'
    assert hold(2) == (201, 201, 2000)
    assert db.execute("SELECT base FROM reply_decisions WHERE thread=1 AND reason='plans_need_input'").fetchall() == [(106,)]
    assert db.execute("SELECT reason FROM reply_decisions WHERE thread=1 AND base=104").fetchone() == ('automatic_limit',)
    assert db.execute("SELECT reason FROM reply_decisions WHERE thread=1 AND base=100").fetchone() == ('conversation_complete',)
    release_callback()  # Duplicate carrier callbacks are harmless.
    add_hold(1, 110, 6000)
    release_callback()  # A previous successful callback cannot release a new hold.
    assert hold() == (110, 110, 6000)
    release_callback(base=110, status='failed', delivery='delivered')
    assert hold() is None, 'Explicit aggregate delivery proof can override a late failed sent callback'
    add_hold(1, 120, 7000)
    db.execute(extend_sql[0], (121, 1))
    release_callback(base=120, status='sent', delivery='delivered')
    assert hold() == (120, 121, 7000), 'A newer incoming message must survive late success for the previous base'
    release_callback(base=121, status='sending', delivery='pending')
    assert hold() is not None, 'Partial/pending delivery cannot release the hold'
    release_callback(base=121, status='sending', delivery='delivered')
    assert hold() is None
    for table in ['jobs', 'relationships', 'drafts', 'receipts', 'delivery_reports']:
        assert original_rows(db,table,columns[table]) == before[table], table
    db.commit()
    db.close()
    db = sqlite3.connect(path)
    assert hold() is None and hold(2) == (201, 201, 2000), 'Release and unrelated holds persist independently'
    assert db.execute('PRAGMA integrity_check').fetchone() == ('ok',)
    db.close()

print('PASS: actual v9→v14 preserves all prior data and matches fresh schema; planning holds retain first message/time, extend the incoming watermark and survive reopen; actual release guard/SQL retain failed, automatic, unapproved and stale callbacks; only current approved manual sent/delivered proof releases through its base, without changing drafts or another contact.')
