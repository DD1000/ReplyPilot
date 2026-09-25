#!/usr/bin/env python3
"""Fictional SQLite checks for the actual private outgoing MMS schema and claims."""
from pathlib import Path
import json
import re
import sqlite3
import tempfile

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/com/contentfoundry/replypilot'
store=(JAVA/'MmsOutbox.java').read_text()
source=(JAVA/'MmsAttachments.java').read_text()
creates=re.findall(r'db\.execSQL\("([^"]+)"\)',store)
assert len(creates)==4
claim=re.search(r'update\("attachments",lock,"([^"]+)"',source).group(1)
assert claim=="thread=? AND job=''"
submit=source.split('public static JSONObject send(',1)[1].split('private static void require(',1)[0]
steps=['sql.beginTransaction()','sql.insertOrThrow("sends"','sql.update("attachments",lock','sql.setTransactionSuccessful()','sql.endTransaction()','prepare(c,job','PduPersister.getPduPersister','db.status(id,"sending"','sendMultimediaMessage(']
assert [submit.index(x) for x in steps]==sorted(submit.index(x) for x in steps)
assert 'PendingIntent.FLAG_MUTABLE' in submit and 'new Intent(c,MmsSentReceiver.class)' in submit
assert submit.count('boundary(c,persisted,providerId,savedDraft)')>=2
assert submit.index('savedDraft=Store.get(c).draft(thread,base)')<submit.index('ManualTakeover.claim(c,thread,address)')<submit.index('Messages.activeSim(c,sub)')
assert '!LIVE.contains' not in source  # recovery explicitly captures the live flag
assert 'boolean live=LIVE.contains(id)' in source and 'if(live&&' in source
assert 'hasSentForBase' in source and "status='sent'" in source
assert 'sendMultimediaMessage(' not in source.split('public static void recover(',1)[1]
assert 'status IN (\'sent\',\'failed\') AND settled=0' in source
provider_where=re.search(r'values,"(thread_id=\? AND date=\? AND tr_id=\? AND m_type=\?)"',source).group(1)
with tempfile.TemporaryDirectory(prefix='reply-pilot-outgoing-test-') as folder:
    path=Path(folder)/'fictional.db'
    db=sqlite3.connect(path)
    for sql in creates:db.execute(sql)
    for aid,thread in [('a',1),('b',1),('other',2)]:
        db.execute('INSERT INTO attachments(id,thread,name,mime,bytes,hash,created) VALUES(?,?,?,?,?,?,?)',(aid,thread,'Fictional.jpg','image/jpeg',100,'fictional-hash',10))
    def insert(job,request,thread=1):
        db.execute('INSERT INTO sends(id,request_id,thread,address,caption,sub,base,status,fingerprint,attachments,created,transaction_id,mms_before) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',(job,request,thread,'+12025550100','Test only',1,42,'preparing','fictional-fingerprint','[]',20,'rp'+job,''))
    with db:
        insert('job1','request1')
        assert db.execute('UPDATE attachments SET job=? WHERE '+claim,('job1','1')).rowcount==2
    # A repeated request cannot produce another durable job or carrier claim.
    try:
        with db:insert('job2','request1')
        raise AssertionError('Duplicate request was accepted')
    except sqlite3.IntegrityError:pass
    assert db.execute('SELECT COUNT(*) FROM sends').fetchone()[0]==1
    assert db.execute("SELECT job FROM attachments WHERE id='other'").fetchone()[0]==''
    # Linking and insertion are atomic: a failed claim rolls the new job back.
    try:
        with db:
            insert('rolled-back','request2')
            if db.execute('UPDATE attachments SET job=? WHERE '+claim,('rolled-back','1')).rowcount!=2:raise ValueError('Already claimed')
    except ValueError:pass
    assert db.execute("SELECT 1 FROM sends WHERE id='rolled-back'").fetchone() is None
    db.execute("UPDATE sends SET status='sending',provider_id=77,provider_date=1700000000 WHERE id='job1'")
    db.commit();db.close();db=sqlite3.connect(path)
    # Restart retains unknown work and file ownership; no queue to replay exists.
    assert db.execute("SELECT status,provider_id,transaction_id FROM sends WHERE id='job1'").fetchone()==('sending',77,'rpjob1')
    db.execute("UPDATE sends SET status='unknown' WHERE id='job1'")
    assert db.execute("SELECT COUNT(*) FROM attachments WHERE job='job1'").fetchone()[0]==2
    # A late confirmed outcome can settle exactly this job and is repeat-safe.
    db.execute("UPDATE sends SET status='sent' WHERE id='job1'")
    assert db.execute("DELETE FROM attachments WHERE job='job1'").rowcount==2
    db.execute("UPDATE sends SET settled=1 WHERE id='job1' AND status IN ('sent','failed')")
    assert db.execute("DELETE FROM attachments WHERE job='job1'").rowcount==0
    assert db.execute("SELECT COUNT(*) FROM attachments WHERE thread=2").fetchone()[0]==1
    # Provider IDs are not identities: a reused ID or altered thread/date/token fails.
    db.execute('CREATE TABLE provider(_id INTEGER PRIMARY KEY,thread_id INTEGER,date INTEGER,tr_id TEXT,m_type INTEGER,msg_box INTEGER)')
    db.execute("INSERT INTO provider VALUES(77,1,1700000000,'rpjob1',128,4)")
    assert db.execute('UPDATE provider SET msg_box=2 WHERE _id=77 AND '+provider_where,(1,1700000000,'rpjob1',128)).rowcount==1
    db.execute("UPDATE provider SET date=1700000001,tr_id='another' WHERE _id=77")
    assert db.execute('UPDATE provider SET msg_box=2 WHERE _id=77 AND '+provider_where,(1,1700000000,'rpjob1',128)).rowcount==0
    db.commit();db.close()
print('Outgoing MMS private schema, atomic claims, replay protection, recovery persistence, and provider binding passed.')
