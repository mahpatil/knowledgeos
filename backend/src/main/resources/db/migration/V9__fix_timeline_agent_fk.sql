-- V9: Fix timeline_events.agent_id FK to use ON DELETE SET NULL.
-- When an agent is deleted, its timeline events are retained (agent_id → null).

ALTER TABLE timeline_events
    DROP CONSTRAINT timeline_events_agent_id_fkey,
    ADD CONSTRAINT timeline_events_agent_id_fkey
        FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE SET NULL;
