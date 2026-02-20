package com.knowledgeos.repository;

import com.knowledgeos.domain.Agent;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {

    List<Agent> findByProjectId(UUID projectId);

    List<Agent> findByProjectIdAndStatus(UUID projectId, String status);
}
