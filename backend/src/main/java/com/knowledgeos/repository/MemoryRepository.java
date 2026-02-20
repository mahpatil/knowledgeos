package com.knowledgeos.repository;

import com.knowledgeos.domain.MemoryEntry;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRepository extends JpaRepository<MemoryEntry, UUID> {

    List<MemoryEntry> findByProjectId(UUID projectId);

    @Query("FROM MemoryEntry m WHERE m.project.id = :projectId AND m.layer = :layer")
    List<MemoryEntry> findByProjectIdAndLayer(UUID projectId, String layer);

    /**
     * Delete expired scratch entries — called by the scheduled expiry cleaner.
     */
    @Query("DELETE FROM MemoryEntry m WHERE m.expiresAt < :now")
    int deleteExpiredBefore(OffsetDateTime now);

    List<MemoryEntry> findByQdrantIdIn(List<UUID> qdrantIds);
}
