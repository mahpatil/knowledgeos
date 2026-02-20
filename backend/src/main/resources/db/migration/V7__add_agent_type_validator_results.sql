-- V7: Add agent_type to agents, create validator_results table
--
-- agent_type: 'pod' (Kubernetes container, default) | 'local' (Claude Code on developer machine)
-- validator_results: stores the output of running project-type-specific validators

ALTER TABLE agents
    ADD COLUMN agent_type VARCHAR(20) NOT NULL DEFAULT 'pod'
        CHECK (agent_type IN ('pod', 'local'));

CREATE TABLE validator_results (
    id             UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    changeset_id   UUID        NOT NULL REFERENCES changesets(id) ON DELETE CASCADE,
    passed         BOOLEAN     NOT NULL,
    failures       JSONB       NOT NULL DEFAULT '[]',
    duration_ms    BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_validator_results_changeset ON validator_results(changeset_id);
