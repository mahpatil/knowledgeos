package com.knowledgeos.service;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.Project;
import com.knowledgeos.domain.TimelineEvent;
import com.knowledgeos.dto.TimelineEventResponse;
import com.knowledgeos.dto.TimelinePage;
import com.knowledgeos.repository.AgentRepository;
import com.knowledgeos.repository.ProjectRepository;
import com.knowledgeos.repository.TimelineEventRepository;
import com.knowledgeos.websocket.AgentEventWebSocket;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Cross-cutting timeline log.
 *
 * Every service calls {@link #log} to record what happened.
 * Events are persisted in {@code timeline_events} and immediately broadcast
 * to WebSocket subscribers of {@code /ws/events/{projectId}}.
 *
 * Cursor pagination: cursor = base64( offset integer ).
 * Default page size: 20 events, newest first.
 */
@Singleton
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);
    private static final int DEFAULT_LIMIT = 20;

    @Inject TimelineEventRepository eventRepository;
    @Inject ProjectRepository projectRepository;
    @Inject AgentRepository agentRepository;
    @Inject AgentEventWebSocket webSocket;

    /**
     * Record a timeline event and broadcast it to WebSocket subscribers.
     *
     * @param projectId project scope (required)
     * @param agentId   agent that triggered the event (nullable)
     * @param type      event type (e.g. "changeset_submitted")
     * @param payload   key-value metadata stored as JSONB
     * @param source    "pod" | "local" | "user"
     */
    @Transactional
    public void log(UUID projectId, UUID agentId, String type,
                    Map<String, Object> payload, String source) {
        try {
            Project project = projectRepository.findById(projectId).orElse(null);
            if (project == null) {
                TimelineService.log.warn("Timeline log skipped — project not found: {}", projectId);
                return;
            }

            TimelineEvent event = new TimelineEvent();
            event.setProject(project);
            event.setType(type);
            event.setSource(source != null ? source : "user");

            if (agentId != null) {
                agentRepository.findById(agentId).ifPresent(event::setAgent);
            }

            event.setPayload(toJson(payload != null ? payload : Map.of()));
            event = eventRepository.save(event);

            // Broadcast to WebSocket subscribers (best-effort)
            try {
                webSocket.broadcast(projectId.toString(), toJson(responseToMap(event)));
            } catch (Exception e) {
                TimelineService.log.debug("WS broadcast skipped: {}", e.getMessage());
            }

            TimelineService.log.debug("Timeline[{}] {} project={}", type, source, projectId);

        } catch (Exception e) {
            // Timeline must never throw — log and swallow
            TimelineService.log.warn("Timeline log failed for type={}: {}", type, e.getMessage());
        }
    }

    /** Convenience: derive source from agent type. */
    public void log(UUID projectId, UUID agentId, String type, Map<String, Object> payload) {
        String source = "user";
        if (agentId != null) {
            source = agentRepository.findById(agentId)
                .map(Agent::getAgentType)
                .orElse("user");
        }
        log(projectId, agentId, type, payload, source);
    }

    public TimelinePage list(UUID projectId, String cursor, Integer limit, String type) {
        int pageSize = (limit != null && limit > 0 && limit <= 100) ? limit : DEFAULT_LIMIT;
        int offset   = decodeCursor(cursor);

        Pageable pageable = Pageable.from(offset, pageSize);
        List<TimelineEvent> events = eventRepository.findByProject(projectId, type, pageable);

        long total = eventRepository.countByProject(projectId, type);
        boolean hasMore = (offset + pageSize) < total;
        String nextCursor = hasMore ? encodeCursor(offset + pageSize) : null;

        return new TimelinePage(
            events.stream().map(this::toResponse).toList(),
            nextCursor,
            hasMore
        );
    }

    public TimelineEventResponse getById(UUID projectId, UUID eventId) {
        return eventRepository.findByIdAndProjectId(eventId, projectId)
            .map(this::toResponse)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "Timeline event not found: " + eventId));
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private TimelineEventResponse toResponse(TimelineEvent e) {
        Map<String, Object> payloadMap = fromJson(e.getPayload());
        return new TimelineEventResponse(
            e.getId(),
            e.getType(),
            payloadMap,
            e.isReversible(),
            e.getReplayCmd(),
            e.getAgent() != null ? e.getAgent().getId() : null,
            e.getCreatedAt()
        );
    }

    /** Build a simple map for WS broadcast (avoid ObjectMapper dependency). */
    private Map<String, Object> responseToMap(TimelineEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId() != null ? e.getId().toString() : null);
        m.put("type", e.getType());
        m.put("source", e.getSource());
        m.put("agentId", e.getAgent() != null ? e.getAgent().getId().toString() : null);
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return m;
    }

    /**
     * Minimal JSON serializer for {@code Map<String, Object>}.
     * Values must be strings, numbers, booleans, or null.
     * Used instead of Jackson ObjectMapper to avoid a bean dependency.
     */
    static String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":");
            Object val = entry.getValue();
            if (val == null) {
                sb.append("null");
            } else if (val instanceof Number || val instanceof Boolean) {
                sb.append(val);
            } else {
                sb.append("\"").append(escape(val.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /** Parse a flat JSON object string back into a Map (best-effort). */
    @SuppressWarnings("unchecked")
    static Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return Map.of();
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            String inner = json.trim();
            if (inner.startsWith("{")) inner = inner.substring(1);
            if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);
            // Split on top-level commas (simplified — no nested objects)
            for (String pair : splitTopLevel(inner)) {
                int colon = pair.indexOf(':');
                if (colon < 0) continue;
                String key = pair.substring(0, colon).trim().replaceAll("^\"|\"$", "");
                String rawVal = pair.substring(colon + 1).trim();
                if ("null".equals(rawVal)) {
                    result.put(key, null);
                } else if (rawVal.startsWith("\"")) {
                    result.put(key, rawVal.replaceAll("^\"|\"$", "").replace("\\\"", "\""));
                } else {
                    result.put(key, rawVal);
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            else if (!inStr && (c == '{' || c == '[')) depth++;
            else if (!inStr && (c == '}' || c == ']')) depth--;
            else if (!inStr && depth == 0 && c == ',') {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length()) parts.add(s.substring(start).trim());
        return parts;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            return Integer.parseInt(new String(Base64.getDecoder().decode(cursor)));
        } catch (Exception e) {
            return 0;
        }
    }

    private String encodeCursor(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes());
    }
}
