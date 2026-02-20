package com.knowledgeos;

import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.ValidatorResultResponse;
import com.knowledgeos.service.validator.MigrationValidator;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class MigrationValidatorTest {

    @Inject MigrationValidator validator;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    private ChangeSet makeChangeSet() {
        Project project = new Project("Migration", "migration", "ns-mg");
        ChangeSet cs = new ChangeSet();
        cs.setProject(project);
        cs.setIntent("migrate service");
        cs.setDiff("");
        cs.setFilesChanged("[]");
        cs.setStatus("pending");
        return cs;
    }

    @Test
    void run_outputsMatch_returns100PercentEquivalence(@TempDir Path workspace) throws IOException {
        Path expected = workspace.resolve("legacy/expected-output");
        Path actual   = workspace.resolve("target/output");
        Files.createDirectories(expected);
        Files.createDirectories(actual);
        Files.writeString(expected.resolve("result.txt"),   "Hello World");
        Files.writeString(actual.resolve("result.txt"),     "Hello World");
        Files.writeString(expected.resolve("summary.txt"),  "Done");
        Files.writeString(actual.resolve("summary.txt"),    "Done");

        ValidatorResultResponse result = validator.run(makeChangeSet(), null, workspace.toString());

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void run_partialMatch_returnsCorrectPercentage(@TempDir Path workspace) throws IOException {
        Path expected = workspace.resolve("legacy/expected-output");
        Path actual   = workspace.resolve("target/output");
        Files.createDirectories(expected);
        Files.createDirectories(actual);

        // File A matches
        Files.writeString(expected.resolve("a.txt"), "Match");
        Files.writeString(actual.resolve("a.txt"),   "Match");

        // File B does not match
        Files.writeString(expected.resolve("b.txt"), "Expected output");
        Files.writeString(actual.resolve("b.txt"),   "Different output");

        ValidatorResultResponse result = validator.run(makeChangeSet(), null, workspace.toString());

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("b.txt") || f.contains("Equivalence"));
    }

    @Test
    void run_missingActualFile_fails(@TempDir Path workspace) throws IOException {
        Path expected = workspace.resolve("legacy/expected-output");
        Path actual   = workspace.resolve("target/output");
        Files.createDirectories(expected);
        Files.createDirectories(actual);
        Files.writeString(expected.resolve("missing.txt"), "Expected");
        // No corresponding file in actual/

        ValidatorResultResponse result = validator.run(makeChangeSet(), null, workspace.toString());

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("missing.txt") || f.contains("Missing"));
    }

    @Test
    void run_missingDirectories_fails(@TempDir Path workspace) {
        // Neither legacy/expected-output nor target/output exist

        ValidatorResultResponse result = validator.run(makeChangeSet(), null, workspace.toString());

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).isNotEmpty();
    }
}
