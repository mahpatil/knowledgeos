package com.knowledgeos;

import com.knowledgeos.dto.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.core.type.Argument;
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
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

/**
 * Full lifecycle integration test for Phase 1.
 * Tests the happy path: create project → create workspace → spawn agent → verify .mcp/ scaffold.
 */
@MicronautTest
class Phase1IntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void fullLifecycle_createProject_workspace_agent_terminalStream() {
        // 1. Create project
        HttpResponse<ProjectResponse> projectResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("Integration Project", "software", null)),
            ProjectResponse.class
        );
        AssertionsForClassTypes.assertThat(projectResp.getStatus()).isEqualTo(HttpStatus.CREATED);
        ProjectResponse project = projectResp.getBody().get();
        Assertions.assertThat(project.id()).isNotNull();
        Assertions.assertThat(project.namespace()).startsWith("project-");

        // 2. Create workspace
        HttpResponse<WorkspaceResponse> wsResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + project.id() + "/workspaces",
                new CreateWorkspaceRequest("main", "code", "read-write")),
            WorkspaceResponse.class
        );
        AssertionsForClassTypes.assertThat(wsResp.getStatus()).isEqualTo(HttpStatus.CREATED);
        WorkspaceResponse ws = wsResp.getBody().get();
        Assertions.assertThat(ws.path()).isNotBlank();

        // 3. Verify .mcp/ scaffold written to workspace
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "config.json")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "locks.json")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "memory-refs.json")).isRegularFile();
        Assertions.assertThat(Path.of(ws.path(), ".mcp", "agent-log")).isDirectory();

        // 4. Spawn agent
        HttpResponse<AgentResponse> agentResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + project.id() + "/agents",
                new CreateAgentRequest("Implementer", "claude", "Implementer", null, null, ws.id(), null)),
            AgentResponse.class
        );
        AssertionsForClassTypes.assertThat(agentResp.getStatus()).isEqualTo(HttpStatus.CREATED);
        AgentResponse agent = agentResp.getBody().get();
        Assertions.assertThat(agent.id()).isNotNull();

        // 5. Verify agent is listed
        HttpResponse<List<Map>> agentListResp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + project.id() + "/agents"),
            Argument.listOf(Map.class)
        );
        Assertions.assertThat(agentListResp.getBody().get()).hasSize(1);
    }

    @Test
    void createWorkspace_writesMcpDirectoryWithCorrectFiles() {
        HttpResponse<ProjectResponse> projectResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("MCP Scaffold Test", "software", null)),
            ProjectResponse.class
        );
        String projectId = projectResp.getBody().get().id().toString();

        HttpResponse<WorkspaceResponse> wsResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/workspaces",
                new CreateWorkspaceRequest("mcp-test-ws", "code", null)),
            WorkspaceResponse.class
        );

        WorkspaceResponse ws = wsResp.getBody().get();
        Path wsPath = Path.of(ws.path());

        // Verify .mcp/ directory structure
        Assertions.assertThat(wsPath.resolve(".mcp")).isDirectory();
        Assertions.assertThat(wsPath.resolve(".mcp/config.json")).isRegularFile();
        Assertions.assertThat(wsPath.resolve(".mcp/locks.json")).isRegularFile();
        Assertions.assertThat(wsPath.resolve(".mcp/memory-refs.json")).isRegularFile();
        Assertions.assertThat(wsPath.resolve(".mcp/agent-log")).isDirectory();

        // Verify config.json has expected keys
        try {
            String configContent = java.nio.file.Files.readString(wsPath.resolve(".mcp/config.json"));
            Assertions.assertThat(configContent).contains("workspaceId");
            Assertions.assertThat(configContent).contains("projectId");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
