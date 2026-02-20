-- V4: ChangeSets and FileLocks
-- ChangeSets track proposed file changes from agents before applying to workspace.
-- Status flow: pending → auto_applied | agent_review | human_review → approved | rejected → applied | rolled_back
--
-- CRITICAL: ChangeSetService.submit() uses SELECT FOR UPDATE on file_locks
-- to atomically detect write-lock conflicts before persisting a new changeset.

CREATE TABLE changesets (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id        UUID        NOT NULL REFERENCES projects(id),
    agent_id          UUID        REFERENCES agents(id),     -- nullable: system or user can submit
    intent            TEXT        NOT NULL,
    files_changed     JSONB       NOT NULL DEFAULT '[]',     -- ["src/Foo.java", "src/Bar.java"]
    diff              TEXT        NOT NULL,                  -- unified diff content
    reverse_diff      TEXT,                                  -- stored after apply, used for rollback
    status            VARCHAR(50) NOT NULL DEFAULT 'pending'
                                  CHECK (status IN (
                                      'pending', 'auto_applied', 'agent_review', 'human_review',
                                      'approved', 'rejected', 'applied', 'rolled_back'
                                  )),
    auto_apply_policy VARCHAR(30) DEFAULT 'never'
                                  CHECK (auto_apply_policy IN ('always', 'on_tests_pass', 'never')),
    tests_run         JSONB       NOT NULL DEFAULT '[]',     -- test identifiers that were run
    validator_results JSONB,                                 -- ValidatorResultResponse JSON
    reject_reason     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_changesets_project ON changesets(project_id);
CREATE INDEX idx_changesets_agent   ON changesets(agent_id);
CREATE INDEX idx_changesets_status  ON changesets(status);
CREATE INDEX idx_changesets_created ON changesets(created_at);

-- FileLocks prevent concurrent writes to the same workspace file.
-- SELECT FOR UPDATE on this table is used in ChangeSetService.submit()
-- to ensure no two agents can concurrently write the same file.

CREATE TABLE file_locks (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id  UUID        NOT NULL REFERENCES projects(id),
    file_path   TEXT        NOT NULL,
    lock_type   VARCHAR(10) NOT NULL DEFAULT 'write'
                            CHECK (lock_type IN ('read', 'write')),
    locked_by   UUID        REFERENCES agents(id),           -- nullable: system-level lock
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Only one write lock per file per project (partial unique index)
CREATE UNIQUE INDEX idx_file_locks_write ON file_locks(project_id, file_path)
    WHERE lock_type = 'write';

CREATE INDEX idx_file_locks_project   ON file_locks(project_id);
CREATE INDEX idx_file_locks_expires   ON file_locks(expires_at);
CREATE INDEX idx_file_locks_file_path ON file_locks(project_id, file_path);
