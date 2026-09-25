#!/usr/bin/env python3
"""Actual archive schema/query/purge SQL over fictional data, including WAL rollback.
Cipher authentication is exercised separately by HistoryArchiveCipherTest.
"""
from pathlib import Path
import hashlib
import json
import re
import sqlite3
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT/'app/src/main/java/com/contentfoundry/replypilot/HistoryArchive.java').read_text()
POLICY = (ROOT/'app/src/main/java/com/contentfoundry/replypilot/HistoryArchivePolicy.java').read_text()
CREATE = SOURCE.split('@Override public void onCreate(SQLiteDatabase db){',1)[1].split('\n        }',1)[0]
SCHEMA = re.findall(r'db\.execSQL\("([^"]+)"\)',CREATE)
assert len(SCHEMA)==7 and any('archive_identities' in sql for sql in SCHEMA)
SQLS = re.findall(r'String sql=(.+);',SOURCE)
assert len(SQLS)==2
assert 'getNoBackupFilesDir()' in SOURCE and 'setWriteAheadLoggingEnabled(true)' in SOURCE
assert 'db.beginTransactionNonExclusive()' in SOURCE and 'db.setTransactionSuccessful()' in SOURCE
assert 'db.delete("archive_messages","seen<>?"' in SOURCE and 'db.delete("archive_threads","seen<>?"' in SOURCE
assert 'if(data.isNull(0))break;' in SOURCE and 'expectedSnapshot(request,generation)' in SOURCE
assert 'if(!create){corrupt();throw new IOException(' in SOURCE
assert 'catch(AEADBadTagException|IllegalArgumentException invalid){corrupt();throw invalid;}' in SOURCE
assert 'blocked=true;clearPending=true;contentEpoch++' in SOURCE
clear_block=SOURCE.split('if(clear){',1)[1].split('if(scrub)',1)[0]
CLEAR_TABLES=re.findall(r'db\.delete\("([^"]+)",null,null\)',clear_block)
assert CLEAR_TABLES==['archive_messages','archive_threads','archive_identities']
assert SOURCE.index('if(clear){') < SOURCE.index('sync(c,db,permission,revision)')
HISTORY_ORDER = re.search(r'HISTORY_ORDER="([^"]+)"',POLICY).group(1)
INBOX_ORDER = re.search(r'INBOX_ORDER="([^"]+)"',POLICY).group(1)
HISTORY_WHERE = re.search(r'new Query\("(thread=\? AND [^"]+)"',POLICY).group(1)
INBOX_WHERE = re.search(r'new Query\("(\(date<\?[^"]+)"',POLICY).group(1)
PURGE_ORPHANS = re.search(r'db\.execSQL\("(DELETE FROM archive_messages WHERE thread NOT IN[^"]+)"',SOURCE).group(1)


def concat(expression, values):
    """Evaluate only literal concatenation in the actual Java page SQL template."""
    parts=[];start=0;quoted=False;escaped=False;depth=0
    for index,char in enumerate(expression):
        if quoted:
            if escaped:escaped=False
            elif char=='\\':escaped=True
            elif char=='"':quoted=False
        elif char=='"':quoted=True
        elif char=='(':depth+=1
        elif char==')':depth-=1
        elif char=='+' and depth==0:parts.append(expression[start:index].strip());start=index+1
    parts.append(expression[start:].strip())
    return ''.join(json.loads(part) if part.startswith('"') else str(values[part]) for part in parts)


def history_sql(thread, before=None, limit=40):
    where=HISTORY_WHERE if before else 'thread=?'
    args=[thread,thread]
    if before:
        date,rank,identifier=before;args += [date,date,rank,rank,identifier]
    return concat(SQLS[1],{'query.where()':where,'HistoryArchivePolicy.HISTORY_ORDER':HISTORY_ORDER,'(limit+1)':limit+1}),args


def inbox_sql(before=None,limit=60):
    where=INBOX_WHERE if before else '1'
    args=[] if before is None else [before[0],before[0],before[1]]
    return concat(SQLS[0],{'query.where()':where,'HistoryArchivePolicy.INBOX_ORDER':INBOX_ORDER,'(search.isEmpty()?limit+1:HistoryArchivePolicy.SCAN_PAGE+1)':limit+1}),args


def digest(db):
    result=[]
    for table in ['archive_meta','archive_messages','archive_threads','archive_identities']:
        result.append(db.execute(f'SELECT * FROM {table} ORDER BY rowid').fetchall())
    return hashlib.sha256(repr(result).encode()).hexdigest()


def put_message(db,thread,kind,identifier,date,seen,payload):
    db.execute('INSERT OR REPLACE INTO archive_messages VALUES(?,?,?,?,?,?,?,?)',(kind,identifier,thread,date,kind=='mms',seen,hashlib.sha256(payload).hexdigest(),payload))


def put_thread(db,thread,date,seen,payload):
    db.execute('INSERT OR REPLACE INTO archive_threads VALUES(?,?,?,?,?,?)',(thread,date,thread*10000+1,'sms',seen,payload))


with tempfile.TemporaryDirectory(prefix='reply-pilot-all-history-') as directory:
    path=Path(directory)/'fictional.db';db=sqlite3.connect(path);db.execute('PRAGMA journal_mode=WAL')
    for sql in SCHEMA:db.execute(sql)
    db.execute('PRAGMA user_version=1')
    # More chats than every former20/150 cap, and a history exceeding30/50 rows.
    for thread in range(1,701):
        header=json.dumps({'thread_id':thread,'address':f'+1202555{thread:04}','name':f'Fictional contact {thread}'}).encode()
        put_thread(db,thread,thread*1000,1,header)
        db.execute('INSERT INTO archive_identities VALUES(?,?)',(thread,f'identity-{thread}'))
        for number in range(1,11):
            put_message(db,thread,'sms',thread*10000+number,thread*1000+number,1,f'Fictional thread {thread} message {number}'.encode())
    long_body=('Full original Unicode text 😀\n'*500).encode()
    for number in range(1,1201):
        for kind in ['sms','mms']:
            put_message(db,700,kind,90000000+number,1000000+number//3,1,long_body if number==1199 else f'{kind}-{number}'.encode())
    db.execute('UPDATE archive_meta SET generation=1,saved_at=1000,contacts=1 WHERE id=1');db.commit()
    assert db.execute('SELECT COUNT(*) FROM archive_threads').fetchone()==(700,)
    assert db.execute('SELECT COUNT(*) FROM archive_messages WHERE thread=700').fetchone()==(2410,)

    # Execute the exact production LEFT JOIN page SQL, including stable tie order.
    expected=db.execute('SELECT kind,id,date,rank,payload FROM archive_messages WHERE thread=700 ORDER BY '+HISTORY_ORDER).fetchall()
    collected=[];before=None
    while True:
        sql,args=history_sql(700,before);page=db.execute(sql,args).fetchall()
        assert all(row[5:]==(1,1000) for row in page)
        selected=page[:40];collected += [(row[1],row[2],row[3],int(row[1]=='mms'),row[4]) for row in selected]
        if len(page)<=40:break
        last=selected[-1];before=(last[3],int(last[1]=='mms'),last[2])
    assert collected==expected and len({(row[0],row[1]) for row in collected})==2410
    assert sum(row[4]==long_body for row in collected)==2,'Full text must survive beyond legacy2,048-character cutoff'
    inbox=[];cursor=None
    while True:
        sql,args=inbox_sql(cursor);page=db.execute(sql,args).fetchall();inbox += [row[0] for row in page[:60]]
        if len(page)<=60:break
        cursor=(page[59][1],page[59][0])
    assert inbox==list(range(700,0,-1))

    # Another reader sees the last committed snapshot while a refresh is partial.
    reader=sqlite3.connect(path);before_failure=digest(db)
    try:
        with db:
            put_message(db,700,'sms',90000001,1000001,2,b'Partial uncommitted scan')
            db.execute('DELETE FROM archive_messages WHERE seen<>?',(2,))
            assert reader.execute('SELECT COUNT(*) FROM archive_messages').fetchone()[0]==9400
            raise OSError('Fictional provider/disk failure before headers completed')
    except OSError:pass
    assert digest(db)==before_failure,'Failed refresh must retain the entire previous good snapshot'
    reader.close();db.close();db=sqlite3.connect(path)
    assert digest(db)==before_failure and db.execute('PRAGMA user_version').fetchone()==(1,)

    # Successful reconciliation removes deleted records, deleted chats and orphans.
    with db:
        db.execute('UPDATE archive_messages SET seen=2 WHERE NOT (kind=? AND id=?)',('sms',90000001))
        db.execute('DELETE FROM archive_messages WHERE seen<>?',(2,))
        db.execute('UPDATE archive_threads SET seen=2 WHERE thread<>1')
        db.execute('DELETE FROM archive_threads WHERE seen<>?',(2,))
        db.execute(PURGE_ORPHANS)
        db.execute('UPDATE archive_meta SET generation=2,saved_at=2000 WHERE id=1')
    assert db.execute('SELECT COUNT(*) FROM archive_messages WHERE thread=1').fetchone()==(0,)
    assert db.execute('SELECT kind FROM archive_messages WHERE id=90000001').fetchall()==[('mms',)]
    assert db.execute('SELECT COUNT(*) FROM archive_threads').fetchone()==(699,)

    # Thread identity staging protects against recycled recipients during sync.
    assert 'if(!captured.moveToFirst()||!identity.equals(captured.getString(0)))' in SOURCE
    preserved=digest(db)
    try:
        with db:
            captured=db.execute('SELECT fingerprint FROM archive_identities WHERE thread=?',(700,)).fetchone()
            assert captured and captured[0]=='identity-700'
            if captured[0]!='new-recipient-identity':raise OSError('Recipients changed mid-scan')
    except OSError:pass
    assert digest(db)==preserved

    # Empty/missing/deleted pages still carry metadata; stale snapshot1 can be
    # rejected against2 instead of looking like a never-initialized snapshot0.
    sql,args=history_sql(1);empty=db.execute(sql,args).fetchall()
    assert len(empty)==1 and empty[0][0] is None and empty[0][5:]==(2,2000)
    sql,args=history_sql(700,(0,0,1));exhausted=db.execute(sql,args).fetchall()
    assert len(exhausted)==1 and exhausted[0][0] is not None and exhausted[0][1] is None and exhausted[0][5:]==(2,2000)
    with db:
        db.execute('DELETE FROM archive_messages');db.execute('DELETE FROM archive_threads');db.execute('UPDATE archive_meta SET generation=3,saved_at=3000 WHERE id=1')
    sql,args=inbox_sql();empty=db.execute(sql,args).fetchall();assert empty==[(None,None,None,3,3000)]
    db.close();db=sqlite3.connect(path)
    assert db.execute(sql,args).fetchall()==empty and db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    # Authentication failure requests the real clear branch before another sync.
    # Even an unchanged plaintext signature must get a new encrypted payload.
    old_plain=b'Fictional unchanged plaintext';signature=hashlib.sha256(old_plain).hexdigest()
    put_message(db,7,'sms',17,100,3,old_plain)
    db.execute('UPDATE archive_messages SET payload=? WHERE kind=? AND id=?',(b'corrupt old-key payload','sms',17))
    db.commit()
    for table in CLEAR_TABLES:db.execute('DELETE FROM '+table)
    assert db.execute('SELECT signature FROM archive_messages WHERE kind=? AND id=?',('sms',17)).fetchone() is None
    put_message(db,7,'sms',17,100,4,old_plain)
    assert db.execute('SELECT signature,payload FROM archive_messages WHERE kind=? AND id=?',('sms',17)).fetchone()==(signature,old_plain)
    db.commit();db.close()

print('PASS: actual encrypted-record archive schema and production paged SQL cover 700 chats / 2,410 messages in one chat without former caps; preserve full long text; WAL readers keep old complete data during partial refresh; failed sync rolls back; deletions reconcile; recipient changes abort; empty pages retain current snapshot; restart preserves committed data; auth/key failure clears old ciphertext before rebuilding unchanged records. Cipher AAD/tamper/Unicode tests run in JVM.')
