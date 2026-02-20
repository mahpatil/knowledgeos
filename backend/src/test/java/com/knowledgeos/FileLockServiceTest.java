package com.knowledgeos;

import com.knowledgeos.dto.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.core.type.Argument;
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

import io.micronaut.core.type.Argument;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class FileLockServiceTest {

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    private UUID projectId;
    private UUID agentId;

    @BeforeEach
    void setup() {
        HttpResponse<ProjectResponse> projResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("Lock Test Project", "software", null)),
            ProjectResponse.class
        );
        projectId = projResp.getBody().get().id();

        HttpResponse<AgentResponse> agentResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents",
                new CreateAgentRequest("Lock Agent", "claude", "Implementer", null, null, null, null)),
            AgentResponse.class
        );
        agentId = agentResp.getBody().get().id();
    }

    @Test
    void acquire_noConflict_returns201() {
        var req = new AcquireLockRequest("src/main/java/Foo.java", "write", 300, agentId);

        HttpResponse<FileLockResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks", req),
            FileLockResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.CREATED);
        FileLockResponse lock = resp.getBody().get();
        Assertions.assertThat(lock.id()).isNotNull();
        Assertions.assertThat(lock.filePath()).isEqualTo("src/main/java/Foo.java");
        Assertions.assertThat(lock.lockType()).isEqualTo("write");
        Assertions.assertThat(lock.expiresAt()).isNotNull();
    }

    @Test
    void acquire_duplicateWriteLock_returns409() {
        var req = new AcquireLockRequest("src/Conflict.java", "write", 300, agentId);

        // First acquire succeeds
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks", req),
            FileLockResponse.class
        );

        // Second acquire on same file conflicts
        assertThatThrownBy(() ->
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/projects/" + projectId + "/locks", req),
                FileLockResponse.class
            )
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT)
        );
    }

    @Test
    void listLocks_returnsActiveLocks() {
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks",
                new AcquireLockRequest("src/A.java", "write", 300, agentId)),
            FileLockResponse.class
        );

        HttpResponse<List<Map>> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/locks"),
            Argument.listOf(Map.class)
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resp.getBody().get()).isNotEmpty();
    }

    @Test
    void release_lock_returns204() {
        // Acquire
        HttpResponse<FileLockResponse> acquired = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks",
                new AcquireLockRequest("src/ToRelease.java", "write", 300, agentId)),
            FileLockResponse.class
        );
        UUID lockId = acquired.getBody().get().id();

        // Release
        HttpResponse<Void> delResp = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/projects/" + projectId + "/locks/" + lockId),
            Void.class
        );

        AssertionsForClassTypes.assertThat(delResp.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void release_thenReacquire_succeeds() {
        var req = new AcquireLockRequest("src/Cycle.java", "write", 300, agentId);

        // Acquire
        HttpResponse<FileLockResponse> first = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks", req),
            FileLockResponse.class
        );
        UUID lockId = first.getBody().get().id();

        // Release
        client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/projects/" + projectId + "/locks/" + lockId),
            Void.class
        );

        // Reacquire succeeds (no conflict)
        HttpResponse<FileLockResponse> second = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks", req),
            FileLockResponse.class
        );
        AssertionsForClassTypes.assertThat(second.getStatus()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void changeset_onLockedFile_returns409() {
        // Lock the file
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks",
                new AcquireLockRequest("src/GuardedFile.java", "write", 300, agentId)),
            FileLockResponse.class
        );

        // Try to submit changeset on same file
        var csReq = new CreateChangeSetRequest(
            "Trying to change locked file",
            List.of("src/GuardedFile.java"),
            "--- a/GuardedFile.java\n+++ b/GuardedFile.java\n@@ -1 +1 @@\n-old\n+new\n",
            null, "never", null
        );

        assertThatThrownBy(() ->
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets", csReq),
                ChangeSetResponse.class
            )
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT)
        );
    }
}
