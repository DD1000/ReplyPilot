#!/usr/bin/env python3
"""Fictional SQLite crash/resume/isolation tests using the actual learning schema.
Cryptographic AAD, full Unicode batching and readiness rules run in JVM tests.
"""
from pathlib import Path
import re, sqlite3, tempfile, uuid

ROOT=Path(__file__).resolve().parents[1]
SOURCE=(ROOT/'app/src/main/java/com/contentfoundry/replypilot/HistoryLearning.java').read_text()
ARCHIVE=(ROOT/'app/src/main/java/com/contentfoundry/replypilot/HistoryArchive.java').read_text()
CREATE=SOURCE.split('@Override public void onCreate(SQLiteDatabase db){',1)[1].split('\n        }',1)[0]
SCHEMA=re.findall(r'db\.execSQL\("([^"]+)"\)',CREATE)
assert len(SCHEMA)==5
PROGRESS=re.search(r'db\.execSQL\("(UPDATE learning_meta SET processed_messages[^\"]+)"',SOURCE).group(1)
FIRST=re.search(r'db\.rawQuery\("(SELECT thread FROM learning_queue[^\"]+)"',SOURCE).group(1)
FINAL=re.search(r'DatabaseUtils.longForQuery\(db,"(SELECT COUNT\(\*\) FROM learning_queue WHERE thread[^\"]+)"',SOURCE).group(1)
assert 'getNoBackupFilesDir()' in SOURCE and 'setWriteAheadLoggingEnabled(true)' in SOURCE
assert 'sealLearning(run+":unit:"+seq' in SOURCE and 'sealLearning(run+":memory:"+thread' in SOURCE
assert 'if(restart)v.put("restart",1)' in SOURCE
assert SOURCE.count('learningMatches(c,thread,scope,sources)')==2
assert SOURCE.index('learningMatches(c,thread,scope,sources)')<SOURCE.index('CloudClient.request(config,"/history-analysis",input)')<SOURCE.rindex('learningMatches(c,thread,scope,sources)')
assert 'if(more)enqueue(c,3300)' in SOURCE and 'historyAnalysisVersion' in SOURCE
assert 'source_signature' in SOURCE and 'learningScope(c,thread)' in SOURCE
assert 'new JSONObject().put("requestId",requestId).put("previous"' in SOURCE
assert 'thread' not in re.search(r'JSONObject input=(.+);',SOURCE).group(1).split('.put("previous"',1)[0]
assert 'savedEpoch!=contentEpoch' in ARCHIVE
assert SOURCE.index('HistoryLearningPolicy.requireCompleteText') < SOURCE.index('List<String> fragments=HistoryLearningPolicy.fragments') < SOURCE.index('totals[0]++')

def request_id(run,thread,first,last):
    import hashlib
    return str(uuid.UUID(bytes=hashlib.md5(f'{run}:{thread}:{first}:{last}'.encode()).digest(),version=3))

def seed(db):
    for sql in SCHEMA:db.execute(sql)
    # Preparing metadata is committed before the potentially long snapshot copy.
    db.execute("UPDATE learning_meta SET config='paired-a',phase='preparing',restart=1")
    db.commit()

def progress(db):
    return db.execute('SELECT run,phase,processed_messages,total_messages,completed_contacts,total_contacts FROM learning_meta').fetchone()

def checkpoint(db,thread,last,messages,final,memory,fail=False):
    with db:
        changed=db.execute('UPDATE learning_contacts SET memory=?,done=? WHERE thread=? AND scope=?',(memory,int(final),thread,f'scope-{thread}')).rowcount
        assert changed==1
        db.execute('DELETE FROM learning_queue WHERE thread=? AND seq<=?',(thread,last))
        db.execute(PROGRESS,(messages,int(final),'run-a'))
        if fail:raise OSError('Fictional interruption after summary write, before commit')

with tempfile.TemporaryDirectory(prefix='reply-pilot-learning-') as folder:
    path=Path(folder)/'learning.db';db=sqlite3.connect(path);db.execute('PRAGMA journal_mode=WAL');seed(db)
    reader=sqlite3.connect(path)
    # A big initial transaction never blocks status from showing safe preparing.
    with db:
        for thread in (7,8):
            db.execute('INSERT INTO learning_contacts VALUES(?,?,?,0)',(thread,f'scope-{thread}',b'cipher-empty'))
            for number in range(90):
                # A source split into 3 exact encrypted fragments counts only once.
                source=number//3
                db.execute('INSERT INTO learning_queue(thread,kind,source_id,source_signature,last_fragment,payload) VALUES(?,?,?,?,?,?)',(thread,'sms' if source%2 else 'mms',thread*100+source,f'signature-{thread}-{source}',int(number%3==2),b'ciphertext-not-plaintext'))
        assert reader.execute('SELECT phase,total_messages FROM learning_meta').fetchone()==('preparing',0)
        assert reader.execute('SELECT COUNT(*) FROM learning_queue').fetchone()==(0,)
        db.execute("UPDATE learning_meta SET run='run-a',phase='analyzing',restart=0,snapshot=123,total_messages=60,total_contacts=2")
    assert reader.execute('SELECT COUNT(*) FROM learning_queue').fetchone()==(180,)
    reader.close()
    first_thread=db.execute(FIRST).fetchone()[0];assert first_thread==7
    batch=db.execute('SELECT seq,last_fragment FROM learning_queue WHERE thread=? ORDER BY seq LIMIT 40',(7,)).fetchall()
    first,last=batch[0][0],batch[-1][0];completed=sum(row[1] for row in batch)
    req=request_id('run-a',7,first,last);before=progress(db)
    try:checkpoint(db,7,last,completed,False,b'partial-new-memory',True)
    except OSError:pass
    assert progress(db)==before
    assert db.execute('SELECT COUNT(*) FROM learning_queue').fetchone()==(180,)
    assert db.execute('SELECT memory FROM learning_contacts WHERE thread=7').fetchone()==(b'cipher-empty',)
    # Lost network replies / process death must retry the same immutable batch ID.
    db.close();db=sqlite3.connect(path)
    retried=db.execute('SELECT seq FROM learning_queue WHERE thread=? ORDER BY seq LIMIT 40',(7,)).fetchall()
    assert req==request_id('run-a',7,retried[0][0],retried[-1][0])
    checkpoint(db,7,last,completed,False,b'committed-style-7')
    assert progress(db)[2:]==(13,60,0,2)
    assert db.execute('SELECT memory,done FROM learning_contacts WHERE thread=8').fetchone()==(b'cipher-empty',0)
    assert db.execute(FINAL,(7,last)).fetchone()==(50,)
    db.close();db=sqlite3.connect(path)
    assert db.execute('SELECT memory FROM learning_contacts WHERE thread=7').fetchone()==(b'committed-style-7',)
    assert db.execute('SELECT MIN(seq) FROM learning_queue WHERE thread=7').fetchone()==(41,)
    # Completing each contact advances counters once; a split source only counts
    # at its last fragment, including when split across a batch boundary.
    while True:
        next_thread=db.execute(FIRST).fetchone()
        if not next_thread:break
        thread=next_thread[0];batch=db.execute('SELECT seq,last_fragment FROM learning_queue WHERE thread=? ORDER BY seq LIMIT 40',(thread,)).fetchall()
        last=batch[-1][0];final=db.execute(FINAL,(thread,last)).fetchone()[0]==0
        checkpoint(db,thread,last,sum(row[1] for row in batch),final,f'complete-memory-{thread}'.encode())
    assert progress(db)[2:]==(60,60,2,2)
    assert db.execute('SELECT thread,done FROM learning_contacts ORDER BY thread').fetchall()==[(7,1),(8,1)]
    # Recipient reuse cannot silently overwrite another recipient's summary.
    with db:
        assert db.execute('UPDATE learning_contacts SET memory=? WHERE thread=? AND scope=?',(b'wrong-recipient',7,'new-recipient-scope')).rowcount==0
    assert db.execute('SELECT memory FROM learning_contacts WHERE thread=7').fetchone()==(b'complete-memory-7',)
    # Failed refresh/import leaves the completed checkpoint intact and retryable.
    before=db.execute('SELECT * FROM learning_contacts ORDER BY thread').fetchall()
    try:
        with db:
            db.execute('DELETE FROM learning_contacts');db.execute("UPDATE learning_meta SET run='run-b',total_messages=1")
            raise OSError('Fictional failed snapshot export')
    except OSError:pass
    assert db.execute('SELECT * FROM learning_contacts ORDER BY thread').fetchall()==before
    assert progress(db)[0]=='run-a'
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
print('PASS: production history-learning SQL preserves atomic memory/cursor/progress, resumes the identical request after restart, keeps contacts isolated, counts long-message fragments once, rejects wrong recipient scope, and exposes preparing status through WAL during initial export.')
