-- V5: Memory entries
-- Three-layer memory system:
--   canonical: permanent, project-wide, never expires
--   feature:   project-scoped, survives sprints
--   scratch:   agent-scoped, 4-hour TTL — expires_at is set on write
--
-- qdrant_id: UUID used to store/retrieve the embedding vector in Qdrant.
-- justification: required (NOT NULL, NOT empty) — agents must explain why they memorize.

CREATE TABLE memory_entries (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id    UUID         NOT NULL REFERENCES projects(id),
    title         VARCHAR(512) NOT NULL,
    content       TEXT         NOT NULL,
    justification TEXT         NOT NULL,     -- API validates: @NotBlank enforced in service too
    layer         VARCHAR(20)  NOT NULL
                               CHECK (layer IN ('canonical', 'feature', 'scratch')),
    scope_key     VARCHAR(255),              -- e.g. feature-branch name, sprint ID
    tags          JSONB        NOT NULL DEFAULT '[]',
    qdrant_id     UUID,                      -- Qdrant point ID for semantic search
    expires_at    TIMESTAMPTZ,               -- NULL for canonical/feature; 4h from now for scratch
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_memory_project ON memory_entries(project_id);
CREATE INDEX idx_memory_layer   ON memory_entries(project_id, layer);
CREATE INDEX idx_memory_expires ON memory_entries(expires_at)
    WHERE expires_at IS NOT NULL;
CREATE INDEX idx_memory_scope   ON memory_entries(project_id, scope_key)
    WHERE scope_key IS NOT NULL;
