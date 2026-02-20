package com.knowledgeos.service;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.FileLock;
import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.AcquireLockRequest;
import com.knowledgeos.dto.FileLockResponse;
import com.knowledgeos.repository.AgentRepository;
import com.knowledgeos.repository.FileLockRepository;
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

/**
 * Manages workspace file locks.
 *
 * Write locks are exclusive (one writer per file per project).
 * Read locks are shared (multiple readers allowed).
 *
 * Redis is used as a fast lookup cache (lock:{projectId}:{filePath}).
 * PostgreSQL is the source of truth for expiry and reclaim.
 */
@Singleton
public class FileLockService {

    private static final Logger log = LoggerFactory.getLogger(FileLockService.class);

    @Inject FileLockRepository fileLockRepository;
    @Inject ProjectRepository projectRepository;
    @Inject AgentRepository agentRepository;
    @Inject TimelineService timelineService;

    @Transactional
    public FileLockResponse acquire(UUID projectId, AcquireLockRequest req) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String lockType = req.lockType() != null ? req.lockType() : "write";
        int duration = req.durationSeconds() > 0 ? req.durationSeconds() : 300;

        // For write locks, check for existing write lock on this file (unique constraint in DB)
        if ("write".equals(lockType)) {
            List<FileLock> existing = fileLockRepository
                .findActiveWriteLocksForFiles(projectId, List.of(req.filePath()));
            if (!existing.isEmpty()) {
                throw new HttpStatusException(HttpStatus.CONFLICT,
                    "Write lock already held on: " + req.filePath());
            }
        }

        Agent agent = null;
        if (req.agentId() != null) {
            agent = agentRepository.findById(req.agentId()).orElse(null);
        }

        FileLock lock = new FileLock();
        lock.setProject(project);
        lock.setFilePath(req.filePath());
        lock.setLockType(lockType);
        lock.setLockedBy(agent);
        lock.setExpiresAt(OffsetDateTime.now().plusSeconds(duration));

        lock = fileLockRepository.save(lock);
        log.info("Lock acquired: id={} file={} type={} expires={}", lock.getId(), req.filePath(), lockType, lock.getExpiresAt());

        timelineService.log(projectId, req.agentId(), "lock_acquired",
            Map.of("lockId", lock.getId().toString(), "filePath", req.filePath(), "lockType", lockType));

        return toResponse(lock);
    }

    public List<FileLockResponse> listForProject(UUID projectId) {
        return fileLockRepository.findActiveByProjectId(projectId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void release(UUID projectId, UUID lockId) {
        FileLock lock = fileLockRepository.findById(lockId)
            .filter(l -> l.getProject().getId().equals(projectId))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Lock not found: " + lockId));

        fileLockRepository.delete(lock);
        log.info("Lock released: id={} file={}", lockId, lock.getFilePath());
        timelineService.log(projectId, null, "lock_released",
            Map.of("lockId", lockId.toString(), "filePath", lock.getFilePath()), "user");
    }

    @Transactional
    public FileLockResponse reclaim(UUID projectId, UUID lockId) {
        FileLock lock = fileLockRepository.findById(lockId)
            .filter(l -> l.getProject().getId().equals(projectId))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Lock not found: " + lockId));

        // Extend expiry by 5 minutes
        lock.setExpiresAt(OffsetDateTime.now().plusMinutes(5));
        lock = fileLockRepository.update(lock);
        log.info("Lock reclaimed: id={} new expires={}", lockId, lock.getExpiresAt());

        return toResponse(lock);
    }

    /**
     * Scheduled expiry cleaner — runs every 60 seconds.
     * Removes expired locks from the database.
     */
    @Scheduled(fixedDelay = "60s")
    @Transactional
    void cleanExpiredLocks() {
        try {
            int deleted = fileLockRepository.deleteExpired();
            if (deleted > 0) {
                log.info("Cleaned {} expired file locks", deleted);
            }
        } catch (Exception e) {
            log.warn("Error cleaning expired locks: {}", e.getMessage());
        }
    }

    private FileLockResponse toResponse(FileLock l) {
        return new FileLockResponse(
            l.getId(),
            l.getFilePath(),
            l.getLockType(),
            l.getLockedBy() != null ? l.getLockedBy().getId() : null,
            l.getExpiresAt(),
            l.getCreatedAt()
        );
    }
}
