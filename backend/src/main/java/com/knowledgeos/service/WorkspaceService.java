package com.knowledgeos.service;

import com.knowledgeos.domain.Project;
import com.knowledgeos.domain.Workspace;
import com.knowledgeos.dto.CreateWorkspaceRequest;
import com.knowledgeos.dto.WorkspaceResponse;
import com.knowledgeos.repository.WorkspaceRepository;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provisions workspaces: creates directory structure, .mcp/ scaffold,
 * and registers PVC metadata.
 *
 * In local dev, creates directories under workspace-base-path.
 * In production, the PVC is mounted at the same path inside the cluster.
 *
 * .mcp/ scaffold (critical — every safety mechanism depends on this):
 *   .mcp/config.json        — workspace identity and settings
 *   .mcp/locks.json         — active file locks mirror (updated by FileLockService)
 *   .mcp/memory-refs.json   — canonical memory references for this workspace
 *   .mcp/agent-log/         — per-agent command audit logs
 */
@Singleton
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    @Inject
    WorkspaceRepository workspaceRepository;

    @Value("${kubernetes.workspace-base-path:/workspaces}")
    String workspaceBasePath;

    @Transactional
    public WorkspaceResponse create(Project project, CreateWorkspaceRequest req) {
        // PVC name uses a random token (independent of JPA-generated entity ID)
        String pvcToken = UUID.randomUUID().toString();
        String pvcName = buildPvcName(project.getId().toString(), pvcToken);
        String path = buildPath(project.getNamespace(), req.name());
        String mode = req.mode() != null ? req.mode() : "read-write";

        // Create filesystem directories (doesn't require entity ID)
        List<String> fileTree = provisionDirectory(path, req.type());

        // Persist workspace entity — let @GeneratedValue produce the UUID
        Workspace ws = new Workspace();
        ws.setProject(project);
        ws.setName(req.name());
        ws.setType(req.type());
        ws.setMode(mode);
        ws.setPath(path);
        ws.setPvcName(pvcName);
        ws.setFileTree(toJson(fileTree));
        ws = workspaceRepository.save(ws);

        // Write .mcp/ scaffold after save so we have the real entity ID
        writeMcpScaffold(path, project.getId().toString(), ws.getId().toString());

        log.info("Workspace created: id={} path={} pvc={}", ws.getId(), path, pvcName);
        return toResponse(ws, fileTree);
    }

    public List<WorkspaceResponse> listForProject(UUID projectId) {
        return workspaceRepository.findByProjectId(projectId)
            .stream()
            .map(ws -> toResponse(ws, fromJsonList(ws.getFileTree())))
            .toList();
    }

    public Optional<WorkspaceResponse> findById(UUID id) {
        return workspaceRepository.findById(id)
            .map(ws -> toResponse(ws, fromJsonList(ws.getFileTree())));
    }

    public Optional<Workspace> findEntityById(UUID id) {
        return workspaceRepository.findById(id);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String buildPvcName(String projectId, String wsId) {
        String shortProjectId = projectId.replace("-", "").substring(0, 8);
        String shortWsId = wsId.replace("-", "").substring(0, 8);
        return "ws-" + shortProjectId + "-" + shortWsId;
    }

    private String buildPath(String namespace, String wsName) {
        return workspaceBasePath + "/" + namespace + "/" + wsName;
    }

    /**
     * Create workspace directory tree based on workspace type.
     * Returns list of created paths relative to workspace root.
     */
    private List<String> provisionDirectory(String path, String type) {
        List<String> created = new ArrayList<>();
        try {
            Files.createDirectories(Path.of(path));
            created.add("/");

            switch (type) {
                case "code" -> {
                    Files.createDirectories(Path.of(path, "src"));
                    Files.createDirectories(Path.of(path, "tests"));
                    Files.createDirectories(Path.of(path, "docs"));
                    Files.writeString(Path.of(path, "README.md"), "# Workspace\n");
                    created.addAll(List.of("src/", "tests/", "docs/", "README.md"));
                }
                case "content" -> {
                    Files.createDirectories(Path.of(path, "chapters"));
                    Files.createDirectories(Path.of(path, "assets"));
                    Files.writeString(Path.of(path, "outline.md"), "# Outline\n");
                    Files.writeString(Path.of(path, "style-guide.md"), "# Style Guide\n");
                    created.addAll(List.of("chapters/", "assets/", "outline.md", "style-guide.md"));
                }
                case "legacy" -> {
                    Files.createDirectories(Path.of(path, "source"));
                    Files.createDirectories(Path.of(path, "expected-output"));
                    created.addAll(List.of("source/", "expected-output/"));
                }
                case "target" -> {
                    Files.createDirectories(Path.of(path, "src"));
                    Files.createDirectories(Path.of(path, "output"));
                    Files.createDirectories(Path.of(path, "tests"));
                    created.addAll(List.of("src/", "output/", "tests/"));
                }
                case "mapping" -> {
                    Files.createDirectories(Path.of(path, "schemas"));
                    Files.createDirectories(Path.of(path, "mappings"));
                    Files.writeString(Path.of(path, "domain-model.md"), "# Domain Model\n");
                    created.addAll(List.of("schemas/", "mappings/", "domain-model.md"));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to provision workspace directory: " + path, e);
        }
        return created;
    }

    /**
     * Write the .mcp/ scaffold directory.
     * This is the foundation of agent isolation — every safety mechanism depends on it.
     */
    private void writeMcpScaffold(String workspacePath, String projectId, String workspaceId) {
        try {
            Path mcpDir = Path.of(workspacePath, ".mcp");
            Files.createDirectories(mcpDir);
            Files.createDirectories(mcpDir.resolve("agent-log"));

            // config.json — workspace identity
            String configJson = String.format(
                "{\n  \"workspaceId\": \"%s\",\n  \"projectId\": \"%s\",\n  \"version\": \"1.0\",\n  \"created\": \"%s\"\n}",
                workspaceId, projectId, java.time.Instant.now().toString());
            Files.writeString(mcpDir.resolve("config.json"), configJson);

            // locks.json — empty lock registry (updated by FileLockService)
            Files.writeString(mcpDir.resolve("locks.json"), "[]");

            // memory-refs.json — canonical memory references
            Files.writeString(mcpDir.resolve("memory-refs.json"), "[]");

        } catch (IOException e) {
            throw new RuntimeException("Failed to write .mcp scaffold at: " + workspacePath, e);
        }
    }

    private WorkspaceResponse toResponse(Workspace ws, List<String> fileTree) {
        return new WorkspaceResponse(
            ws.getId(),
            ws.getName(),
            ws.getType(),
            ws.getMode(),
            ws.getPath(),
            ws.getPvcName(),
            fileTree
        );
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            if (i < list.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return List.of();
        // Simple extraction: strip brackets and split by comma-quoted-string boundaries
        try {
            String inner = json.trim().replaceFirst("^\\[", "").replaceFirst("]$", "").trim();
            if (inner.isEmpty()) return List.of();
            List<String> result = new ArrayList<>();
            for (String part : inner.split(",(?=\")")) {
                result.add(part.trim().replaceAll("^\"|\"$", ""));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
