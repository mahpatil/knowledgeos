package com.knowledgeos.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket endpoint that streams real-time timeline events to UI clients.
 *
 * Connect: ws://host/ws/events/{projectId}
 * Each message is a JSON-serialized {@link com.knowledgeos.dto.TimelineEventResponse}.
 *
 * Session registry is in-process only (single node). For multi-node deployments
 * this would need a Redis pub/sub bus.
 */
@Singleton
@ServerWebSocket("/ws/events/{projectId}")
public class AgentEventWebSocket {

    private static final Logger log = LoggerFactory.getLogger(AgentEventWebSocket.class);

    /** projectId → set of open WebSocket sessions */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> registry =
        new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(String projectId, WebSocketSession session) {
        registry.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("Events WS opened: project={} session={} total={}",
            projectId, session.getId(), registry.get(projectId).size());
    }

    @OnClose
    public void onClose(String projectId, WebSocketSession session) {
        Set<WebSocketSession> sessions = registry.get(projectId);
        if (sessions != null) {
            sessions.remove(session);
        }
        log.debug("Events WS closed: project={} session={}", projectId, session.getId());
    }

    @OnMessage
    public void onMessage(String projectId, String message, WebSocketSession session) {
        // Clients don't send messages — ignore
    }

    /**
     * Broadcast a pre-serialized JSON string to all subscribers of the project.
     * Called by {@link com.knowledgeos.service.TimelineService} on every log() call.
     * Failures are swallowed — WebSocket delivery is best-effort.
     */
    public void broadcast(String projectId, String jsonMessage) {
        Set<WebSocketSession> sessions = registry.get(projectId);
        if (sessions == null || sessions.isEmpty()) return;

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendAsync(jsonMessage);
                } catch (Exception e) {
                    log.debug("WS send failed for session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
        log.debug("Broadcast to {} subscribers for project {}", sessions.size(), projectId);
    }
}
