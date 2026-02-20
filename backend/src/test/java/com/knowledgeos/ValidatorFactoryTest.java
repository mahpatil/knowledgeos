package com.knowledgeos;

import com.knowledgeos.domain.Project;
import com.knowledgeos.service.validator.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@MicronautTest
class ValidatorFactoryTest {

    @Inject ValidatorFactory factory;
    @Inject SoftwareValidator softwareValidator;
    @Inject ContentValidator contentValidator;
    @Inject MigrationValidator migrationValidator;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void forProject_softwareType_returnsSoftwareValidator() {
        Project p = new Project("Test", "software", "ns-sw");
        assertThat(factory.forProject(p)).isInstanceOf(SoftwareValidator.class);
    }

    @Test
    void forProject_contentType_returnsContentValidator() {
        Project p = new Project("Test", "content", "ns-ct");
        assertThat(factory.forProject(p)).isInstanceOf(ContentValidator.class);
    }

    @Test
    void forProject_migrationOrResearch_returnsMigrationValidator() {
        Project p1 = new Project("Test", "migration", "ns-mg");
        Project p2 = new Project("Test", "research",  "ns-rs");
        assertThat(factory.forProject(p1)).isInstanceOf(MigrationValidator.class);
        assertThat(factory.forProject(p2)).isInstanceOf(MigrationValidator.class);
    }

    @Test
    void forProject_unknownType_throwsIllegalArgument() {
        Project p = new Project("Test", "unknown", "ns-uk");
        assertThatThrownBy(() -> factory.forProject(p))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown project type");
    }
}
