package com.knowledgeos.repository;

import com.knowledgeos.domain.Project;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByStatusNot(String status);

    Optional<Project> findByNamespace(String namespace);
}
