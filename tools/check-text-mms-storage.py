#!/usr/bin/env python3
"""Run actual v15→16 draft migration SQL and source binding/owner-edit persistence."""
from pathlib import Path
import re, sqlite3, tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
assert 'super(c,"pilot.db",null,18)' in s
old="CREATE TABLE drafts(thread INTEGER PRIMARY KEY, base INTEGER NOT NULL, body TEXT NOT NULL, alternatives TEXT NOT NULL, engine TEXT NOT NULL, location_revision INTEGER NOT NULL DEFAULT 0, location_expires INTEGER NOT NULL DEFAULT 0)"
block=s.split('if(oldVersion<16){',1)[1].split('}',1)[0]
alters=re.findall(r'db.execSQL\("([^"]+)"\)',block)
assert len(alters)==4
fresh=re.search(r'db.execSQL\("(CREATE TABLE drafts[^"\n]+)"\)',s).group(1)
with tempfile.TemporaryDirectory(prefix='pilot-mms-text-') as temp:
    path=Path(temp)/'db';db=sqlite3.connect(path)
    db.execute(old);db.execute("INSERT INTO drafts VALUES(1,7,'Keep my reply','[]','Edited by you',23,5000)")
    for sql in alters: db.execute(sql)
    assert db.execute('SELECT * FROM drafts').fetchone()==(1,7,'Keep my reply','[]','Edited by you',23,5000,0,0,'','')
    other=sqlite3.connect(':memory:');other.execute(fresh)
    assert db.execute('PRAGMA table_info(drafts)').fetchall()==other.execute('PRAGMA table_info(drafts)').fetchall()
    db.execute("INSERT INTO drafts(thread,base,body,alternatives,engine,source_mms,source_mms_date,source_text,source_address) VALUES(2,0,'Been good','[]','OpenAI · awaiting your review',99,8000,'How you been','+12025550100')")
    db.commit();db.close();db=sqlite3.connect(path)
    assert db.execute('SELECT source_mms,source_mms_date,source_text,source_address FROM drafts WHERE thread=2').fetchone()==(99,8000,'How you been','+12025550100')
    # The existing owner-save API uses REPLACE: omitted source columns must reset.
    db.execute("INSERT OR REPLACE INTO drafts(thread,base,body,alternatives,engine,location_revision,location_expires) VALUES(2,0,'My revised answer','[]','Edited by you',0,0)")
    assert db.execute('SELECT source_mms,source_mms_date,source_text,source_address FROM drafts WHERE thread=2').fetchone()==(0,0,'','')
    assert db.execute('SELECT body FROM drafts WHERE thread=1').fetchone()==('Keep my reply',)
print('PASS: v15→16 draft migration preserves existing text/location metadata; source binding survives reopen; explicit owner edit clears only its binding.')
