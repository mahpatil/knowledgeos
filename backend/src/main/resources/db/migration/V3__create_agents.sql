-- V3: Agents table
-- Each agent maps to a running Kubernetes Pod in the project namespace.
-- model: claude | codex
-- role: one of 11 defined agent roles
-- permissions: JSONB {"read": [paths], "write": [paths], "execute": bool}
-- token_count / cost_usd: accumulated usage for cost tracking

CREATE TABLE agents (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    workspace_id    UUID         REFERENCES workspaces(id),
    name            VARCHAR(255) NOT NULL,
    model           VARCHAR(50)  NOT NULL CHECK (model IN ('claude','codex')),
    role            VARCHAR(50)  NOT NULL CHECK (role IN (
                        'Architect','Implementer','Reviewer','Tester','Debugger',
                        'Refactorer','DocumentationWriter','ReverseEngineer',
                        'DomainModeler','Translator','Verifier'
                    )),
    status          VARCHAR(50)  NOT NULL DEFAULT 'pending'
                                 CHECK (status IN ('pending','running','stopped','failed','terminated')),
    pod_name        VARCHAR(253),
    prompt          TEXT,
    permissions     JSONB        NOT NULL DEFAULT '{"read":[],"write":[],"execute":false}',
    workspace_mounts JSONB       NOT NULL DEFAULT '[]',
    token_count     BIGINT       NOT NULL DEFAULT 0,
    cost_usd        NUMERIC(12,6) NOT NULL DEFAULT 0.0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agents_project    ON agents(project_id);
CREATE INDEX idx_agents_status     ON agents(status);
CREATE INDEX idx_agents_workspace  ON agents(workspace_id);
