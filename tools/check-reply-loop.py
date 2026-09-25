#!/usr/bin/env python3
"""Exercise the actual native schema/count SQL using fictional SQLite records."""
from pathlib import Path
from store_migrations import upgrade_v14, upgrade_v11, assert_v11_defaults, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows
import re
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/contentfoundry/replypilot"
store = (JAVA / "Store.java").read_text()
policy = (JAVA / "AutomaticReplyPolicy.java").read_text()
create = re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)', store)
version = int(re.search(r'super\(c,"pilot.db",null,(\d+)\)',store).group(1))
assert version in (7,8,9,10,11,12,13,14), 'Review any newer migration before extending this check'
v7 = [sql for sql in create if "reply_decisions" in sql or "jobs_thread_automatic" in sql]
assert len(v7) == 2
submitted = re.search(r'SUBMITTED_SQL="([^"]+)"', policy).group(1)
count_parts = re.search(r'COUNT_SQL="([^"]+)"\+SUBMITTED_SQL\+"([^"]+)"', policy)
count_sql = count_parts.group(1) + submitted + count_parts.group(2)
delete_draft = re.search(r'db\.delete\("drafts","([^"]*engine LIKE[^\"]*)"', store).group(1)


def schema(db):
    # ALTER TABLE formatting differs from CREATE TABLE even for equal schemas.
    tables=[row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    indexes=db.execute("SELECT name,tbl_name,sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall()
    return ({name:db.execute(f'PRAGMA table_info({name})').fetchall() for name in tables},indexes)


with tempfile.TemporaryDirectory(prefix="reply-pilot-loop-sql-") as folder:
    path = Path(folder) / "fictional.db"
    db = sqlite3.connect(path)
    # Construct actual v6 from the shipped v5 fixture and the v6 migration, not
    # from today's CREATE TABLE (which would prepopulate future columns).
    db.executescript((ROOT/'tools/fixtures/store-v5-schema.sql').read_text())
    v6=store.split('if(oldVersion<6){',1)[1].split('\n',1)[0]
    for sql in re.findall(r'db\.execSQL\("([^"]+)"\)',v6):db.execute(sql)
    delivery=store.split('private static void createDeliveryReports(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
    for sql in re.findall(r'db\.execSQL\("([^"]+)"\)',delivery):db.execute(sql)
    db.execute("INSERT INTO relationships(thread,body,revision) VALUES(9,'Fictional profile',3)")
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(9,90,'Keep this typed text','[]','Edited by you')")
    db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,status,created,delivery_status,delivered_at,sms_date) VALUES(9,'+15550001009','Fictional old message',90,1,100,'sent',100,'delivered',110,100)")
    db.execute("INSERT INTO receipts VALUES(1,0,-1)")
    db.execute("INSERT INTO delivery_reports VALUES(1,0,'delivered',0,'3gpp',110)")
    old_tables=["jobs", "drafts", "relationships", "receipts", "delivery_reports"]
    old_columns={table:[row[1] for row in db.execute(f'PRAGMA table_info({table})')] for table in old_tables}
    old = {table: db.execute(f"SELECT * FROM {table}").fetchall() for table in old_tables}
    for sql in v7:
        db.execute(sql)
    if version>=8:
        sleep=re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}',store)
        assert sleep, 'Expected explicit v8 migration block'
        additions=re.findall(r'db\.execSQL\("([^"]+)"\)',sleep.group(1))
        assert additions==['ALTER TABLE jobs ADD COLUMN sleep_revision INTEGER NOT NULL DEFAULT 0','ALTER TABLE jobs ADD COLUMN auto_delay INTEGER NOT NULL DEFAULT -1']
        for sql in additions:db.execute(sql)
        assert db.execute('SELECT sleep_revision,auto_delay FROM jobs').fetchone()==(0,-1)
    if version>=9:
        ranges=re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}',store)
        assert ranges, 'Expected guarded v9 relationship migration'
        additions=re.findall(r'db\.execSQL\("([^"]+)"\)',ranges.group(1))
        assert len(additions)==3
        for sql in additions:db.execute(sql)
        assert db.execute('SELECT auto_delay_mode,auto_delay_min,auto_delay_max FROM relationships').fetchone()==('fixed',300,1800)
    if version>=10:
        assert 'if(oldVersion<10)createReplyHolds(db);' in store
        holds=store.split('private static void createReplyHolds(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
        for sql in re.findall(r'db\.execSQL\("([^"]+)"\)',holds):db.execute(sql)
        assert db.execute('SELECT COUNT(*) FROM reply_holds').fetchone()==(0,)
    if version>=11:
        upgrade_v11(db,store,6)
        assert_v11_defaults(db)
    if version>=12:
        upgrade_v12(db,store)
        assert_v12_defaults(db)
        if version>=13:
            upgrade_v13(db,store,6)
            assert_v13_defaults(db)
            upgrade_v14(db,store)
    for table, rows in old.items():
        assert db.execute(f'SELECT {",".join(old_columns[table])} FROM {table}').fetchall() == rows, table
    fresh = sqlite3.connect(":memory:")
    for sql in create:
        fresh.execute(sql)
    assert schema(db) == schema(fresh), f"fresh and upgraded v{version} schemas differ"

    def job(thread=1, automatic=1, status="sent", uri="content://sms/100", delivery="none", attention=""):
        cursor = db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,status,created,auto_send,uri,delivery_status,attention_kind) VALUES(?, '+15550001001','Fictional reply',1001,1,1,?,1,?,?,?,?)", (thread, status, automatic, uri, delivery, attention))
        return cursor.lastrowid

    def count(thread=1, excluded=0):
        return db.execute(count_sql, (thread, excluded, thread)).fetchone()[0]

    for status, uri in [("sent", "content://sms/100"), ("sending", "content://sms/101"), ("unknown", "content://sms/102"), ("failed", "content://sms/103"), ("sent", "content://sms/104")]:
        job(status=status, uri=uri)
    assert count() == 5
    for status in ["scheduled", "awaiting_alert", "cancelled", "paused", "failed"]:
        job(status=status, uri="")
    assert count() == 5, "unsubmitted attempts must not consume the cap"
    job(thread=2, automatic=0)
    assert count() == 5, "another contact cannot reset the cap"
    for status, delivery in [("sent", "none"), ("sending", "pending"), ("failed", "delivered")]:
        job(automatic=0, status=status, delivery=delivery, attention="delay")
    assert count() == 5, "a notification deferral is not a substantive manual reply and cannot reset the cap"
    old_manual = job(automatic=0, status="scheduled", uri="")
    job(automatic=0, status="failed")
    assert count() == 5, "queued/failed manual attempts cannot reset the cap"
    job(automatic=0, status="sent")
    assert count() == 0
    job()
    db.execute("UPDATE jobs SET status='sent' WHERE _id=?", (old_manual,))
    assert count() == 1, "a late callback for an older manual job cannot erase a newer automatic submission"
    job(automatic=0, status="failed", delivery="delivered")
    assert count() == 0, "explicit manual delivery proof is a valid reset"
    job()
    current = job()
    assert count() == 2 and count(excluded=current) == 1
    # All jobs have ancient timestamps. Neither elapsed time nor reopening resets it.
    db.commit()
    db.close()
    db = sqlite3.connect(path)
    assert count() == 2

    db.execute("INSERT INTO reply_decisions VALUES(1,1001,'automatic_limit',1)")
    db.execute("INSERT OR REPLACE INTO reply_decisions VALUES(1,1001,'automatic_limit',2)")
    assert db.execute("SELECT COUNT(*) FROM reply_decisions WHERE thread=1 AND base=1001").fetchone()[0] == 1
    assert db.execute("SELECT reason FROM reply_decisions WHERE thread=1 AND base=1002").fetchone() is None
    assert db.execute("SELECT reason FROM reply_decisions WHERE thread=2 AND base=1001").fetchone() is None
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(1,1001,'Stale generated promise','[]','OpenAI · awaiting your review')")
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(2,1001,'Other person draft','[]','OpenAI · awaiting your review')")
    db.execute("DELETE FROM drafts WHERE " + delete_draft, (1, 1001))
    assert db.execute("SELECT body FROM drafts WHERE thread=1").fetchone() is None
    assert db.execute("SELECT body FROM drafts WHERE thread=2").fetchone() == ("Other person draft",)
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(1,1001,'Keep my edit','[]','Edited by you')")
    db.execute("DELETE FROM drafts WHERE " + delete_draft, (1, 1001))
    assert db.execute("SELECT body FROM drafts WHERE thread=1").fetchone() == ("Keep my edit",)
    db.execute("UPDATE drafts SET base=1000,engine='OpenAI · awaiting your review' WHERE thread=1")
    db.execute("DELETE FROM drafts WHERE " + delete_draft, (1, 1001))
    assert db.execute("SELECT body FROM drafts WHERE thread=1").fetchone() == ("Keep my edit",)
    db.commit()
    db.close()
    db = sqlite3.connect(path)
    assert db.execute("SELECT reason FROM reply_decisions WHERE thread=1 AND base=1001").fetchone() == ("automatic_limit",)
    assert count() == 2
    db.close()

print(f"PASS: actual v6→v{version} preserves data and matches fresh schema; cap/reset/thread isolation persist; silence preserves edited and unrelated drafts.")
