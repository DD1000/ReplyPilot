#!/usr/bin/env python3
"""Actual SQLite repair/query predicates for delivered sends and deferred learning."""
from pathlib import Path
import re
import sqlite3
import tempfile

JAVA=Path(__file__).resolve().parents[1]/'app/src/main/java/com/contentfoundry/replypilot'
store=(JAVA/'Store.java').read_text();sender=(JAVA/'Sender.java').read_text();learning=(JAVA/'ApprovedLearning.java').read_text()
repair=re.search(r'db.execSQL\("(UPDATE jobs SET status=\'sent\'[^"]+)"',store).group(1)
create=re.search(r'db.execSQL\("(CREATE TABLE jobs [^"]+)"',store).group(1)
age=re.search(r'update\("jobs",v,"(status=\'sending\' AND delivery_status[^\"]+)"',store).group(1)
thread_query=re.search(r'query\("(SELECT \* FROM jobs WHERE thread=\? ORDER BY _id DESC LIMIT 40)"',store).group(1)
assert 'DeliveryPolicy.settledSendStatus(job.optString("status"),state)' in store
assert 'DeliveryPolicy.receiptStatus(failure,j.optString("delivery_status"))' in sender
assert 'if(failure&&!confirmed)db.deliveryUnknown(id)' in sender
assert 'values.put("type",Telephony.Sms.MESSAGE_TYPE_SENT)' in sender
assert 'ApprovedLearning.scheduleCapture(c,id,thread,base)' in sender and 'ApprovedLearning.capture(c,id,thread,base)' not in sender
assert 'PilotApp.IO.execute(()->capture(c,jobId,anchor))' in learning
assert learning.count('ApprovedLearningPolicy.sameAnchor(expected,anchor(c,expected.thread(),expected.base()))')==2
with tempfile.TemporaryDirectory(prefix='pilot-send-outcomes-') as tmp:
    path=Path(tmp)/'fixture.db';db=sqlite3.connect(path);db.execute(create)
    cases=[('sending','delivered',2,'content://sms/10'),('unknown','delivered',2,'content://sms/11'),('failed','delivered',2,'content://sms/12'),('sending','pending',2,'content://sms/13'),('sending','unknown',2,'content://sms/14'),('sending','failed',2,'content://sms/15'),('scheduled','delivered',2,'content://sms/16'),('cancelled','delivered',2,'content://sms/17'),('sending','delivered',0,'content://sms/18'),('sending','delivered',2,''),('sent','delivered',2,'content://sms/20')]
    for i,(status,delivery,parts,uri) in enumerate(cases,1):
        db.execute('INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,created,delivery_status,parts,uri,sms_date) VALUES(?,?,?, ?,?,?,?,?,?,?,?,?,?,?)',(i,1 if i%2 else 2,'+12025550100','Exact approved body',77,1,1000,1,status,1000,delivery,parts,uri,1000))
    immutable=db.execute('SELECT _id,thread,address,body,base,uri,sms_date FROM jobs ORDER BY _id').fetchall()
    assert db.execute(repair).rowcount==3
    assert db.execute(repair).rowcount==0
    outcomes=dict(db.execute('SELECT _id,status FROM jobs'))
    assert all(outcomes[i]=='sent' for i in [1,2,3,11])
    for i in range(4,11):assert outcomes[i]==cases[i-1][0]
    assert db.execute('SELECT _id,thread,address,body,base,uri,sms_date FROM jobs ORDER BY _id').fetchall()==immutable
    # Confirmed jobs never become unknown merely because their sent callback was lost.
    db.execute(f"UPDATE jobs SET status='unknown' WHERE {age}",(2000,))
    assert db.execute('SELECT status FROM jobs WHERE _id=1').fetchone()==('sent',)
    assert db.execute('SELECT status FROM jobs WHERE _id=4').fetchone()==('unknown',)
    assert all(row[1]==1 for row in db.execute(thread_query,(1,)))
    db.commit();db.close();db=sqlite3.connect(path)
    assert db.execute('SELECT status,delivery_status FROM jobs WHERE _id=1').fetchone()==('sent','delivered')
    # The actual delayed-learning predicate remains anchored to the approved base
    # even after a same-time outgoing SMS and a newer incoming message exist.
    db.execute('CREATE TABLE sms(_id INTEGER PRIMARY KEY,thread_id INTEGER,date INTEGER,type INTEGER,body TEXT)')
    rows=[(10,1,100,2,'Prior owner reply'),(11,1,101,1,'Earlier incoming'),(12,1,102,1,'Approved incoming'),(13,1,102,2,'Newly sent reply'),(14,1,102,1,'Newer unseen incoming'),(15,2,101,1,'Other contact')]
    db.executemany('INSERT INTO sms VALUES(?,?,?,?,?)',rows)
    selection=re.search(r'"(thread_id=\? AND type IN \(1,2\) AND \(date<\? OR \(date=\? AND _id<=\?\)\))"',learning).group(1)
    found=db.execute(f'SELECT type,body FROM sms WHERE {selection} ORDER BY date DESC,_id DESC',(1,102,102,12)).fetchall()
    incoming=[]
    for kind,body in found:
        if kind==2:break
        incoming.append(body)
    assert incoming==['Approved incoming','Earlier incoming']
    assert all('Newer' not in text and 'Newly' not in text and 'Other' not in text for text in incoming)
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
print('PASS: delivered send repair is idempotent and durable; pending/partial/malformed states stay unconfirmed; exact payload and dedupe identity survive; compact jobs stay contact-scoped; delayed learning excludes future/other-contact messages.')
