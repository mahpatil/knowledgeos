package com.knowledgeos.service;

import com.knowledgeos.domain.MemoryEntry;
import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.CreateMemoryRequest;
import com.knowledgeos.dto.MemoryResponse;
import com.knowledgeos.dto.MemorySearchRequest;
import com.knowledgeos.memory.QdrantMemoryStore;
import com.knowledgeos.repository.MemoryRepository;
import com.knowledgeos.repository.ProjectRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final int SCRATCH_TTL_HOURS = 4;

    @Inject MemoryRepository memoryRepository;
    @Inject ProjectRepository projectRepository;
    @Inject QdrantMemoryStore qdrantStore;
    @Inject TimelineService timelineService;

    @Transactional
    public MemoryResponse write(UUID projectId, CreateMemoryRequest req) {
        // Service-level justification guard (in addition to @NotBlank on DTO)
        if (req.justification() == null || req.justification().isBlank()) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST,
                "justification must not be blank");
        }

        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        MemoryEntry entry = new MemoryEntry();
        entry.setProject(project);
        entry.setTitle(req.title());
        entry.setContent(req.content());
        entry.setJustification(req.justification());
        entry.setLayer(req.layer());
        entry.setScopeKey(req.scopeKey());
        entry.setTags(toJsonArray(req.tags()));

        // Scratch layer gets a 4-hour TTL
        if ("scratch".equals(req.layer())) {
            entry.setExpiresAt(OffsetDateTime.now().plusHours(SCRATCH_TTL_HOURS));
        }

        // Assign a stable Qdrant ID before saving
        UUID qdrantId = UUID.randomUUID();
        entry.setQdrantId(qdrantId);

        entry = memoryRepository.save(entry);

        // Asynchronously upsert to Qdrant (failures are logged, not propagated)
        String textToEmbed = req.title() + " " + req.content();
        qdrantStore.upsert(qdrantId, projectId, req.layer(), textToEmbed,
            Map.of("memoryId", entry.getId().toString(),
                   "title", req.title(),
                   "scopeKey", req.scopeKey() != null ? req.scopeKey() : ""));

        log.info("Memory written: id={} layer={} project={}", entry.getId(), req.layer(), projectId);
        timelineService.log(projectId, null, "memory_written",
            Map.of("memoryId", entry.getId().toString(), "layer", req.layer(), "title", req.title()));
        return toResponse(entry);
    }

    public List<MemoryResponse> list(UUID projectId, String layer) {
        List<MemoryEntry> entries = (layer != null)
            ? memoryRepository.findByProjectIdAndLayer(projectId, layer)
            : memoryRepository.findByProjectId(projectId);

        return entries.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(UUID projectId, UUID memId) {
        MemoryEntry entry = memoryRepository.findById(memId)
            .filter(m -> m.getProject().getId().equals(projectId))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Memory entry not found: " + memId));

        if (entry.getQdrantId() != null) {
            qdrantStore.delete(entry.getQdrantId());
        }
        memoryRepository.delete(entry);
        log.info("Memory deleted: id={} project={}", memId, projectId);
        timelineService.log(projectId, null, "memory_deleted",
            Map.of("memoryId", memId.toString()), "user");
    }

    public List<MemoryResponse> search(UUID projectId, MemorySearchRequest req) {
        // Ask Qdrant for top-K point IDs ordered by semantic similarity
        List<UUID> qdrantIds = qdrantStore.search(projectId, req.layer(), req.query(), req.limit());

        if (qdrantIds.isEmpty()) {
            // Fallback: return all entries for the project (plain DB listing)
            return list(projectId, req.layer());
        }

        // Fetch entries in ranked order
        List<MemoryEntry> entries = memoryRepository.findByQdrantIdIn(qdrantIds);
        // Re-order to match Qdrant ranking
        Map<UUID, MemoryEntry> byQdrantId = new java.util.HashMap<>();
        for (MemoryEntry e : entries) {
            if (e.getQdrantId() != null) byQdrantId.put(e.getQdrantId(), e);
        }

        return qdrantIds.stream()
            .map(byQdrantId::get)
            .filter(java.util.Objects::nonNull)
            .map(this::toResponse)
            .toList();
    }

    /**
     * Scheduled cleaner — removes expired scratch entries every hour.
     */
    @Scheduled(fixedDelay = "1h")
    @Transactional
    void cleanExpiredScratch() {
        try {
            int deleted = memoryRepository.deleteExpiredBefore(OffsetDateTime.now());
            if (deleted > 0) {
                log.info("Cleaned {} expired scratch memory entries", deleted);
            }
        } catch (Exception e) {
            log.warn("Error cleaning expired memory entries: {}", e.getMessage());
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private MemoryResponse toResponse(MemoryEntry m) {
        return new MemoryResponse(
            m.getId(),
            m.getTitle(),
            m.getContent(),
            m.getJustification(),
            m.getLayer(),
            m.getScopeKey(),
            fromJsonArray(m.getTags()),
            null,  // score — only set from Qdrant search results
            m.getCreatedAt(),
            m.getExpiresAt()
        );
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i).replace("\"", "\\\"")).append("\"");
            if (i < list.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private List<String> fromJsonArray(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return List.of();
        try {
            String inner = json.trim().replaceFirst("^\\[", "").replaceFirst("]$", "").trim();
            if (inner.isEmpty()) return List.of();
            List<String> result = new java.util.ArrayList<>();
            for (String part : inner.split(",(?=\")")) {
                result.add(part.trim().replaceAll("^\"|\"$", ""));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
