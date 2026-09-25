#!/usr/bin/env python3
"""Fictional SQLite checks using the actual Train Autopilot persona schema from Personas.java.
Encryption, recipient binding and Keystore behavior run only on the phone."""
from pathlib import Path
import re, sqlite3, tempfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=(ROOT/'app/src/main/java/com/contentfoundry/replypilot/Personas.java').read_text()
SCHEMA=re.findall(r'db\.execSQL\("([^"]+)"\)',SOURCE.split('@Override public void onCreate(SQLiteDatabase db){',1)[1].split('}',1)[0])
assert len(SCHEMA)==1 and SCHEMA[0].startswith('CREATE TABLE personas(')
assert 'getNoBackupFilesDir(),"personas-v1.db"' in SOURCE and 'setWriteAheadLoggingEnabled(true)' in SOURCE
assert 'HistoryArchiveCipher.seal(key(true),identity(thread,trainedAt,scope)' in SOURCE, 'personas are stored encrypted'
assert 'CONFLICT_REPLACE' in SOURCE and 'delete("personas","thread=?"' in SOURCE
assert 'if(!saved.scope().equals(HistoryArchive.learningScope(c,thread)))return null;' in SOURCE, 'a persona is bound to its recipient'
RETIRED=(ROOT/'app/src/main/java/com/contentfoundry/replypilot/HistoryLearning.java').read_text()
assert 'SQLiteDatabase.deleteDatabase(new File(c.getNoBackupFilesDir(),"history-learning-v1.db"))' in RETIRED, 'old all-history summaries are deleted'

with tempfile.TemporaryDirectory() as folder:
    db=sqlite3.connect(Path(folder)/'personas-v1.db');db.execute('PRAGMA journal_mode=WAL');db.execute(SCHEMA[0])
    def save(thread,trained,messages,payload):
        db.execute('INSERT OR REPLACE INTO personas(thread,scope,trained_at,trained_messages,latest_date,model,payload) VALUES(?,?,?,?,?,?,?)',(thread,f'scope-{thread}',trained,messages,trained-1,'gpt-6-astra',payload))
    save(7,1000,850,b'sealed-a');save(8,1001,40,b'sealed-b')
    save(7,2000,1000,b'sealed-c')  # Retrain replaces, never duplicates.
    assert db.execute('SELECT COUNT(*) FROM personas').fetchone()[0]==2
    assert db.execute('SELECT trained_at,trained_messages,payload FROM personas WHERE thread=7').fetchone()==(2000,1000,b'sealed-c')
    db.execute('DELETE FROM personas WHERE thread=?',(8,))  # Remove training for one chat only.
    assert [r[0] for r in db.execute('SELECT thread FROM personas')]==[7]
    for bad in [(9,None,1,1,1,'',b'x'),(9,'s',None,1,1,'',b'x'),(9,'s',1,1,1,'',None)]:
        try:db.execute('INSERT INTO personas(thread,scope,trained_at,trained_messages,latest_date,model,payload) VALUES(?,?,?,?,?,?,?)',bad);raise SystemExit('NOT NULL constraint missing')
        except sqlite3.IntegrityError:pass
    db.commit();db.close()
print('PASS: persona schema, retrain replacement, per-chat removal, required fields, encrypted storage and retired all-history data.')
