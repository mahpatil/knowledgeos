package com.knowledgeos.repository;

import com.knowledgeos.domain.TimelineEvent;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.CrudRepository;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimelineEventRepository extends CrudRepository<TimelineEvent, UUID> {

    /** All events for a project, newest first, with optional type filter. */
    @Query("FROM TimelineEvent e WHERE e.project.id = :projectId AND (:type IS NULL OR e.type = :type) ORDER BY e.createdAt DESC")
    List<TimelineEvent> findByProject(UUID projectId, @Nullable String type, Pageable pageable);

    /** Count events matching the same criteria (for hasMore calculation). */
    @Query("SELECT COUNT(e) FROM TimelineEvent e WHERE e.project.id = :projectId AND (:type IS NULL OR e.type = :type)")
    long countByProject(UUID projectId, @Nullable String type);

    Optional<TimelineEvent> findByIdAndProjectId(UUID id, UUID projectId);
}
