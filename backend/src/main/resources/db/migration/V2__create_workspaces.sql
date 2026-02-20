-- V2: Workspaces table
-- Each workspace maps to a Kubernetes PVC mounted into agent pods.
-- type: code | content | legacy | target | mapping
-- mode: read-write (default) | read-only
-- pvc_name: the k8s PersistentVolumeClaim name
-- path: absolute path inside the cluster (and on the host via hostMount)

CREATE TABLE workspaces (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id  UUID         NOT NULL REFERENCES projects(id),
    name        VARCHAR(255) NOT NULL,
    type        VARCHAR(50)  NOT NULL CHECK (type IN ('code','content','legacy','target','mapping')),
    mode        VARCHAR(20)  NOT NULL DEFAULT 'read-write'
                             CHECK (mode IN ('read-write','read-only')),
    path        TEXT         NOT NULL,
    pvc_name    VARCHAR(253) NOT NULL,   -- k8s PVC names max 253 chars
    file_tree   JSONB        NOT NULL DEFAULT '[]',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, name)
);

CREATE INDEX idx_workspaces_project ON workspaces(project_id);
