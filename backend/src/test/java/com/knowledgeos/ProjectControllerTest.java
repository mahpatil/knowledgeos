package com.knowledgeos;

import com.knowledgeos.dto.CreateProjectRequest;
import com.knowledgeos.dto.ProjectResponse;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

@MicronautTest
class ProjectControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void createProject_validRequest_returns201AndPersists() {
        var req = new CreateProjectRequest("My Software Project", "software", null);

        HttpResponse<ProjectResponse> response = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects", req),
            ProjectResponse.class
        );

        AssertionsForClassTypes.assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        Assertions.assertThat(response.getBody()).isPresent();
        ProjectResponse body = response.getBody().get();
        Assertions.assertThat(body.id()).isNotNull();
        Assertions.assertThat(body.name()).isEqualTo("My Software Project");
        Assertions.assertThat(body.type()).isEqualTo("software");
        Assertions.assertThat(body.namespace()).startsWith("project-");
        Assertions.assertThat(body.status()).isEqualTo("active");
        Assertions.assertThat(body.createdAt()).isNotNull();
    }

    @Test
    void createProject_invalidType_returns400() {
        var req = new CreateProjectRequest("Test", "invalid-type", null);

        assertThatThrownBy(() ->
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/projects", req), ProjectResponse.class)
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void createProject_blankName_returns400() {
        var req = new CreateProjectRequest("", "software", null);

        assertThatThrownBy(() ->
            client.toBlocking().exchange(HttpRequest.POST("/api/v1/projects", req), ProjectResponse.class)
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void getProject_existing_returns200WithResponse() {
        // First create a project
        var req = new CreateProjectRequest("Fetch Test", "content", null);
        HttpResponse<ProjectResponse> created = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects", req), ProjectResponse.class
        );
        UUID id = created.getBody().get().id();

        // Then fetch it
        HttpResponse<ProjectResponse> fetched = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + id), ProjectResponse.class
        );

        AssertionsForClassTypes.assertThat(fetched.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(fetched.getBody().get().id()).isEqualTo(id);
        Assertions.assertThat(fetched.getBody().get().name()).isEqualTo("Fetch Test");
    }

    @Test
    void getProject_notFound_returns404() {
        UUID randomId = UUID.randomUUID();

        assertThatThrownBy(() ->
            client.toBlocking().exchange(
                HttpRequest.GET("/api/v1/projects/" + randomId), ProjectResponse.class
            )
        ).isInstanceOfSatisfying(HttpClientResponseException.class, ex ->
            AssertionsForClassTypes.assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND)
        );
    }

    @Test
    void listProjects_returnsAll() {
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects", new CreateProjectRequest("P1", "software", null)),
            ProjectResponse.class
        );
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects", new CreateProjectRequest("P2", "content", null)),
            ProjectResponse.class
        );

        HttpResponse<List<Map>> response = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects"), Argument.listOf(Map.class)
        );

        AssertionsForClassTypes.assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        Assertions.assertThat(response.getBody().get()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void deleteProject_archives_doesNotPhysicallyDelete() {
        var req = new CreateProjectRequest("To Archive", "research", null);
        HttpResponse<ProjectResponse> created = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects", req), ProjectResponse.class
        );
        UUID id = created.getBody().get().id();

        // Delete (archive) it
        HttpResponse<Void> deleteResp = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/projects/" + id), Void.class
        );
        AssertionsForClassTypes.assertThat(deleteResp.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);

        // Should still be fetchable but archived
        HttpResponse<ProjectResponse> fetched = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + id), ProjectResponse.class
        );
        Assertions.assertThat(fetched.getBody().get().status()).isEqualTo("archived");
    }
}
