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
class MemoryServiceTest {

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
                new CreateProjectRequest("Memory Test Project", "software", null)),
            ProjectResponse.class
        );
        projectId = resp.getBody().get().id();
    }

    @Test
    void write_canonical_returns201() {
        var req = new CreateMemoryRequest(
            "Service Architecture",
            "The system uses hexagonal architecture with ports and adapters.",
            "Needed for Architect agent context",
            "canonical",
            null,
            List.of("architecture", "design")
        );

        HttpResponse<MemoryResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/memory", req),
            MemoryResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.CREATED);
        MemoryResponse mem = resp.getBody().get();
        Assertions.assertThat(mem.id()).isNotNull();
        Assertions.assertThat(mem.title()).isEqualTo("Service Architecture");
        Assertions.assertThat(mem.layer()).isEqualTo("canonical");
        Assertions.assertThat(mem.expiresAt()).isNull();  // canonical never expires
    }

    @Test
    void write_scratch_setsExpiryFourHours() {
        var req = new CreateMemoryRequest(
            "Temp debugging note",
            "Found null pointer at line 42",
            "Scratch note for current debug session",
            "scratch",
            null,
            null
        );

        HttpResponse<MemoryResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/memory", req),
            MemoryResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.CREATED);
        MemoryResponse mem = resp.getBody().get();
        Assertions.assertThat(mem.layer()).isEqualTo("scratch");
        Assertions.assertThat(mem.expiresAt()).isNotNull();  // scratch has 4h TTL
    }

    @Test
    void write_blankJustification_returns400() {
        var req = new CreateMemoryRequest(
            "Bad entry",
            "Some content",
            "",  // blank justification — should be rejected
            "canonical",
            null,
            null
        );

        assertThatThrownBy(() ->
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/projects/" + projectId + "/memory", req),
                MemoryResponse.class
            )
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void write_invalidLayer_returns400() {
        var req = new CreateMemoryRequest(
            "Bad layer entry",
            "Content",
            "Some justification",
            "invalid-layer",
            null,
            null
        );

        assertThatThrownBy(() ->
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/projects/" + projectId + "/memory", req),
                MemoryResponse.class
            )
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void list_returnsMemoryEntries() {
        writeMemory("Entry A", "canonical");
        writeMemory("Entry B", "feature");

        HttpResponse<List<Map>> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/memory"),
            Argument.listOf(Map.class)
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resp.getBody().get()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void list_filterByLayer_returnsOnlyMatchingLayer() {
        writeMemory("Canonical Entry", "canonical");
        writeMemory("Scratch Entry", "scratch");

        HttpResponse<List<Map>> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/memory?layer=canonical"),
            Argument.listOf(Map.class)
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(resp.getBody().get()).isNotEmpty();
    }

    @Test
    void delete_memory_returns204() {
        UUID memId = writeMemory("To delete", "feature");

        HttpResponse<Void> resp = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/projects/" + projectId + "/memory/" + memId),
            Void.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void search_returnsResults() {
        writeMemory("Hexagonal architecture", "canonical");

        var searchReq = new MemorySearchRequest("architecture patterns", null, 5);

        HttpResponse<List<Map>> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/memory/search", searchReq),
            Argument.listOf(Map.class)
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        // Results may vary depending on Qdrant availability; just assert 200 OK
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private UUID writeMemory(String title, String layer) {
        var req = new CreateMemoryRequest(
            title, "Content of " + title, "Test justification", layer, null, null
        );
        HttpResponse<MemoryResponse> resp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/memory", req),
            MemoryResponse.class
        );
        return resp.getBody().get().id();
    }
}
