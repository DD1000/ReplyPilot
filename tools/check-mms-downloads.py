#!/usr/bin/env python3
"""Exercise actual incoming-MMS SQLite schema and compare-and-set clauses, without Android or messages."""
from pathlib import Path
import re
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/com/contentfoundry/replypilot/MmsDownloads.java"
source = SOURCE.read_text()
create = re.findall(r'db\.execSQL\("([^"]+)"\)', source)
migration = next(statement for statement in create if statement.startswith("ALTER TABLE downloads ADD COLUMN receipt_token "))
create = [statement for statement in create if statement.startswith("CREATE ")]
assert len(create) == 3, "Review incoming schema changes before updating this harness"

def clause(value):
    assert '"' + value + '"' in source, "Production claim changed: " + value
    return value

start = clause("fingerprint=? AND state IN ('pending','failed','interrupted')")
callback = clause("token=? AND state IN ('downloading','interrupted')")
repair = clause("token=? AND state IN ('failed','interrupted')")
adoption = clause("fingerprint=? AND notice_id=0")
acknowledgment = clause("fingerprint=? AND state='downloaded' AND ack_state=''")
recovery = clause("notice_id=0 OR state IN ('pending','downloading','processing') OR ack_state='sending' OR ready_hash<>''")

with tempfile.TemporaryDirectory(prefix="reply-pilot-incoming-sql-") as temporary:
    # Upgrade keeps active carrier work and records unknown legacy receipt order
    # conservatively; it cannot manufacture evidence of a new incoming message.
    legacy = sqlite3.connect(Path(temporary) / "legacy.db")
    for statement in create:
        legacy.execute(statement.replace(", receipt_token INTEGER NOT NULL DEFAULT -1", ""))
    legacy.execute("INSERT INTO downloads(fingerprint,notice_id,thread,notice_date,location,transaction_id,sender,sub,state,token,updated) VALUES('existing',17,3,1234,'http://mmsc.example/item','tx','+15555550001',2,'downloading','existing-carrier-token',1000)")
    legacy.execute(migration)
    assert legacy.execute("SELECT state,token,notice_id,receipt_token FROM downloads").fetchone() == ("downloading", "existing-carrier-token", 17, -1)
    assert legacy.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    legacy.close()

    path = Path(temporary) / "incoming.db"
    db = sqlite3.connect(path)
    for statement in create:
        db.execute(statement)
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    def insert(fingerprint, notice):
        db.execute("INSERT INTO downloads(fingerprint,notice_id,thread,notice_date,location,transaction_id,sender,sub,state,updated) VALUES(?,?,?,?,?,?,?,?,?,?)",
                   (fingerprint, notice, 3, 1234, "http://mmsc.example/item", "tx", "+15555550001", 2, "pending", 1000))
    insert("notice-a", 17)
    db.execute("UPDATE downloads SET receipt_token=7 WHERE fingerprint='notice-a'")
    for fingerprint, notice in [("notice-a", 18), ("notice-b", 17)]:
        try:
            insert(fingerprint, notice)
            raise AssertionError("Duplicate notice accepted")
        except sqlite3.IntegrityError:
            pass
    assert db.execute("UPDATE downloads SET state='downloading',token='first' WHERE " + start, ("notice-a",)).rowcount == 1
    assert db.execute("UPDATE downloads SET state='downloading',token='duplicate' WHERE " + start, ("notice-a",)).rowcount == 0
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + callback, ("foreign",)).rowcount == 0
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + callback, ("first",)).rowcount == 1
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + callback, ("first",)).rowcount == 0
    db.execute("UPDATE downloads SET expected_parts=2,retrieve_id='message-a',ready_hash='digest' WHERE fingerprint='notice-a'")
    db.commit()
    db.close()

    # A fresh process finds interrupted save evidence; it does not issue a carrier request.
    db = sqlite3.connect(path)
    assert db.execute("SELECT notice_id,thread,receipt_token FROM downloads WHERE fingerprint='notice-a'").fetchone() == (17, 3, 7)
    rows = db.execute("SELECT fingerprint,state,expected_parts,retrieve_id,ready_hash FROM downloads WHERE " + recovery).fetchall()
    assert rows == [("notice-a", "processing", 2, "message-a", "digest")]
    db.execute("UPDATE downloads SET state='interrupted' WHERE fingerprint='notice-a'")
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + repair, ("first",)).rowcount == 1
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + repair, ("first",)).rowcount == 0
    assert db.execute("SELECT expected_parts,retrieve_id,ready_hash FROM downloads WHERE fingerprint='notice-a'").fetchone() == (2, "message-a", "digest")

    # A new explicit network attempt replaces the capability. Late callbacks cannot claim it.
    db.execute("UPDATE downloads SET state='failed' WHERE fingerprint='notice-a'")
    assert db.execute("UPDATE downloads SET state='downloading',token='second' WHERE " + start, ("notice-a",)).rowcount == 1
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + callback, ("first",)).rowcount == 0
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + callback, ("second",)).rowcount == 1
    db.execute("UPDATE downloads SET state='downloaded' WHERE fingerprint='notice-a'")
    assert db.execute("UPDATE downloads SET ack_state='sending',ack_token='ack-first' WHERE " + acknowledgment, ("notice-a",)).rowcount == 1
    assert db.execute("UPDATE downloads SET ack_state='sending',ack_token='ack-second' WHERE " + acknowledgment, ("notice-a",)).rowcount == 0
    assert db.execute("UPDATE downloads SET state='processing' WHERE " + callback, ("second",)).rowcount == 0

    # Provider insertion and private-store binding are separate: exact adoption happens once.
    insert("unbound", 0)
    assert db.execute("UPDATE downloads SET notice_id=22,state='interrupted' WHERE " + adoption, ("unbound",)).rowcount == 1
    assert db.execute("UPDATE downloads SET notice_id=23 WHERE " + adoption, ("unbound",)).rowcount == 0
    assert db.execute("SELECT COUNT(*) FROM downloads").fetchone()[0] == 2
    assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
    db.close()

print("Incoming MMS SQLite checks passed: duplicate notice/start, callback replay, stale token, persisted repair, one-shot acknowledgment and interrupted binding.")
print("This does not exercise an Android provider, actual PDUs, carrier network, permissions or process lifecycle.")
