package com.knowledgeos.service;

import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.CreateProjectRequest;
import com.knowledgeos.dto.ProjectResponse;
import com.knowledgeos.dto.UpdateProjectRequest;
import com.knowledgeos.k8s.NamespaceManager;
import com.knowledgeos.repository.ProjectRepository;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    @Inject ProjectRepository projectRepository;
    @Inject NamespaceManager namespaceManager;
    @Inject TimelineService timelineService;

    @Value("${kubernetes.workspace-base-path:/workspaces}")
    String workspaceBasePath;

    @Value("${app.api-key:dev-local-key}")
    String apiKey;

    @Transactional
    public ProjectResponse create(CreateProjectRequest req) {
        // Namespace derived from a random token (independent of JPA-generated entity ID)
        String namespace = "project-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        Project project = new Project(req.name(), req.type(), namespace);
        // Let @GeneratedValue handle UUID generation — do NOT pre-set the ID
        project = projectRepository.save(project);
        UUID id = project.getId();

        // Provision k8s namespace — failures are logged but don't fail the API call
        try {
            namespaceManager.provision(id.toString(), namespace);
        } catch (Exception e) {
            log.warn("Failed to provision k8s namespace {} — continuing: {}", namespace, e.getMessage());
        }

        // Write CLAUDE.md to workspace root so Claude Code knows its project context
        writeClaudeMd(id, req.name(), namespace);

        // Timeline
        timelineService.log(id, null, "project_created",
            Map.of("name", req.name(), "type", req.type()), "user");

        log.info("Project created: id={} name={} namespace={}", id, req.name(), namespace);
        return toResponse(project);
    }

    public List<ProjectResponse> listAll() {
        return projectRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public ProjectResponse getById(UUID id) {
        return projectRepository.findById(id)
            .map(this::toResponse)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "Project not found: " + id));
    }

    public Optional<Project> findEntityById(UUID id) {
        return projectRepository.findById(id);
    }

    @Transactional
    public ProjectResponse update(UUID id, UpdateProjectRequest req) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "Project not found: " + id));

        if (req.name() != null) project.setName(req.name());
        if (req.status() != null) project.setStatus(req.status());

        projectRepository.update(project);
        return toResponse(project);
    }

    @Transactional
    public void archive(UUID id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "Project not found: " + id));
        project.setStatus("archived");
        projectRepository.update(project);
        log.info("Project archived: id={}", id);
    }

    private void writeClaudeMd(UUID projectId, String projectName, String namespace) {
        try {
            Path projectDir = Path.of(workspaceBasePath, namespace);
            Files.createDirectories(projectDir);
            String content = String.format("""
                # KnowledgeOS Project: %s

                PROJECT_ID=%s
                KOS_API=http://localhost:8080
                KOS_API_KEY=%s

                ## Instructions for Claude Code
                You are operating as a KnowledgeOS agent (agentType: local).
                - Call kos_register_agent at session start to appear in the project dashboard
                - Use kos_acquire_lock before editing any file (prevents concurrent conflicts)
                - Use kos_submit_changeset instead of direct commits (enables review + rollback)
                - Use kos_write_memory for decisions (layer: canonical) or findings (layer: scratch)
                - All activity is visible in real-time at http://localhost:5173/projects/%s
                """, projectName, projectId, apiKey, projectId);
            Files.writeString(projectDir.resolve("CLAUDE.md"), content);
            log.info("CLAUDE.md written to {}/CLAUDE.md", projectDir);
        } catch (IOException e) {
            log.warn("Could not write CLAUDE.md: {}", e.getMessage());
        }
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
            p.getId(),
            p.getName(),
            p.getType(),
            p.getNamespace(),
            p.getStatus(),
            p.getCreatedAt()
        );
    }
}
