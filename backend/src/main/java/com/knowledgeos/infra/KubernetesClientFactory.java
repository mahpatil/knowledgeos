package com.knowledgeos.infra;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the fabric8 KubernetesClient as a Micronaut bean.
 * Uses the default kubeconfig / in-cluster config (auto-detected).
 *
 * In tests, this bean is replaced by @MockBean(KubernetesClient.class).
 */
@Factory
public class KubernetesClientFactory {

    private static final Logger log = LoggerFactory.getLogger(KubernetesClientFactory.class);

    @Singleton
    @Requires(missingBeans = KubernetesClient.class)
    public KubernetesClient kubernetesClient() {
        try {
            KubernetesClient client = new KubernetesClientBuilder().build();
            log.info("KubernetesClient initialized: {}", client.getMasterUrl());
            return client;
        } catch (Exception e) {
            log.warn("Failed to initialize KubernetesClient (will use no-op stub): {}", e.getMessage());
            // Return a no-op client so the app starts without a cluster in dev mode
            return new KubernetesClientBuilder()
                .withConfig(new io.fabric8.kubernetes.client.ConfigBuilder()
                    .withMasterUrl("https://localhost:6443")
                    .withTrustCerts(true)
                    .build())
                .build();
        }
    }
}
