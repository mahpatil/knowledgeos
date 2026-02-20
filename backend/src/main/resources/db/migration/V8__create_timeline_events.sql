-- V8: Timeline events — cross-cutting audit log for every significant action.
--
-- Every service (ProjectService, AgentService, ChangeSetService, FileLockService,
-- MemoryService) calls TimelineService.log() to write an event here.
-- The WebSocket (/ws/events/{projectId}) broadcasts each new event to UI clients.
--
-- source: who initiated the action
--   pod   — Kubernetes-hosted agent
--   local — Claude Code running on developer machine via MCP server
--   user  — direct API call from a human

CREATE TABLE timeline_events (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id  UUID        NOT NULL REFERENCES projects(id),
    agent_id    UUID        REFERENCES agents(id),
    type        VARCHAR(60) NOT NULL,
    payload     JSONB       NOT NULL DEFAULT '{}',
    source      VARCHAR(20) NOT NULL DEFAULT 'user'
                            CHECK (source IN ('pod', 'local', 'user')),
    reversible  BOOLEAN     NOT NULL DEFAULT FALSE,
    replay_cmd  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_timeline_project      ON timeline_events(project_id, created_at DESC);
CREATE INDEX idx_timeline_agent        ON timeline_events(agent_id);
CREATE INDEX idx_timeline_project_type ON timeline_events(project_id, type);
