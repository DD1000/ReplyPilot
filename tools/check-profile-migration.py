"""Exercise historical v1-v5 relationship layouts through current profile SQL."""
from pathlib import Path
from store_migrations import upgrade_v14, upgrade_v11, assert_v11_defaults, upgrade_v12, assert_v12_defaults, upgrade_v13, assert_v13_defaults, original_rows
import re, sqlite3
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/contentfoundry/replypilot/Store.java').read_text()
current_version=int(re.search(r'super\(c,"pilot.db",null,(\d+)\)',source).group(1))
assert current_version in (8,9,10,11,12,13,14), 'Review newer profile migrations before extending this check'
# Use the shipped historical schema so new columns cannot silently appear in an
# old-version fixture before its actual migration has been exercised.
historical=(root/'tools/fixtures/store-v5-schema.sql').read_text()
tables={re.match(r'CREATE TABLE (\w+)',sql.strip()).group(1):sql.strip() for sql in historical.split(';') if sql.strip()}
creates=[tables[name] for name in ('jobs','receipts','drafts','relationships')]
rel_alters=re.findall(r'db.execSQL\("(ALTER TABLE relationships [^"]+)"\)',source)
rel_alters=[sql for sql in rel_alters if re.search(r'ADD COLUMN (samples|cloud_enabled|auto_draft|relationship_kind|tone|auto_send|auto_delay) ',sql)]
current_relationship=re.search(r'db\.execSQL\("(CREATE TABLE relationships[^"]+)"\)',source).group(1)
range_alters=[]
if current_version>=9:
    guarded=re.search(r'if\(oldVersion>=2&&oldVersion<9\)\s*\{([^}]+)\}',source)
    assert guarded, 'v1 already creates the current relationship table; do not add duplicate range columns'
    range_alters=re.findall(r'db\.execSQL\("([^"]+)"\)',guarded.group(1))
    assert len(range_alters)==3
job_alters=re.findall(r'db.execSQL\("(ALTER TABLE jobs [^"]+)"\)',source)
job_alters=[sql for sql in job_alters if re.search(r'ADD COLUMN (auto_send|profile_revision|config_revision|original) ',sql)]
profile_sql=re.search(r'execSQL\("(INSERT INTO relationships\(thread,body,revision,samples[^"]+)"',source).group(1)
assert len(creates)==4 and len(rel_alters)==7 and len(job_alters)==4
old_jobs=creates[0].split(', auto_send INTEGER')[0]+')'
old2="CREATE TABLE relationships(thread INTEGER PRIMARY KEY, body TEXT NOT NULL, revision INTEGER NOT NULL)"
old3=old2[:-1]+", samples TEXT NOT NULL DEFAULT '', cloud_enabled INTEGER NOT NULL DEFAULT 0, auto_draft INTEGER NOT NULL DEFAULT 0)"
old4=old3[:-1]+", relationship_kind TEXT NOT NULL DEFAULT '', tone TEXT NOT NULL DEFAULT '')"
job_columns='_id,thread,address,body,base,sub,due,approved,status,note,uri,parts,created'
for version in (1,2,3,4,5):
    db=sqlite3.connect(':memory:')
    db.execute(creates[0] if version==5 else old_jobs)
    for sql in creates[1:3]:db.execute(sql)
    if version>=2:db.execute({2:old2,3:old3,4:old4,5:creates[-1]}[version])
    db.execute("INSERT INTO jobs(thread,address,body,base,sub,due,approved,status,created) VALUES(1,'+12025550147','approved exact text',7,1,123,1,'scheduled',100)")
    db.execute("INSERT INTO drafts VALUES(1,7,'edited draft','[]','Edited by you')")
    if version>=2:db.execute("INSERT INTO relationships(thread,body,revision) VALUES(1,?,9)",('existing note '+'x'*1400,))
    if version>=3:db.execute("UPDATE relationships SET samples='Me: original sample',cloud_enabled=1,auto_draft=1 WHERE thread=1")
    if version>=4:db.execute("UPDATE relationships SET relationship_kind='Partner',tone='Warm' WHERE thread=1")
    jobs=db.execute('SELECT '+job_columns+' FROM jobs').fetchall();drafts=db.execute('SELECT * FROM drafts').fetchall();draft_columns=[row[1] for row in db.execute('PRAGMA table_info(drafts)')]
    # The version guards below match Store.onUpgrade. Version 1 creates the current relationship table directly.
    if version==1:db.execute(current_relationship)
    else:
        if version<3:
            for sql in rel_alters[:3]:db.execute(sql)
        if version<4:
            for sql in rel_alters[3:5]:db.execute(sql)
        if version<5:
            for sql in rel_alters[5:]:db.execute(sql)
    if version<5:
        for sql in job_alters:db.execute(sql)
    if version>=2:
        for sql in range_alters:db.execute(sql)
    if current_version>=10:
        assert 'if(oldVersion<10)createReplyHolds(db);' in source
        holds=source.split('private static void createReplyHolds(SQLiteDatabase db){',1)[1].split('\n    }',1)[0]
        for sql in re.findall(r'db\.execSQL\("([^"]+)"\)',holds):db.execute(sql)
        assert db.execute('SELECT COUNT(*) FROM reply_holds').fetchone()==(0,), 'Upgrading does not invent a planning hold'
    if current_version>=11:
        upgrade_v11(db,source,version)
        assert_v11_defaults(db)
    if current_version>=12:
        upgrade_v12(db,source)
        assert_v12_defaults(db)
        if current_version>=13:
            upgrade_v13(db,source,version)
            assert_v13_defaults(db)
            upgrade_v14(db,source)
    if version>=2:
        row=db.execute('SELECT body,revision,samples,cloud_enabled,auto_draft,relationship_kind,tone,auto_send,auto_delay FROM relationships WHERE thread=1').fetchone()
        assert row[0].startswith('existing note ') and row[1]==9
        assert row[-2:]==(0,300),'An upgrade must never enable unreviewed sending'
        assert row[5:7]==(('Partner','Warm') if version>=4 else ('',''))
        assert row[2:5]==(('Me: original sample',1,1) if version>=3 else ('',0,0))
    assert db.execute('SELECT auto_send,profile_revision,config_revision,original FROM jobs').fetchone()==(0,0,'','')
    extra=('fixed',300,1800) if current_version>=9 else ()
    db.execute(profile_sql,(1,'','',1,1,'Friend','Use AI intuition',1,600)+extra+(('always_reply',1) if current_version>=11 else ()) + ((0,'') if current_version>=13 else ()))
    db.execute(profile_sql,(2,'different person','Me: yes',1,0,'Coworker','Professional',0,300)+extra+(('keep_going',0) if current_version>=11 else ()) + ((0,'') if current_version>=13 else ()))
    assert db.execute('SELECT relationship_kind,tone,body,auto_send,auto_delay FROM relationships WHERE thread=1').fetchone()==('Friend','Use AI intuition','',1,600)
    assert db.execute('SELECT relationship_kind,tone,auto_draft,auto_send FROM relationships WHERE thread=2').fetchone()==('Coworker','Professional',0,0)
    if current_version>=11:
        assert db.execute('SELECT engagement,share_location FROM relationships ORDER BY thread').fetchall()==[('always_reply',1),('keep_going',0)]
    db.execute(profile_sql,(1,'changed dynamics','old samples',0,0,'Family','',0,900)+extra+(('natural',0) if current_version>=11 else ()) + ((0,'') if current_version>=13 else ()))
    assert db.execute('SELECT tone,auto_send FROM relationships WHERE thread=2').fetchone()==('Professional',0)
    if current_version>=11:
        assert db.execute('SELECT engagement,share_location FROM relationships ORDER BY thread').fetchall()==[('natural',0),('keep_going',0)], 'Removing location permission or changing engagement affects only the chosen person'
    assert db.execute('SELECT '+job_columns+' FROM jobs').fetchall()==jobs
    assert original_rows(db,'drafts',draft_columns)==drafts
    db.close()
print(f'PASS: historical v1–v5 profiles upgrade through v{current_version} without duplicate columns, preserve notes/samples/tones/opt-ins/manual jobs/drafts, and support current profile saves; per-person delays, engagement and explicit location opt-ins stay isolated.')
