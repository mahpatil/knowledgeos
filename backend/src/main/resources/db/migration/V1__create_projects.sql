-- V1: Projects table
-- Represents a knowledge-work project. Namespace is provisioned as a k8s namespace.
-- policy_set stores project-level agent permission policies as JSONB.

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL CHECK (type IN ('software','content','marketing','migration','research')),
    namespace   VARCHAR(63)  NOT NULL UNIQUE,    -- k8s namespace name (max 63 chars)
    status      VARCHAR(50)  NOT NULL DEFAULT 'active'
                             CHECK (status IN ('active','paused','archived')),
    policy_set  JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_status    ON projects(status);
CREATE INDEX idx_projects_type      ON projects(type);
CREATE INDEX idx_projects_created   ON projects(created_at DESC);
