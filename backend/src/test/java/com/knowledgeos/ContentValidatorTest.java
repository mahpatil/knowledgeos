package com.knowledgeos;

import com.knowledgeos.domain.ChangeSet;
import com.knowledgeos.domain.Project;
import com.knowledgeos.dto.ValidatorResultResponse;
import com.knowledgeos.service.validator.ContentValidator;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class ContentValidatorTest {

    @Inject ContentValidator validator;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ChangeSet makeChangeSet(String diff, String policySet) {
        Project project = new Project("Blog", "content", "ns-blog");
        project.setPolicySet(policySet != null ? policySet : "{}");
        ChangeSet cs = new ChangeSet();
        cs.setProject(project);
        cs.setIntent("publish article");
        cs.setDiff(diff);
        cs.setFilesChanged("[]");
        cs.setStatus("pending");
        return cs;
    }

    /** Build a unified diff with the given text as added lines. */
    private String toDiff(String addedText) {
        StringBuilder sb = new StringBuilder("+++ b/article.md\n");
        for (String line : addedText.split("\n")) {
            sb.append("+").append(line).append("\n");
        }
        return sb.toString();
    }

    /** Build a readable paragraph of the given word count. */
    private String readableText(int wordCount) {
        StringBuilder sb = new StringBuilder();
        String sentence = "This is a simple sentence that anyone can read. ";
        while (sb.toString().trim().split("\\s+").length < wordCount) {
            sb.append(sentence);
        }
        return sb.toString();
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test
    void run_belowMinWordCount_autoRejects() {
        String diff = toDiff("Short text only here.");
        ChangeSet cs = makeChangeSet(diff, null);

        ValidatorResultResponse result = validator.run(cs, null, "/tmp/ws");

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("Word count too low"));
    }

    @Test
    void run_failsReadability_autoRejects() {
        // Long sentences with complex polysyllabic words → low Flesch score
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append("Antidisestablishmentarianism electroencephalographically demonstrates supercalifragilistic. ");
        }
        String diff = toDiff(sb.toString());
        ChangeSet cs = makeChangeSet(diff, null);

        ValidatorResultResponse result = validator.run(cs, null, "/tmp/ws");

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("Readability"));
    }

    @Test
    void run_humanEditorialApprovalPolicy_forcesHumanReview() {
        String diff = toDiff(readableText(150));
        // Project has editorial_approval policy — all checks pass but human review required
        ChangeSet cs = makeChangeSet(diff, "{\"editorial_approval\":true}");

        ValidatorResultResponse result = validator.run(cs, null, "/tmp/ws");

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.requiresHumanReview()).isTrue();
    }

    @Test
    void run_allChecksPass_returnsPassed() {
        String diff = toDiff(readableText(150));
        ChangeSet cs = makeChangeSet(diff, null);

        ValidatorResultResponse result = validator.run(cs, null, "/tmp/ws");

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.requiresHumanReview()).isFalse();
    }
}
