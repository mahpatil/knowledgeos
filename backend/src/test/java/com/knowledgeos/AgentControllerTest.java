package com.knowledgeos;

import com.knowledgeos.dto.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

@MicronautTest
class AgentControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    // Inject the mock bean so verify() works correctly regardless of test instance ordering
    @Inject
    KubernetesClient k8sClient;

    private UUID projectId;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @BeforeEach
    void setup() {
        HttpResponse<ProjectResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("Agent Test Project", "software", null)),
            ProjectResponse.class
        );
        projectId = resp.getBody().get().id();
    }

    @Test
    void createAgent_validRole_returns201() {
        var req = new CreateAgentRequest("Claude Implementer", "claude", "Implementer", null, null, null);

        HttpResponse<AgentResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents", req),
            AgentResponse.class
        );

        AssertionsForClassTypes.assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        AgentResponse agent = response.getBody().get();
        Assertions.assertThat(agent.id()).isNotNull();
        Assertions.assertThat(agent.name()).isEqualTo("Claude Implementer");
        Assertions.assertThat(agent.model()).isEqualTo("claude");
        Assertions.assertThat(agent.role()).isEqualTo("Implementer");
        Assertions.assertThat(agent.status()).isIn("pending", "running");
    }

    @Test
    void createAgent_invalidRole_returns400() {
        var req = new CreateAgentRequest("Bad Agent", "claude", "InvalidRole", null, null, null);

        assertThatThrownBy(() ->
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/projects/" + projectId + "/agents", req),
                AgentResponse.class
            )
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void createAgent_spawnsKubernetesPod() {
        var req = new CreateAgentRequest("Architect Agent", "claude", "Architect", null, null, null);

        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents", req),
            AgentResponse.class
        );

        // Verify fabric8 was called to create/apply a pod
        verify(k8sClient, atLeastOnce()).pods();
    }

    @Test
    void deleteAgent_stopsPod() {
        var createReq = new CreateAgentRequest("Temp Agent", "claude", "Tester", null, null, null);
        HttpResponse<AgentResponse> created = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents", createReq),
            AgentResponse.class
        );
        UUID agentId = created.getBody().get().id();

        HttpResponse<Void> deleteResp = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/projects/" + projectId + "/agents/" + agentId),
            Void.class
        );

        AssertionsForClassTypes.assertThat(deleteResp.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void stopAgent_gracefulShutdown() {
        var createReq = new CreateAgentRequest("Running Agent", "claude", "Reviewer", null, null, null);
        HttpResponse<AgentResponse> created = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents", createReq),
            AgentResponse.class
        );
        UUID agentId = created.getBody().get().id();

        HttpResponse<AgentResponse> stopped = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents/" + agentId + "/stop", ""),
            AgentResponse.class
        );

        AssertionsForClassTypes.assertThat(stopped.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(stopped.getBody().get().status()).isIn("stopped", "terminating");
    }
}
