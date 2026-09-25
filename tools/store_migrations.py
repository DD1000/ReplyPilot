"""Small shared extractors for the current Store v11/v12/v13 migration SQL.

No current CREATE schema is used to fake an earlier database. Unexpected Java
migration structure fails here so additions cannot silently bypass coverage.
"""
import re


def upgrade_v11(db, source, old_version):
    assert 'if(oldVersion<11){' in source
    block = source.split('if(oldVersion<11){', 1)[1].split('\n        }', 1)[0]
    profile_guard = re.search(r'if\(oldVersion>=2\)\{([^\n]+)\}', block)
    assert profile_guard, 'v1 creates the latest relationship schema directly'
    profile = re.findall(r'db\.execSQL\("([^"]+)"\)', profile_guard.group(1))
    assert profile == [
        "ALTER TABLE relationships ADD COLUMN engagement TEXT NOT NULL DEFAULT 'natural'",
        'ALTER TABLE relationships ADD COLUMN share_location INTEGER NOT NULL DEFAULT 0',
    ]
    loop = re.search(r'for\(String table:new String\[\]\{([^}]+)\}\)\{([^\n]+)\}', block)
    assert loop, 'Expected the real draft/job provenance migration loop'
    tables = re.findall(r'"([^"]+)"', loop.group(1))
    fragments = re.findall(r'db\.execSQL\("([^"]+)"\+table\+"([^"]+)"\)', loop.group(2))
    assert tables == ['drafts', 'jobs'] and fragments == [
        ('ALTER TABLE ', ' ADD COLUMN location_revision INTEGER NOT NULL DEFAULT 0'),
        ('ALTER TABLE ', ' ADD COLUMN location_expires INTEGER NOT NULL DEFAULT 0'),
    ]
    if old_version >= 2:
        for sql in profile:
            db.execute(sql)
    for table in tables:
        for prefix, suffix in fragments:
            db.execute(prefix + table + suffix)


def assert_v11_defaults(db):
    assert all(row == ('natural', 0) for row in db.execute('SELECT engagement,share_location FROM relationships'))
    for table in ['drafts', 'jobs']:
        assert all(row == (0, 0) for row in db.execute(f'SELECT location_revision,location_expires FROM {table}')), table


def original_rows(db, table, columns):
    return db.execute(f'SELECT {",".join(columns)} FROM {table} ORDER BY 1').fetchall()


def upgrade_v12(db, source):
    block = re.search(r'if\(oldVersion<12\)\{([^\n]+)\}', source)
    assert block and 'createAttentionActions(db);' in block.group(1), 'Expected the actual v12 migration guard and helper'
    alters = re.findall(r'db\.execSQL\("([^"]+)"\)', block.group(1))
    assert alters == ["ALTER TABLE jobs ADD COLUMN attention_kind TEXT NOT NULL DEFAULT ''"]
    helper = source.split('private static void createAttentionActions(SQLiteDatabase db){', 1)[1].split('\n    }', 1)[0]
    creates = re.findall(r'db\.execSQL\("([^"]+)"\)', helper)
    assert len(creates) == 2 and creates[0].startswith('CREATE TABLE attention_actions(')
    assert 'UNIQUE(thread,base,action)' in creates[0]
    assert creates[1] == 'CREATE INDEX attention_pending ON attention_actions(state,created)'
    for sql in alters + creates:
        db.execute(sql)


def assert_v12_defaults(db):
    assert all(row == ('',) for row in db.execute('SELECT attention_kind FROM jobs'))
    assert db.execute('SELECT COUNT(*) FROM attention_actions').fetchone() == (0,), 'An upgrade cannot invent notification authorization'


def upgrade_v13(db, source, old_version):
    block = re.search(r'if\(oldVersion>=2&&oldVersion<13\)\{([^\n]+)\}', source)
    assert block, 'v1 creates the current relationship table, so must not add humor columns again'
    additions = re.findall(r'db\.execSQL\("([^"]+)"\)', block.group(1))
    assert additions == [
        'ALTER TABLE relationships ADD COLUMN humor_level INTEGER NOT NULL DEFAULT 0',
        "ALTER TABLE relationships ADD COLUMN inside_jokes TEXT NOT NULL DEFAULT ''",
    ]
    if old_version >= 2:
        for sql in additions:
            db.execute(sql)


def assert_v13_defaults(db):
    assert all(row == (0, '') for row in db.execute('SELECT humor_level,inside_jokes FROM relationships')), 'Humor requires an explicit per-contact choice'


def upgrade_v14(db, source):
    assert 'if(oldVersion<14)createPilotTraining(db);' in source
    helper = source.split('private static void createPilotTraining(SQLiteDatabase db){', 1)[1].split('\n    }', 1)[0]
    creates = re.findall(r'db\.execSQL\("([^"]+)"\)', helper)
    assert len(creates) == 3
    assert creates[0].startswith('CREATE TABLE pilot_training(') and 'turn TEXT NOT NULL UNIQUE' in creates[0]
    assert creates[1] == 'CREATE INDEX pilot_training_contact ON pilot_training(thread,scope,_id)'
    assert creates[2].startswith('CREATE TABLE message_meanings(') and 'PRIMARY KEY(thread,scope,kind,message_id)' in creates[2]
    for sql in creates:
        db.execute(sql)
    assert db.execute('SELECT COUNT(*) FROM pilot_training').fetchone() == (0,)
    assert db.execute('SELECT COUNT(*) FROM message_meanings').fetchone() == (0,)
