package com.knowledgeos;

import com.knowledgeos.dto.CreateProjectRequest;
import com.knowledgeos.dto.CreateWorkspaceRequest;
import com.knowledgeos.dto.ProjectResponse;
import com.knowledgeos.dto.WorkspaceResponse;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class WorkspaceControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    private UUID projectId;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @BeforeEach
    void setup() {
        HttpResponse<ProjectResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("WS Test Project", "software", null)),
            ProjectResponse.class
        );
        projectId = resp.getBody().get().id();
    }

    @Test
    void createWorkspace_software_returns201() {
        var req = new CreateWorkspaceRequest("main-code", "code", "read-write");

        HttpResponse<WorkspaceResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/workspaces", req),
            WorkspaceResponse.class
        );

        AssertionsForClassTypes.assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        WorkspaceResponse ws = response.getBody().get();
        Assertions.assertThat(ws.id()).isNotNull();
        Assertions.assertThat(ws.name()).isEqualTo("main-code");
        Assertions.assertThat(ws.type()).isEqualTo("code");
        Assertions.assertThat(ws.mode()).isEqualTo("read-write");
        Assertions.assertThat(ws.pvcName()).isNotBlank();
        Assertions.assertThat(ws.path()).isNotBlank();
    }

    @Test
    void createWorkspace_software_createsCorrectDirectoryStructure() {
        var req = new CreateWorkspaceRequest("src-workspace", "code", null);

        HttpResponse<WorkspaceResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/workspaces", req),
            WorkspaceResponse.class
        );

        WorkspaceResponse ws = response.getBody().get();
        // Workspace path should exist on filesystem
        Assertions.assertThat(Path.of(ws.path())).exists();
        // .mcp/ scaffold should exist
        Assertions.assertThat(Path.of(ws.path(), ".mcp")).isDirectory();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "config.json")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "locks.json")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "memory-refs.json")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "agent-log")).isDirectory();
    }

    @Test
    void createWorkspace_contentBook_createsOutlineAndChapters() {
        var req = new CreateWorkspaceRequest("book-content", "content", null);

        HttpResponse<WorkspaceResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/workspaces", req),
            WorkspaceResponse.class
        );

        WorkspaceResponse ws = response.getBody().get();
        Assertions.assertThat(Path.of(ws.path(), "outline.md")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), "chapters")).isDirectory();
    }

    @Test
    void createWorkspace_migration_createsThreeSubWorkspaces() {
        // Migration project gets legacy + target + mapping workspaces
        HttpResponse<ProjectResponse> migrationProject = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("Migration Project", "migration", null)),
            ProjectResponse.class
        );
        UUID migProjectId = migrationProject.getBody().get().id();

        var req = new CreateWorkspaceRequest("legacy", "legacy", "read-only");

        HttpResponse<WorkspaceResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + migProjectId + "/workspaces", req),
            WorkspaceResponse.class
        );

        AssertionsForClassTypes.assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        WorkspaceResponse ws = response.getBody().get();
        Assertions.assertThat(ws.mode()).isEqualTo("read-only");
        Assertions.assertThat(ws.type()).isEqualTo("legacy");
    }
}
