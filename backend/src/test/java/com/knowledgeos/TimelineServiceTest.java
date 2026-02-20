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
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class TimelineServiceTest {

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
                new CreateProjectRequest("Timeline Test Project", "software", null)),
            ProjectResponse.class
        );
        projectId = projResp.getBody().get().id();

        HttpResponse<AgentResponse> agentResp = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/agents",
                new CreateAgentRequest("Timeline Agent", "claude", "Implementer",
                    null, null, null, null)),
            AgentResponse.class
        );
        agentId = agentResp.getBody().get().id();
    }

    @Test
    void projectCreate_logsProjectCreatedEvent() {
        // project_created should have been logged during setup
        HttpResponse<TimelinePage> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?type=project_created"),
            TimelinePage.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        TimelinePage page = resp.getBody().get();
        assertThat(page.events()).isNotEmpty();
        assertThat(page.events().get(0).type()).isEqualTo("project_created");
    }

    @Test
    void agentCreate_logsAgentCreatedEvent() {
        HttpResponse<TimelinePage> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?type=agent_created"),
            TimelinePage.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        TimelinePage page = resp.getBody().get();
        assertThat(page.events()).isNotEmpty();
        assertThat(page.events().get(0).type()).isEqualTo("agent_created");
    }

    @Test
    void submitChangeset_logsChangesetSubmittedEvent() {
        var csReq = new CreateChangeSetRequest(
            "Add timeline tests",
            List.of("src/TimelineTest.java"),
            "--- a/TimelineTest.java\n+++ b/TimelineTest.java\n@@ -0,0 +1 @@\n+// test\n",
            null, "never", null
        );

        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets", csReq),
            ChangeSetResponse.class
        );

        HttpResponse<TimelinePage> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?type=changeset_submitted"),
            TimelinePage.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        TimelinePage page = resp.getBody().get();
        assertThat(page.events()).isNotEmpty();
        assertThat(page.events().get(0).type()).isEqualTo("changeset_submitted");
    }

    @Test
    void acquireLock_logsLockAcquiredEvent() {
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/locks",
                new AcquireLockRequest("src/Tracked.java", "write", 300, agentId)),
            FileLockResponse.class
        );

        HttpResponse<TimelinePage> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?type=lock_acquired"),
            TimelinePage.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        TimelinePage page = resp.getBody().get();
        assertThat(page.events()).isNotEmpty();
        TimelineEventResponse event = page.events().get(0);
        assertThat(event.type()).isEqualTo("lock_acquired");
        assertThat(event.payload()).containsKey("filePath");
    }

    @Test
    void writeMemory_logsMemoryWrittenEvent() {
        client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/projects/" + projectId + "/memory",
                new CreateMemoryRequest(
                    "Test Decision",
                    "Use PostgreSQL for persistence",
                    "Decided after evaluating MongoDB and PostgreSQL — PostgreSQL chosen for ACID guarantees",
                    "canonical",
                    null, null
                )),
            MemoryResponse.class
        );

        HttpResponse<TimelinePage> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?type=memory_written"),
            TimelinePage.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        TimelinePage page = resp.getBody().get();
        assertThat(page.events()).isNotEmpty();
        assertThat(page.events().get(0).type()).isEqualTo("memory_written");
    }

    @Test
    void listEvents_noTypeFilter_returnsAllEvents() {
        HttpResponse<TimelinePage> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline"),
            TimelinePage.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        // At minimum project_created + agent_created from setup
        assertThat(resp.getBody().get().events()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void listEvents_cursorPagination_returnsCorrectPage() {
        // Submit 3 changesets to generate events
        for (int i = 0; i < 3; i++) {
            client.toBlocking().exchange(
                HttpRequest.POST("/api/v1/projects/" + projectId + "/changesets",
                    new CreateChangeSetRequest(
                        "Intent " + i,
                        List.of("src/File" + i + ".java"),
                        "--- a/File.java\n+++ b/File.java\n@@ -0,0 +1 @@\n+// " + i + "\n",
                        null, "never", null
                    )),
                ChangeSetResponse.class
            );
        }

        // Page 1 — limit 2
        HttpResponse<TimelinePage> page1Resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?limit=2"),
            TimelinePage.class
        );
        TimelinePage page1 = page1Resp.getBody().get();
        assertThat(page1.events()).hasSize(2);
        assertThat(page1.hasMore()).isTrue();
        assertThat(page1.nextCursor()).isNotNull();

        // Page 2 — use cursor from page 1
        HttpResponse<TimelinePage> page2Resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?limit=2&cursor=" + page1.nextCursor()),
            TimelinePage.class
        );
        TimelinePage page2 = page2Resp.getBody().get();
        assertThat(page2.events()).isNotEmpty();
        // Verify no overlap between pages
        List<UUID> page1Ids = page1.events().stream().map(TimelineEventResponse::id).toList();
        List<UUID> page2Ids = page2.events().stream().map(TimelineEventResponse::id).toList();
        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);
    }

    @Test
    void getEventById_returnsEvent() {
        HttpResponse<TimelinePage> listResp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline?type=project_created"),
            TimelinePage.class
        );
        UUID eventId = listResp.getBody().get().events().get(0).id();

        HttpResponse<TimelineEventResponse> resp = client.toBlocking().exchange(
            HttpRequest.GET("/api/v1/projects/" + projectId + "/timeline/" + eventId),
            TimelineEventResponse.class
        );

        AssertionsForClassTypes.assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get().id()).isEqualTo(eventId);
        assertThat(resp.getBody().get().type()).isEqualTo("project_created");
    }
}
