package com.knowledgeos.websocket;

import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket endpoint for terminal I/O streaming.
 *
 * Bridges the browser xterm.js terminal to the tmux session running inside
 * the agent pod via kubectl exec.
 *
 * Reference: dotai/server/socket/terminal.handler.ts (bidirectional PTY bridge)
 */
@ServerWebSocket("/ws/terminal/{projectId}/{agentId}")
@Tag(name = "websocket")
public class TerminalWebSocket {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocket.class);

    @OnOpen
    public void onOpen(String projectId, String agentId, WebSocketSession session) {
        log.info("Terminal WS opened: project={} agent={} session={}", projectId, agentId, session.getId());
        // TODO (Phase 1D): connect to TerminalGatewayService — attach to tmux session via kubectl exec
    }

    @OnMessage
    public void onMessage(String projectId, String agentId, String message, WebSocketSession session) {
        log.debug("Terminal input: project={} agent={} chars={}", projectId, agentId, message.length());
        // TODO (Phase 1D): forward input to CommandAuditLogger, then to kubectl exec stdin
    }

    @OnClose
    public void onClose(String projectId, String agentId, WebSocketSession session) {
        log.info("Terminal WS closed: project={} agent={} session={}", projectId, agentId, session.getId());
        // TODO (Phase 1D): clean up kubectl exec process, remove from session registry
    }
}
