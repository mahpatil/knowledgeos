package com.knowledgeos.repository;

import com.knowledgeos.domain.ChangeSet;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChangeSetRepository extends JpaRepository<ChangeSet, UUID> {

    List<ChangeSet> findByProjectId(UUID projectId);
}
