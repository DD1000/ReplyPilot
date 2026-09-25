#!/usr/bin/env python3
"""Verify the shipped v17 → v18 migration with old sends, profiles, and owner drafts."""
from pathlib import Path
import sqlite3,re,tempfile
root=Path(__file__).resolve().parents[1]
store=(root/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
assert 'super(c,"pilot.db",null,18)' in store
create=store.split('private static void createAutopilot(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
upgrade=store.split('if(oldVersion<18){',1)[1].split('\n        }',1)[0]
statements=re.findall(r'db.execSQL\("([^"]+)"\)',create)+re.findall(r'db.execSQL\("([^"]+)"\)',upgrade)
with tempfile.TemporaryDirectory(prefix='pilot-autopilot-migration-') as temp:
 path=Path(temp)/'pilot.db';db=sqlite3.connect(path);db.executescript((root/'tools/fixtures/autopilot-v17.sql').read_text())
 for thread,flags,delay in [(1,(1,1,1),60),(2,(1,1,0),1800),(3,(0,0,0),300),(4,(1,1,1),1)]:
  db.execute('INSERT INTO relationships(thread,body,revision,cloud_enabled,auto_draft,auto_send,auto_delay,samples,important_details) VALUES(?,?,7,?,?,?,?,?,?)',(thread,'private guidance',*flags,delay,'owner examples','boundaries'))
 for ident,state,auto,kind in [(1,'scheduled',1,''),(2,'awaiting_alert',1,''),(3,'sending',1,''),(4,'sent',1,''),(5,'scheduled',0,''),(6,'scheduled',0,'delay')]:
  db.execute('INSERT INTO jobs(_id,thread,address,body,base,sub,due,approved,status,created,auto_send,attention_kind) VALUES(?,1,?, ?,100,1,1000,1,?,0,?,?)',(ident,'+12025550101','fixture reply',state,auto,kind))
 for thread,engine in [(1,'Edited by you'),(2,'OpenAI · test')]:db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine) VALUES(?,100,'unsent text','[]',?)",(thread,engine))
 db.execute("INSERT INTO manual_sends(request_id,thread,address,body,sub,job_id,created) VALUES('same-tap',1,'+12025550101','owner text',1,3,0)")
 before=db.execute('SELECT * FROM manual_sends').fetchall()
 for statement in statements:db.execute(statement)
 profiles=db.execute('SELECT thread,cloud_enabled,auto_draft,auto_send,auto_delay,engagement,plan_handling,body,samples,important_details,revision FROM relationships ORDER BY thread').fetchall()
 assert [p[1:5] for p in profiles]==[(1,1,1,60),(0,0,0,0),(0,0,0,300),(1,1,1,0)]
 assert all(p[5:7]==('always_reply','delay_answer') and p[7:]==('private guidance','owner examples','boundaries',8) for p in profiles)
 assert db.execute('SELECT _id,status,approved FROM jobs ORDER BY _id').fetchall()==[(1,'paused',0),(2,'paused',0),(3,'sending',1),(4,'sent',1),(5,'scheduled',1),(6,'paused',0)]
 assert db.execute('SELECT thread,body FROM drafts').fetchall()==[(1,'unsent text')]
 assert db.execute('SELECT * FROM manual_sends').fetchall()==before
 assert {'submitted','notified'}<= {r[1] for r in db.execute('PRAGMA table_info(autopilot_attention)')}
 # Attention must distinguish carrier submission from a queued/crashed draft.
 db.execute("INSERT INTO autopilot_attention(job,thread,reason,created) VALUES(3,1,'plans',0)")
 assert db.execute('SELECT submitted,notified FROM autopilot_attention').fetchone()==(0,0)
 db.commit();db.close();db=sqlite3.connect(path)
 assert db.execute('SELECT * FROM manual_sends').fetchall()==before
 assert db.execute('SELECT submitted,notified FROM autopilot_attention').fetchone()==(0,0)
 assert db.execute('PRAGMA integrity_check').fetchone()==('ok',)
print('PASS: v17→18 preserves owner drafts, pairing-independent contact guidance and manual-send identity; only existing Autopilot stays enabled; retired unsent AI work pauses; submission-bound attention survives restart.')
