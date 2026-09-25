#!/usr/bin/env python3
"""Exercise the actual v14 guidance schema and native pruning/forget SQL.

All records are fictional. This uses a temporary SQLite database, never phone
messages, local pairing credentials, production data, or a network service.
"""
from pathlib import Path
import re
import sqlite3
import tempfile
from store_migrations import upgrade_v14

ROOT = Path(__file__).resolve().parents[1]
STORE = (ROOT / 'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
NATIVE = (ROOT / 'app/src/main/java/com/contentfoundry/replypilot/PilotTraining.java').read_text()
POLICY = (ROOT / 'app/src/main/java/com/contentfoundry/replypilot/PilotTrainingPolicy.java').read_text()
assert 'super(c,"pilot.db",null,14)' in STORE
limits = dict((key, int(value)) for key, value in re.findall(r'\b(EXAMPLES|MEANINGS)=(\d+)', POLICY))
assert limits == {'EXAMPLES': 24, 'MEANINGS': 40}


def trim_sql(table, limit):
    pattern = rf'db\.execSQL\("(DELETE FROM {table} [^"]+)"\+PilotTrainingPolicy\.{limit}\+"([^"]+)"'
    match = re.search(pattern, NATIVE)
    assert match, f'Expected actual bounded pruning SQL for {table}'
    return match.group(1) + str(limits[limit]) + match.group(2)


TRIM_PRACTICE = trim_sql('pilot_training', 'EXAMPLES')
TRIM_MEANINGS = trim_sql('message_meanings', 'MEANINGS')
clear = NATIVE.split('static JSONObject clear(Context c,long thread)', 1)[1].split('\n    }', 1)[0]
clear_deletes = re.findall(r'db\.delete\("([^"]+)","([^"]+)",args\(current\)\)', clear)
assert clear_deletes == [('pilot_training', 'thread=? AND scope=?')], 'Forgetting practice must preserve per-message explanations'
FORGET = f'DELETE FROM {clear_deletes[0][0]} WHERE {clear_deletes[0][1]}'
remove = re.search(r'if\(meaning\.isEmpty\(\)\)db\.delete\("message_meanings","([^"]+)"', NATIVE)
assert remove, 'Expected exact per-transport message removal'
REMOVE_MEANING = 'DELETE FROM message_meanings WHERE ' + remove.group(1)
assert 'db.insertOrThrow("pilot_training",null,values);' in NATIVE
assert 'SQLiteDatabase.CONFLICT_REPLACE' in NATIVE


def add_practice(db, thread, scope, token, number):
    with db:
        db.execute('INSERT INTO pilot_training(thread,scope,turn,incoming,reply,created) VALUES(?,?,?,?,?,?)',
                   (thread, scope, token, f'Fictional practice {number}', f'Owner answer {number}', number))
        db.execute(TRIM_PRACTICE, (thread, scope, thread, scope))


def add_meaning(db, thread, scope, kind, message_id, created, meaning='An inside joke'):
    with db:
        db.execute('INSERT OR REPLACE INTO message_meanings(thread,scope,kind,message_id,fingerprint,message,meaning,created) VALUES(?,?,?,?,?,?,?,?)',
                   (thread, scope, kind, message_id, f'fingerprint-{kind}-{message_id}', f'Fictional received {kind} {message_id}', meaning, created))
        db.execute(TRIM_MEANINGS, (thread, scope, thread, scope))


def rows(db, table, thread, scope):
    return db.execute(f'SELECT * FROM {table} WHERE thread=? AND scope=? ORDER BY rowid', (thread, scope)).fetchall()


with tempfile.TemporaryDirectory(prefix='reply-pilot-training-sql-') as directory:
    path = Path(directory) / 'fictional-guidance.db'
    db = sqlite3.connect(path)
    # v13->v14 only adds these tables; the migration helper extracts their real
    # Java CREATE statements and verifies both uniqueness constraints/indexes.
    db.execute('PRAGMA user_version=13')
    with db:
        upgrade_v14(db, STORE)
        db.execute('PRAGMA user_version=14')
    assert db.execute('PRAGMA integrity_check').fetchone() == ('ok',)

    # A reused numeric thread must keep the old address binding isolated, and
    # another thread cannot inherit lessons even if it has the same scope hash.
    add_practice(db, 7, 'address-B', 'different-address', 1)
    add_practice(db, 8, 'address-A', 'different-thread', 1)
    add_meaning(db, 7, 'address-B', 'sms', 1, 1)
    add_meaning(db, 8, 'address-A', 'sms', 1, 1)
    unaffected = {(table, thread, scope): rows(db, table, thread, scope)
                  for table in ['pilot_training', 'message_meanings']
                  for thread, scope in [(7, 'address-B'), (8, 'address-A')]}

    for number in range(1, 41):
        add_practice(db, 7, 'address-A', f'turn-{number}', number)
    kept = db.execute('SELECT turn,reply FROM pilot_training WHERE thread=7 AND scope=? ORDER BY _id', ('address-A',)).fetchall()
    assert len(kept) == 24
    assert kept[0] == ('turn-17', 'Owner answer 17') and kept[-1] == ('turn-40', 'Owner answer 40')
    before_retry = rows(db, 'pilot_training', 7, 'address-A')
    try:
        add_practice(db, 7, 'address-A', 'turn-40', 99)
        raise AssertionError('A repeated native turn token must not create a second lesson')
    except sqlite3.IntegrityError:
        pass
    assert rows(db, 'pilot_training', 7, 'address-A') == before_retry
    try:
        add_practice(db, 8, 'address-B', 'turn-40', 100)
        raise AssertionError('Turn token uniqueness also holds across contacts')
    except sqlite3.IntegrityError:
        pass

    for number in range(1, 61):
        add_meaning(db, 7, 'address-A', 'sms', number, number)
    kept_notes = db.execute('SELECT message_id FROM message_meanings WHERE thread=7 AND scope=? ORDER BY created', ('address-A',)).fetchall()
    assert kept_notes == [(number,) for number in range(21, 61)]
    add_meaning(db, 7, 'address-A', 'mms', 60, 61, 'Media-specific context')
    assert len(rows(db, 'message_meanings', 7, 'address-A')) == 40
    assert db.execute('SELECT kind,meaning FROM message_meanings WHERE thread=7 AND scope=? AND message_id=60 ORDER BY kind', ('address-A',)).fetchall() == [('mms', 'Media-specific context'), ('sms', 'An inside joke')]
    add_meaning(db, 7, 'address-A', 'sms', 60, 62, 'Edited meaning')
    assert len(rows(db, 'message_meanings', 7, 'address-A')) == 40
    assert db.execute('SELECT meaning FROM message_meanings WHERE thread=7 AND scope=? AND kind=? AND message_id=60', ('address-A', 'sms')).fetchone() == ('Edited meaning',)
    with db:
        db.execute(REMOVE_MEANING, (7, 'address-A', 'sms', 60))
    assert db.execute('SELECT kind FROM message_meanings WHERE thread=7 AND scope=? AND message_id=60', ('address-A',)).fetchall() == [('mms',)]

    for key, expected in unaffected.items():
        assert rows(db, *key) == expected, key
    snapshots = {table: db.execute(f'SELECT * FROM {table} ORDER BY rowid').fetchall()
                 for table in ['pilot_training', 'message_meanings']}
    db.commit()
    db.close()
    db = sqlite3.connect(path)
    assert db.execute('PRAGMA user_version').fetchone() == (14,)
    for table, expected in snapshots.items():
        assert db.execute(f'SELECT * FROM {table} ORDER BY rowid').fetchall() == expected, table

    notes_before_forget = db.execute('SELECT * FROM message_meanings ORDER BY rowid').fetchall()
    with db:
        db.execute(FORGET, (7, 'address-A'))
    assert rows(db, 'pilot_training', 7, 'address-A') == []
    assert db.execute('SELECT * FROM message_meanings ORDER BY rowid').fetchall() == notes_before_forget
    for key, expected in unaffected.items():
        assert rows(db, *key) == expected, key
    db.close()
    db = sqlite3.connect(path)
    assert rows(db, 'pilot_training', 7, 'address-A') == []
    assert db.execute('SELECT * FROM message_meanings ORDER BY rowid').fetchall() == notes_before_forget
    assert db.execute('PRAGMA integrity_check').fetchone() == ('ok',)
    db.close()

print('PASS: actual v14 schema + native pruning keeps newest 24 practice examples / 40 meanings per thread and address; unique turn retries cannot duplicate lessons; SMS/MMS same-ID meanings remain distinct; edits/removal are scoped; forget-practice preserves meanings and other contacts; all persistence survives reopen.')
