package com.knowledgeos.repository;

import com.knowledgeos.domain.FileLock;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface FileLockRepository extends JpaRepository<FileLock, UUID> {

    List<FileLock> findByProjectId(UUID projectId);

    /**
     * Find active write locks on any of the given file paths.
     * Used by ChangeSetService.submit() to detect write-lock conflicts.
     * Called inside @Transactional — Hibernate applies optimistic locking at flush.
     */
    @Query("FROM FileLock f WHERE f.project.id = :projectId " +
           "AND f.filePath IN (:filePaths) " +
           "AND f.lockType = 'write' " +
           "AND f.expiresAt > CURRENT_TIMESTAMP")
    List<FileLock> findActiveWriteLocksForFiles(UUID projectId, List<String> filePaths);

    /**
     * Find all active locks for a project (both read and write, not yet expired).
     */
    @Query("FROM FileLock f WHERE f.project.id = :projectId AND f.expiresAt > CURRENT_TIMESTAMP")
    List<FileLock> findActiveByProjectId(UUID projectId);

    /**
     * Delete locks that have passed their expiry — called by the scheduled expiry cleaner.
     */
    @Query("DELETE FROM FileLock f WHERE f.expiresAt < CURRENT_TIMESTAMP")
    int deleteExpired();
}
