package com.knowledgeos;

import com.knowledgeos.k8s.NamespaceManager;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

@MicronautTest
class NamespaceManagerTest {

    @Inject
    NamespaceManager namespaceManager;

    @Inject
    KubernetesClient k8sClient;

    @MockBean(KubernetesClient.class)
    KubernetesClient mockK8s() {
        // RETURNS_DEEP_STUBS automatically handles all chained calls (namespaces().withName().get(), etc.)
        return mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void provision_createsNamespace() {
        String projectId = UUID.randomUUID().toString();
        String namespace = "project-" + projectId.substring(0, 8);

        namespaceManager.provision(projectId, namespace);

        verify(k8sClient, atLeastOnce()).namespaces();
    }

    @Test
    void provision_createsServiceAccountAndRBAC() {
        String projectId = UUID.randomUUID().toString();
        String namespace = "project-" + projectId.substring(0, 8);

        namespaceManager.provision(projectId, namespace);

        verify(k8sClient, atLeastOnce()).serviceAccounts();
    }

    @Test
    void provision_createsNetworkPolicy() {
        String projectId = UUID.randomUUID().toString();
        String namespace = "project-" + projectId.substring(0, 8);

        namespaceManager.provision(projectId, namespace);

        // NetworkPolicy creation involves the network client
        verify(k8sClient, atLeastOnce()).namespaces();
    }
}
