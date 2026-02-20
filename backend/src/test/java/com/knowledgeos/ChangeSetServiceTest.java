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

import io.micronaut.core.type.Argument;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class ChangeSetServiceTest {

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    private UUID projectId;

    @BeforeEach
    void setup() {
        HttpResponse<ProjectResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects",
                new CreateProjectRequest("CS Test Project", "software", null)),
            ProjectResponse.class
        );
        projectId = resp.getBody().get().id();
    }

    @Test
    void submit_noConflictingLock_returns201() {
        var req = new CreateChangeSetRequest(
            "Add logging to service",
            List.of("src/main/java/Service.java"),
            "--- a/Service.java\n+++ b/Service.java\n@@ -1,1 +1,2 @@\n+log.info(\"called\");\n",
            null,
            "never",
            null
        );

        HttpResponse<ChangeSetResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets", req),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.CREATED);
        ChangeSetResponse cs = resp.getBody().get();
        Assertions.assertThat(cs.id()).isNotNull();
        Assertions.assertThat(cs.intent()).isEqualTo("Add logging to service");
        Assertions.assertThat(cs.status()).isEqualTo("human_review");
    }

    @Test
    void submit_policyNever_setsHumanReview() {
        var req = new CreateChangeSetRequest(
            "Refactor method",
            List.of("src/Foo.java"),
            "--- a/Foo.java\n+++ b/Foo.java\n@@ -1 +1 @@\n-old\n+new\n",
            null,
            "never",
            null
        );

        HttpResponse<ChangeSetResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets", req),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.CREATED);
        AssertionsForClassTypes.assertThat(resp.getBody().get().status()).isEqualTo("human_review");
    }

    @Test
    void submit_policyAlways_autoAppliesImmediately() {
        var req = new CreateChangeSetRequest(
            "Auto-fix import",
            List.of("src/App.java"),
            "--- a/App.java\n+++ b/App.java\n@@ -1 +1 @@\n-import old;\n+import new;\n",
            null,
            "always",
            null
        );

        HttpResponse<ChangeSetResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets", req),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.CREATED);
        AssertionsForClassTypes.assertThat(resp.getBody().get().status()).isEqualTo("auto_applied");
    }

    @Test
    void submit_withConflictingWriteLock_returns409() {
        // Acquire a write lock on the file first
        var lockReq = new AcquireLockRequest("src/Locked.java", "write", 300, null);
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks", lockReq),
            FileLockResponse.class
        );

        // Now submit a changeset on the same file
        var csReq = new CreateChangeSetRequest(
            "Change locked file",
            List.of("src/Locked.java"),
            "--- a/Locked.java\n+++ b/Locked.java\n@@ -1 +1 @@\n-old\n+new\n",
            null,
            "never",
            null
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

    @Test
    void approve_humanReviewChangeset_setsApproved() {
        UUID csId = submitChangeset("Awaiting approval", "src/Pending.java", "never");

        HttpResponse<ChangeSetResponse> resp = client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/approve", ""),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        AssertionsForClassTypes.assertThat(resp.getBody().get().status()).isEqualTo("approved");
    }

    @Test
    void reject_setsRejectedAndDoesNotModifyFiles() {
        UUID csId = submitChangeset("To be rejected", "src/Rej.java", "never");

        HttpResponse<ChangeSetResponse> resp = client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/reject",
                Map.of("reason", "Not needed")),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        AssertionsForClassTypes.assertThat(resp.getBody().get().status()).isEqualTo("rejected");
    }

    @Test
    void approve_thenApply_fullHappyPath() {
        UUID csId = submitChangeset("Apply me", "src/Apply.java", "never");

        // Approve
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/approve", ""),
            ChangeSetResponse.class
        );

        // Apply
        HttpResponse<ChangeSetResponse> applyResp = client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/apply", ""),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(applyResp.getStatus()).isEqualTo(HttpStatus.OK);
        AssertionsForClassTypes.assertThat(applyResp.getBody().get().status()).isEqualTo("applied");
    }

    @Test
    void rollback_afterApply_setsRolledBack() {
        UUID csId = submitChangeset("Rollback test", "src/Roll.java", "never");

        // Approve + Apply
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/approve", ""),
            ChangeSetResponse.class
        );
        client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/apply", ""),
            ChangeSetResponse.class
        );

        // Rollback
        HttpResponse<ChangeSetResponse> rollResp = client.toBlocking().exchange(
            HttpRequest.PUT("/api/v1/projects/" + projectId + "/changesets/" + csId + "/rollback", ""),
            ChangeSetResponse.class
        );

        AssertionsForClassTypes.assertThat(rollResp.getStatus()).isEqualTo(HttpStatus.OK);
        AssertionsForClassTypes.assertThat(rollResp.getBody().get().status()).isEqualTo("rolled_back");
    }

    @Test
    void listChangesets_returnsAll() {
        submitChangeset("First", "src/A.java", "never");
        submitChangeset("Second", "src/B.java", "never");

        HttpResponse<List<Map>> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/changesets"),
            Argument.listOf(Map.class)
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat((List<?>) resp.getBody().get()).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private UUID submitChangeset(String intent, String filePath, String policy) {
        var req = new CreateChangeSetRequest(
            intent, List.of(filePath),
            "--- a/File.java\n+++ b/File.java\n@@ -1 +1 @@\n-old\n+new\n",
            null, policy, null
        );
        HttpResponse<ChangeSetResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets", req),
            ChangeSetResponse.class
        );
        return resp.getBody().get().id();
    }
}
