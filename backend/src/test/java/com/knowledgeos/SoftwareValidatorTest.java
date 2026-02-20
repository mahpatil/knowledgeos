package com.knowledgeos;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.ValidatorResultResponse;
import com.knowledgeos.service.validator.ProcessResult;
import com.knowledgeos.service.validator.ProcessRunner;
import com.knowledgeos.service.validator.SoftwareValidator;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MicronautTest
class SoftwareValidatorTest {

    @Inject SoftwareValidator validator;
    @Inject ProcessRunner processRunner;   // resolves to the mock below

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @MockBean(ProcessRunner.class)
    ProcessRunner mockRunner() {
        return mock(ProcessRunner.class);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ChangeSet makeChangeSet() {
        Project project = new Project("Test", "software", "default");
        ChangeSet cs = new ChangeSet();
        cs.setProject(project);
        cs.setIntent("test");
        cs.setDiff("");
        cs.setFilesChanged("[]");
        cs.setStatus("pending");
        return cs;
    }

    private Agent makeAgent(String agentType) {
        Agent a = new Agent();
        a.setAgentType(agentType);
        a.setPodName("test-pod");
        return a;
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void run_testsPass_returnsValidatorResultPassed() {
        when(processRunner.run(any(), any()))
            .thenReturn(new ProcessResult(0, "BUILD SUCCESS\n3 tests, 0 failures"));

        ValidatorResultResponse result = validator.run(makeChangeSet(), makeAgent("local"), "/tmp/workspace");

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void run_testsFail_returnsValidatorResultFailed() {
        when(processRunner.run(any(), any()))
            .thenReturn(new ProcessResult(1, "FooTest > testBar FAILED\nBUILD FAILURE"));

        ValidatorResultResponse result = validator.run(makeChangeSet(), makeAgent("local"), "/tmp/workspace");

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).isNotEmpty();
    }

    @Test
    void run_lintFails_returnsValidatorResultFailed() {
        when(processRunner.run(any(), any()))
            .thenReturn(new ProcessResult(1, "checkstyle ERROR: Foo.java:10 — line too long\nBUILD FAILURE"));

        ValidatorResultResponse result = validator.run(makeChangeSet(), makeAgent("local"), "/tmp/workspace");

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("ERROR") || f.contains("FAIL"));
    }

    @Test
    void run_localAgent_usesShellNotKubectl() {
        AtomicReference<List<String>> capturedCommand = new AtomicReference<>();
        when(processRunner.run(any(), any())).thenAnswer(inv -> {
            capturedCommand.set(inv.getArgument(0));
            return new ProcessResult(0, "BUILD SUCCESS");
        });

        validator.run(makeChangeSet(), makeAgent("local"), "/tmp/workspace");

        assertThat(capturedCommand.get()).isNotNull();
        assertThat(String.join(" ", capturedCommand.get())).doesNotContain("kubectl");
    }

    @Test
    void run_podAgent_usesKubectl() {
        AtomicReference<List<String>> capturedCommand = new AtomicReference<>();
        when(processRunner.run(any(), any())).thenAnswer(inv -> {
            capturedCommand.set(inv.getArgument(0));
            return new ProcessResult(0, "BUILD SUCCESS");
        });

        validator.run(makeChangeSet(), makeAgent("pod"), "/tmp/workspace");

        assertThat(capturedCommand.get()).isNotNull();
        assertThat(capturedCommand.get().get(0)).isEqualTo("kubectl");
    }
}
