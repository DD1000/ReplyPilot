#!/usr/bin/env python3
"""Exercise real v11→v14 SQL and single-use action claims with fictional data.

SQLite checks actual native query predicates and migration statements. Android
permission, message/burst binding and carrier behavior belong to native tests.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json
import re
import sqlite3
import tempfile
import threading
from store_migrations import upgrade_v14, upgrade_v11, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/contentfoundry/replypilot'
source = (JAVA / 'Store.java').read_text()
actions = (JAVA / 'AttentionActions.java').read_text()
sender = (JAVA / 'Sender.java').read_text()
policy = (JAVA / 'AttentionPolicy.java').read_text()
attention_job = (JAVA / 'AttentionJob.java').read_text()
assert 'super(c,"pilot.db",null,14)' in source, 'Review newer migrations before extending this check'


def statements(text):
    return re.findall(r'db\.execSQL\("([^"]+)"\)', text)


def helper(name):
    return statements(source.split(f'private static void {name}(SQLiteDatabase db){{', 1)[1].split('\n    }', 1)[0])


def schema(db):
    names = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    return ({name: db.execute(f'PRAGMA table_info({name})').fetchall() for name in names},
            db.execute("SELECT name,tbl_name,sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall())


def method(signature):
    return actions.split(signature, 1)[1].split('\n    }', 1)[0]


claim_where = re.search(r'update\("attention_actions",next,"([^"]+)"', method('static JSONObject claim(Context c)')).group(1)
offer_where = re.search(r'update\("attention_actions",next,"([^"]+)"', method('public static JSONObject perform(Context c,String token)')).group(1)
finish_where = re.search(r'update\("attention_actions",values,"([^"]+)"', method('private static void finish(Context c,String token,String state,String note)')).group(1)
recover_query = re.search(r'query\("([^"]+)"', method('static void recover(Context c)')).group(1)
queued_query = re.search(r'query\("([^"]+)"', method('static long nextWait(Context c)')).group(1)
reoffer_where = re.search(r'update\("attention_actions",retry,"([^"]+)"', actions).group(1)
assert claim_where == "token=? AND state='queued' AND job_id=0"
assert offer_where == "token=? AND state='offered'"
assert finish_where == "token=? AND state IN ('offered','queued','running')"
assert recover_query == "SELECT token FROM attention_actions WHERE state='running'"
assert queued_query == "SELECT * FROM attention_actions WHERE action='joke' AND state='queued' ORDER BY created LIMIT 20"
assert '!LIVE.contains(token)' in method('static void recover(Context c)'), 'Recovery must not fail an in-process worker'
assert reoffer_where == "token=? AND job_id=0 AND state IN ('offered','failed','stale')"
interrupted_delay_where = re.search(r'update\("jobs",deferral,"([^"]+)"', sender).group(1)
assert interrupted_delay_where == "attention_kind='delay' AND status='scheduled'"
recovery_hook=attention_job.split('public static void recoverAndSchedule(Context context)',1)[1].split('static void schedule(',1)[0]
assert recovery_hook.index('AttentionActions.recover(c)') < recovery_hook.index('AttentionActions.nextWait(c)') < recovery_hook.index('schedule(c,')
assert 'if(wait>=0)' in recovery_hook and 'RECOVERY_QUEUED.compareAndSet(false,true)' in recovery_hook
assert 'AttentionJob.recoverAndSchedule(' in (JAVA/'PilotApp.java').read_text()
assert 'AttentionJob.recoverAndSchedule(' in (JAVA/'RecoveryReceiver.java').read_text()
delay_method = sender.split('static JSONObject sendAttentionDelay(Context c,JSONObject action)',1)[1].split('public static long accept(',1)[0]
link_where = re.search(r'update\("attention_actions",link,"([^"]+)"',delay_method).group(1)
assert link_where == "token=? AND action='delay' AND state='running' AND job_id=0"
assert 'provenance.put("attention_kind","delay")' in delay_method
assert 'provenance.put("location_revision",0)' in delay_method and 'provenance.put("location_expires",0)' in delay_method
steps=['sql.beginTransaction()','id=db.queue(','sql.update("jobs",provenance','sql.update("attention_actions",link','sql.setTransactionSuccessful()','sql.endTransaction()','dispatch(c,id)']
assert [delay_method.index(step) for step in steps] == sorted(delay_method.index(step) for step in steps), 'Commit linkage before carrier dispatch; no network inside the link transaction'
delay_text = json.loads(re.search(r'DELAY_TEXT=("(?:\\.|[^"\\])*");',policy).group(1))
style_query = re.search(r'JSONArray jobs=query\("(SELECT uri,thread,address,body,sms_date FROM jobs WHERE [^"]+)"',source).group(1)
assert "(auto_send=1 OR attention_kind='delay')" in style_query


with tempfile.TemporaryDirectory(prefix='reply-pilot-attention-sql-') as folder:
    path = Path(folder) / 'fictional.db'
    db = sqlite3.connect(path)
    # Construct the shipped v11 layout from the fixed old fixture and real prior
    # migrations; current CREATE statements must not pre-add attention fields.
    db.executescript((ROOT / 'tools/fixtures/store-v5-schema.sql').read_text())
    v6 = source.split('if(oldVersion<6){', 1)[1].split('\n', 1)[0]
    v8 = re.search(r'if\(oldVersion<8\)\s*\{([^}]+)\}', source).group(1)
    v9 = re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}', source).group(1)
    for sql in statements(v6) + helper('createDeliveryReports') + helper('createReplyDecisions') + statements(v8) + statements(v9) + helper('createReplyHolds'):
        db.execute(sql)
    upgrade_v11(db, source, 10)
    db.execute('PRAGMA user_version=11')
    assert 'attention_kind' not in [row[1] for row in db.execute('PRAGMA table_info(jobs)')]
    assert db.execute("SELECT name FROM sqlite_master WHERE name='attention_actions'").fetchone() is None
    for thread, engagement in [(1, 'girlfriend'), (2, 'natural')]:
        db.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay,auto_delay_mode,auto_delay_min,auto_delay_max,engagement,share_location) VALUES(?, 'Fictional relationship', 9, 'Me: fictional example',1,1,'Partner','Myself (beta)',1,300,'range',300,1800,?,1)", (thread, engagement))
        db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine,location_revision,location_expires) VALUES(?,?,'Keep exact draft','[]','Edited by you',42,1700001500000)", (thread, thread*100))
    for index, (automatic, status, delivery) in enumerate([(0,'scheduled','none'),(1,'awaiting_alert','none'),(1,'sent','delivered'),(0,'sending','pending')],1):
        db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,approved,status,created,auto_send,profile_revision,config_revision,original,uri,parts,delivery_status,delivered_at,sms_date,sleep_revision,auto_delay,location_revision,location_expires) VALUES(1,'+12025550100',?,100,1,1700000600000,?,?,1700000000000,?,9,'fictional-config','Fictional incoming',?,1,?,1700000700000,1700000000000,7,721,42,1700001500000)",
                   (f'Exact prior message {index}',1-automatic,status,automatic,f'content://sms/{index}' if status in ('sent','sending') else '',delivery))
    db.execute('INSERT INTO receipts VALUES(3,0,-1)')
    db.execute("INSERT INTO delivery_reports VALUES(3,0,'delivered',0,'3gpp',1700000700000)")
    db.execute("INSERT INTO reply_decisions VALUES(1,100,'plans_need_input',1700000000000)")
    db.execute('INSERT INTO reply_holds VALUES(1,99,100,1700000000000)')
    names = [row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")]
    columns = {name:[row[1] for row in db.execute(f'PRAGMA table_info({name})')] for name in names}
    before = {name:original_rows(db,name,columns[name]) for name in names}
    with db:
        upgrade_v12(db,source)
        assert_v12_defaults(db)
        upgrade_v13(db,source,11)
        assert_v13_defaults(db)
        upgrade_v14(db,source)
        db.execute('PRAGMA user_version=14')
    for name in names:
        assert original_rows(db,name,columns[name]) == before[name], name
    fresh = sqlite3.connect(':memory:')
    for sql in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)',source):fresh.execute(sql)
    assert schema(db) == schema(fresh), 'Fresh and upgraded v14 schemas differ'
    fresh.close()

    def offer(token, thread=1, base=100, action='joke'):
        db.execute("INSERT INTO attention_actions(token,thread,base,action,reason,profile_revision,config_revision,fingerprint,burst,state,created) VALUES(?,?,?,?, 'plans_need_input',9,'fictional-config','fictional-exact-message-hash',12,'offered',1700000000000)", (token,thread,base,action))

    first = '11111111-1111-1111-1111-111111111111'
    delay = '22222222-2222-2222-2222-222222222222'
    offer(first);offer(delay,action='delay')
    assert db.execute('SELECT job_id,note FROM attention_actions ORDER BY token').fetchall() == [(0,''),(0,'')]
    for token, thread, base, action in [(first,2,200,'joke'),('33333333-3333-3333-3333-333333333333',1,100,'joke')]:
        try:offer(token,thread,base,action)
        except sqlite3.IntegrityError:pass
        else:raise AssertionError('Token and per-message action uniqueness must prevent duplicate authorizations')
    assert db.execute('SELECT COUNT(*) FROM attention_actions').fetchone() == (2,)
    assert db.execute("UPDATE attention_actions SET state='queued' WHERE "+offer_where,(first,)).rowcount == 1
    assert db.execute("UPDATE attention_actions SET state='queued' WHERE "+offer_where,(first,)).rowcount == 0
    db.commit()

    # Both workers observe queued, then compete through the actual atomic native
    # claim predicate. Exactly one may proceed, even across database connections.
    barrier = threading.Barrier(2)
    def claim():
        conn=sqlite3.connect(path,timeout=5)
        assert conn.execute('SELECT state FROM attention_actions WHERE token=?',(first,)).fetchone()==('queued',)
        barrier.wait(timeout=5)
        with conn:changed=conn.execute("UPDATE attention_actions SET state='running' WHERE "+claim_where,(first,)).rowcount
        conn.close();return changed
    with ThreadPoolExecutor(max_workers=2) as pool:outcomes=list(pool.map(lambda _:claim(),range(2)))
    assert sorted(outcomes)==[0,1], outcomes
    assert db.execute("UPDATE attention_actions SET state='running' WHERE "+claim_where,(first,)).rowcount==0
    assert db.execute("UPDATE attention_actions SET state='complete',note='Fixture complete' WHERE "+finish_where,(first,)).rowcount==1
    assert db.execute("UPDATE attention_actions SET state='failed' WHERE "+finish_where,(first,)).rowcount==0, 'Late completion cannot rewrite a terminal result'
    # ContentValues are represented as bound SQL values; the native token link
    # predicate and transaction/dispatch ordering above are read from source.
    db.commit()
    assert db.execute("UPDATE attention_actions SET state='running' WHERE "+offer_where,(delay,)).rowcount==1
    db.commit()
    class RejectedLink(Exception):pass
    def link_delay(token):
        with db:
            job=db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,approved,status,created) VALUES(1,'+12025550100',?,100,1,1700000800000,1,'scheduled',1700000800000)",(delay_text,)).lastrowid
            db.execute("UPDATE jobs SET attention_kind='delay',location_revision=0,location_expires=0 WHERE _id=?",(job,))
            if db.execute("UPDATE attention_actions SET job_id=? WHERE "+link_where,(job,token)).rowcount!=1:
                raise RejectedLink()
        return job
    delay_job=link_delay(delay)
    assert db.execute('SELECT job_id FROM attention_actions WHERE token=?',(delay,)).fetchone()==(delay_job,)
    assert db.execute('SELECT body,attention_kind,approved,auto_send,location_revision,location_expires FROM jobs WHERE _id=?',(delay_job,)).fetchone()==(delay_text,'delay',1,0,0,0)
    # No FK points at job 0: offered/queued actions deliberately predate a job.
    # Linking is guarded and transactional instead, and dispatch revalidates it.
    assert db.execute('PRAGMA foreign_key_list(attention_actions)').fetchall()==[]
    wrong_action='44444444-4444-4444-4444-444444444444'
    unclaimed='55555555-5555-5555-5555-555555555555'
    offer(wrong_action,thread=2,base=200,action='joke')
    offer(unclaimed,thread=2,base=200,action='delay')
    db.execute("UPDATE attention_actions SET state='running' WHERE token=?",(wrong_action,))
    db.commit()
    job_count=db.execute('SELECT COUNT(*) FROM jobs').fetchone()[0]
    for token in [delay, first, wrong_action, unclaimed, 'ffffffff-ffff-ffff-ffff-ffffffffffff']:
        try:link_delay(token)
        except RejectedLink:pass
        else:raise AssertionError('Used, wrong-action and missing tokens must reject links')
        assert db.execute('SELECT COUNT(*) FROM jobs').fetchone()[0]==job_count, 'Failed link must roll back its inserted carrier job'
    # Actual style-evidence query includes explicit deferrals among messages to
    # exclude, but cannot sweep in an ordinary sent manual SMS.
    db.execute("UPDATE jobs SET uri='content://sms/999',status='sent' WHERE _id=?",(delay_job,))
    style_rows=db.execute(style_query+'?,?,?)',('content://sms/3','content://sms/4','content://sms/999')).fetchall()
    assert {row[0] for row in style_rows}=={'content://sms/3','content://sms/999'}
    # A bound carrier job cannot accidentally become a new background AI claim.
    db.execute("UPDATE attention_actions SET state='queued' WHERE token=?",(delay,))
    assert db.execute("UPDATE attention_actions SET state='running' WHERE "+claim_where,(delay,)).rowcount==0
    assert db.execute("UPDATE attention_actions SET state='queued' WHERE "+offer_where,(delay,)).rowcount==0
    db.commit();db.close()
    db=sqlite3.connect(path)
    assert db.execute('PRAGMA user_version').fetchone()==(14,)
    assert db.execute('SELECT state FROM attention_actions WHERE token=?',(first,)).fetchone()==('complete',)
    assert db.execute('SELECT state,job_id FROM attention_actions WHERE token=?',(delay,)).fetchone()==('queued',delay_job)
    for name in names:
        actual=original_rows(db,name,columns[name])
        if name=='jobs':actual=actual[:len(before[name])]
        assert actual==before[name],name
    assert db.execute('SELECT COUNT(*) FROM jobs').fetchone()[0]==len(before['jobs'])+1
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    db.close()

    # A process can die after the queue commit but before JobScheduler.schedule.
    # Reopen an actual SQLite file and exercise the production recovery queries:
    # interrupted work becomes terminal; only still-queued Joke work may drain.
    recovery_path=Path(folder)/'recovery.db'
    db=sqlite3.connect(recovery_path)
    for sql in re.findall(r'db\.execSQL\("(CREATE [^"]+)"\)',source):db.execute(sql)
    fixtures=[('queued-joke','joke','queued',0),('running-joke','joke','running',0),
              ('running-delay','delay','running',201),('complete','joke','complete',0),
              ('offered','joke','offered',0),('stale','joke','stale',0),
              ('queued-delay','delay','queued',0)]
    tokens={name:f'{index:08x}-0000-0000-0000-000000000000' for index,(name,*_) in enumerate(fixtures,1)}
    replacement='90000000-0000-0000-0000-000000000000'
    for index,(name,action,state,job_id) in enumerate(fixtures,1):
        db.execute("INSERT INTO attention_actions(token,thread,base,action,reason,profile_revision,config_revision,fingerprint,burst,state,created,job_id) VALUES(?,?,100,?,'plans_need_input',9,'fictional-config','hash',12,?,1700000000000,?)",(tokens[name],index,action,state,job_id))
    for job_id,kind in [(201,'delay'),(202,'')]:
        db.execute("INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,created,attention_kind) VALUES(?,3,'+12025550100','Fictional saved text',100,1,1700000800000,1,'scheduled',1700000000000,?)",(job_id,kind))
    db.commit();db.close()
    db=sqlite3.connect(recovery_path)
    recovered={row[0] for row in db.execute(recover_query)}
    assert recovered=={tokens['running-joke'],tokens['running-delay']}
    for token in recovered:
        assert db.execute("UPDATE attention_actions SET state='failed' WHERE "+finish_where,(token,)).rowcount==1
    assert db.execute("UPDATE jobs SET status='paused',approved=0 WHERE "+interrupted_delay_where).rowcount==1
    assert [row[0] for row in db.execute(queued_query)]==[tokens['queued-joke']], 'Recovery cannot schedule a Delay or replay started Joke work'
    assert db.execute('SELECT _id,status,approved FROM jobs ORDER BY _id').fetchall()==[(201,'paused',0),(202,'scheduled',1)]
    assert db.execute("UPDATE attention_actions SET state='running' WHERE "+claim_where,(tokens['queued-joke'],)).rowcount==1
    assert db.execute("UPDATE attention_actions SET state='running' WHERE "+claim_where,(tokens['queued-joke'],)).rowcount==0
    assert db.execute("UPDATE attention_actions SET state='complete' WHERE "+finish_where,(tokens['queued-joke'],)).rowcount==1
    # Explicit retry rotates a token only when no carrier job was ever linked.
    assert db.execute("UPDATE attention_actions SET token=?,state='offered' WHERE "+reoffer_where,(replacement,tokens['running-joke'])).rowcount==1
    assert db.execute("UPDATE attention_actions SET token=?,state='offered' WHERE "+reoffer_where,(replacement,tokens['running-delay'])).rowcount==0
    assert db.execute("UPDATE attention_actions SET state='queued' WHERE "+offer_where,(tokens['running-joke'],)).rowcount==0, 'The old notification token stays invalid'
    db.commit();db.close()
    db=sqlite3.connect(recovery_path)
    assert db.execute(recover_query).fetchall()==[]
    assert db.execute(queued_query).fetchall()==[]
    assert db.execute('SELECT state,job_id FROM attention_actions WHERE token=?',(tokens['running-delay'],)).fetchone()==('failed',201)
    assert db.execute('SELECT state FROM attention_actions WHERE token=?',(replacement,)).fetchone()==('offered',)
    assert db.execute('SELECT COUNT(*) FROM jobs').fetchone()==(2,), 'Recovery must not create a carrier job'
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    db.close()

print('PASS: real v11→v14 preserves profiles, drafts, exact jobs, provenance, receipts and planning holds; old jobs default to no attention authorization; fresh schema agrees; token/action uniqueness, atomic competing claims, job-bound exclusion, terminal idempotency, transactional Delay linkage/rollback and reopen persistence pass; fresh-process recovery drains only queued Joke work, fails interrupted work, pauses interrupted Delay jobs without touching manual timers, and permits only unlinked explicit token rotation; deferrals are excluded from style evidence. Android scheduling and carrier behavior remain native.')
