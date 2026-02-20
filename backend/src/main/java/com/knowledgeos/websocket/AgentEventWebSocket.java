package com.knowledgeos.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket endpoint for real-time agent events.
 *
 * Broadcasts timeline events to connected clients for a given project:
 * - changeset_submitted, human_approval_requested
 * - agent_spawned, agent_stopped
 * - lock_acquired, lock_released
 * - validator_run results
 */
@ServerWebSocket("/ws/events/{projectId}")
public class AgentEventWebSocket {

    private static final Logger log = LoggerFactory.getLogger(AgentEventWebSocket.class);

    @OnOpen
    public void onOpen(String projectId, WebSocketSession session) {
        log.info("Events WS opened: project={} session={}", projectId, session.getId());
        // TODO (Phase 1D): register session in EventBroadcaster for this projectId
    }

    @OnClose
    public void onClose(String projectId, WebSocketSession session) {
        log.info("Events WS closed: project={} session={}", projectId, session.getId());
        // TODO (Phase 1D): deregister session from EventBroadcaster
    }
}
