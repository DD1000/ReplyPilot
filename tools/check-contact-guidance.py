#!/usr/bin/env python3
"""Exercise historical contact-guidance SQL and verify retired actions cannot run."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import re
import sqlite3
import tempfile

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/com/contentfoundry/replypilot'
store=(JAVA/'Store.java').read_text()
actions=(JAVA/'AttentionActions.java').read_text()
assert 'super(c,"pilot.db",null,18)' in store
# Fixed shipped v14 tables, not a v15 schema with columns retrospectively removed.
V14="""
CREATE TABLE relationships(thread INTEGER PRIMARY KEY, body TEXT NOT NULL, revision INTEGER NOT NULL, samples TEXT NOT NULL DEFAULT '', cloud_enabled INTEGER NOT NULL DEFAULT 0, auto_draft INTEGER NOT NULL DEFAULT 0, relationship_kind TEXT NOT NULL DEFAULT '', tone TEXT NOT NULL DEFAULT '', auto_send INTEGER NOT NULL DEFAULT 0, auto_delay INTEGER NOT NULL DEFAULT 300, auto_delay_mode TEXT NOT NULL DEFAULT 'fixed', auto_delay_min INTEGER NOT NULL DEFAULT 300, auto_delay_max INTEGER NOT NULL DEFAULT 1800, engagement TEXT NOT NULL DEFAULT 'natural', share_location INTEGER NOT NULL DEFAULT 0, humor_level INTEGER NOT NULL DEFAULT 0, inside_jokes TEXT NOT NULL DEFAULT '');
CREATE TABLE reply_holds(thread INTEGER PRIMARY KEY, base INTEGER NOT NULL, latest_base INTEGER NOT NULL, created INTEGER NOT NULL);
CREATE TABLE attention_actions(token TEXT PRIMARY KEY, thread INTEGER NOT NULL, base INTEGER NOT NULL, action TEXT NOT NULL, reason TEXT NOT NULL, profile_revision INTEGER NOT NULL, config_revision TEXT NOT NULL, fingerprint TEXT NOT NULL, burst INTEGER NOT NULL, state TEXT NOT NULL, created INTEGER NOT NULL, job_id INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', UNIQUE(thread,base,action));
"""
block=store.split('if(oldVersion<15){',1)[1].split('\n        }',1)[0]
assert 'if(oldVersion>=2)' in block and 'if(oldVersion>=10)' in block and 'if(oldVersion>=12)' in block
alters=re.findall(r'db.execSQL\("([^"]+)"\)',block)
assert len(alters)==5
profile_sql=re.search(r'execSQL\("(INSERT INTO relationships\(thread,body,revision,samples[^"]+)"',store).group(1)
hold_sql=re.search(r'execSQL\("(INSERT INTO reply_holds\(thread,base,latest_base,created\) VALUES\(\?,\?,\?,\?\)[^"]+)"',store).group(1)
assert 'static JSONObject claimPlanDelay(Context c){return null;}' in actions
assert 'static JSONObject claim(Context c){return null;}' in actions
with tempfile.TemporaryDirectory(prefix='pilot-contact-sql-') as tmp:
    path=Path(tmp)/'fixture.db';db=sqlite3.connect(path);db.executescript(V14)
    db.execute("INSERT INTO relationships(thread,body,revision,samples,cloud_enabled,auto_draft,auto_send,relationship_kind,tone,humor_level,inside_jokes,auto_delay,engagement) VALUES(1,'Original dynamic',7,'Original examples',1,1,1,'Friend','Myself (beta)',4,'Original private joke',300,'girlfriend')")
    db.execute("INSERT INTO relationships(thread,body,revision) VALUES(2,'Other contact',2)")
    db.execute('INSERT INTO reply_holds VALUES(1,100,101,1234)')
    db.execute("INSERT INTO attention_actions(token,thread,base,action,reason,profile_revision,config_revision,fingerprint,burst,state,created) VALUES('old',1,101,'joke','plans_need_input',7,'config','fingerprint',8,'offered',1234)")
    old={table:db.execute(f'SELECT * FROM {table} ORDER BY 1').fetchall() for table in ['relationships','reply_holds','attention_actions']}
    columns={table:[r[1] for r in db.execute(f'PRAGMA table_info({table})')] for table in old}
    for sql in alters:db.execute(sql)
    for table,rows in old.items():assert db.execute(f'SELECT {",".join(columns[table])} FROM {table} ORDER BY 1').fetchall()==rows
    assert db.execute('SELECT important_details,plan_handling FROM relationships').fetchall()==[('','ask_me'),('','ask_me')]
    assert db.execute('SELECT delay_attempted FROM reply_holds').fetchone()==(0,)
    assert db.execute('SELECT auto_plan,auto_sleep_revision FROM attention_actions').fetchone()==(0,0)
    fresh=sqlite3.connect(':memory:')
    for table in old:
        sql=re.search(r'db.execSQL\("(CREATE TABLE '+table+r'\([^"]+)"',store).group(1);fresh.execute(sql)
        fields=lambda c:{row[1]:row[2:] for row in c.execute(f'PRAGMA table_info({table})')}
        old_fields,new_fields=fields(db),fields(fresh)
        # New installs now default to Instant; old SQLite defaults are retained
        # until the explicit v18 value migration (tested separately).
        if table=='relationships':
            assert old_fields['auto_delay'][2]=='300' and new_fields['auto_delay'][2]=='0'
            new_fields['auto_delay']=old_fields['auto_delay']
        assert old_fields==new_fields,table
    details='d'*2000
    values=(1,'Updated dynamic','New examples',1,1,1,60,'fixed',300,1800,'girlfriend',0,details,'delay_answer')
    db.execute(profile_sql,values)
    assert db.execute('SELECT body,important_details,plan_handling,revision,auto_delay FROM relationships WHERE thread=1').fetchone()==('Updated dynamic',details,'delay_answer',8,60)
    assert db.execute('SELECT relationship_kind,tone,humor_level,inside_jokes FROM relationships WHERE thread=1').fetchone()==('Friend','Myself (beta)',4,'Original private joke')
    assert db.execute('SELECT important_details,plan_handling FROM relationships WHERE thread=2').fetchone()==('','ask_me')
    db.commit();db.close();db=sqlite3.connect(path)
    assert db.execute('SELECT body,important_details FROM relationships WHERE thread=1').fetchone()==('Updated dynamic',details)
    assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
    db.close()
print('PASS: historical v14→15 guidance migration preserves data; current guidance storage survives restart; retired planning/attention actions cannot claim old work. Current v17→18 mode migration is checked separately.')
