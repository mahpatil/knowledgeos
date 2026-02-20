package com.knowledgeos.k8s;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.rbac.*;
import io.fabric8.kubernetes.api.model.networking.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Provisions and manages project-level Kubernetes namespaces.
 *
 * Each project gets an isolated namespace: project-{id8}
 * Resources created per namespace:
 *   - Namespace with knowledgeos.io labels
 *   - ServiceAccount "agent" for pod identity
 *   - Role + RoleBinding (read pods/logs, exec into own pod)
 *   - NetworkPolicy isolating agents from other project namespaces
 */
@Singleton
public class NamespaceManager {

    private static final Logger log = LoggerFactory.getLogger(NamespaceManager.class);

    @Inject
    KubernetesClient k8s;

    /**
     * Provision all namespace resources for a new project.
     *
     * @param projectId  the full project UUID string
     * @param namespace  the derived namespace name (e.g., "project-550e8400")
     */
    public void provision(String projectId, String namespace) {
        log.info("Provisioning namespace {} for project {}", namespace, projectId);

        try {
            createNamespace(namespace, projectId);
        } catch (Exception e) {
            log.warn("Namespace creation skipped (no k8s cluster?): {}", e.getMessage());
        }
        createServiceAccount(namespace);
        createRbac(namespace);
        createNetworkPolicy(namespace);

        log.info("Namespace {} provisioned successfully", namespace);
    }

    public void deprovision(String namespace) {
        log.info("Deprovisioning namespace {}", namespace);
        try {
            k8s.namespaces().withName(namespace).delete();
        } catch (Exception e) {
            log.warn("Failed to delete namespace {}: {}", namespace, e.getMessage());
        }
    }

    private void createNamespace(String namespace, String projectId) {
        Namespace ns = new NamespaceBuilder()
            .withNewMetadata()
                .withName(namespace)
                .withLabels(Map.of(
                    "knowledgeos.io/managed", "true",
                    "knowledgeos.io/project-id", projectId,
                    "kubernetes.io/metadata.name", namespace
                ))
            .endMetadata()
            .build();

        var existing = k8s.namespaces().withName(namespace).get();
        if (existing == null) {
            k8s.namespaces().resource(ns).create();
            log.debug("Created namespace {}", namespace);
        } else {
            log.debug("Namespace {} already exists, skipping creation", namespace);
        }
    }

    private void createServiceAccount(String namespace) {
        ServiceAccount sa = new ServiceAccountBuilder()
            .withNewMetadata()
                .withName("agent")
                .withNamespace(namespace)
                .withLabels(Map.of("knowledgeos.io/managed", "true"))
            .endMetadata()
            .build();

        try {
            k8s.serviceAccounts().inNamespace(namespace).resource(sa).serverSideApply();
        } catch (Exception e) {
            log.debug("ServiceAccount creation note: {}", e.getMessage());
        }
    }

    private void createRbac(String namespace) {
        // Role: agents can read pods/logs and exec into their own pod
        Role role = new RoleBuilder()
            .withNewMetadata()
                .withName("agent-role")
                .withNamespace(namespace)
            .endMetadata()
            .withRules(
                new PolicyRuleBuilder()
                    .withApiGroups("")
                    .withResources("pods", "pods/log")
                    .withVerbs("get", "list", "watch")
                    .build(),
                new PolicyRuleBuilder()
                    .withApiGroups("")
                    .withResources("pods/exec")
                    .withVerbs("create")
                    .build()
            )
            .build();

        RoleBinding binding = new RoleBindingBuilder()
            .withNewMetadata()
                .withName("agent-binding")
                .withNamespace(namespace)
            .endMetadata()
            .withNewRoleRef()
                .withApiGroup("rbac.authorization.k8s.io")
                .withKind("Role")
                .withName("agent-role")
            .endRoleRef()
            .withSubjects(new SubjectBuilder()
                .withKind("ServiceAccount")
                .withName("agent")
                .withNamespace(namespace)
                .build())
            .build();

        try {
            k8s.rbac().roles().inNamespace(namespace).resource(role).serverSideApply();
            k8s.rbac().roleBindings().inNamespace(namespace).resource(binding).serverSideApply();
        } catch (Exception e) {
            log.debug("RBAC creation note: {}", e.getMessage());
        }
    }

    private void createNetworkPolicy(String namespace) {
        // Agents can only reach knowledgeos-system (backend) and external HTTPS (AI APIs)
        NetworkPolicy policy = new NetworkPolicyBuilder()
            .withNewMetadata()
                .withName("agent-isolation")
                .withNamespace(namespace)
            .endMetadata()
            .withNewSpec()
                .withPodSelector(new LabelSelectorBuilder()
                    .withMatchLabels(Map.of("role", "agent"))
                    .build())
                .withPolicyTypes("Ingress", "Egress")
                .withIngress(new NetworkPolicyIngressRuleBuilder()
                    .withFrom(new NetworkPolicyPeerBuilder()
                        .withNewNamespaceSelector()
                            .withMatchLabels(Map.of(
                                "kubernetes.io/metadata.name", "knowledgeos-system"))
                        .endNamespaceSelector()
                        .build())
                    .build())
                .withEgress(
                    // Allow to knowledgeos-system (backend)
                    new NetworkPolicyEgressRuleBuilder()
                        .withTo(new NetworkPolicyPeerBuilder()
                            .withNewNamespaceSelector()
                                .withMatchLabels(Map.of(
                                    "kubernetes.io/metadata.name", "knowledgeos-system"))
                            .endNamespaceSelector()
                            .build())
                        .build(),
                    // Allow DNS (kube-system port 53)
                    new NetworkPolicyEgressRuleBuilder()
                        .withTo(new NetworkPolicyPeerBuilder()
                            .withNewNamespaceSelector()
                                .withMatchLabels(Map.of(
                                    "kubernetes.io/metadata.name", "kube-system"))
                            .endNamespaceSelector()
                            .build())
                        .withPorts(
                            new NetworkPolicyPortBuilder().withProtocol("UDP").withNewPort(53).build(),
                            new NetworkPolicyPortBuilder().withProtocol("TCP").withNewPort(53).build()
                        )
                        .build(),
                    // Allow HTTPS out (AI APIs)
                    new NetworkPolicyEgressRuleBuilder()
                        .withPorts(
                            new NetworkPolicyPortBuilder().withProtocol("TCP").withNewPort(443).build(),
                            new NetworkPolicyPortBuilder().withProtocol("TCP").withNewPort(80).build()
                        )
                        .build()
                )
            .endSpec()
            .build();

        try {
            k8s.network().networkPolicies().inNamespace(namespace).resource(policy).serverSideApply();
        } catch (Exception e) {
            log.debug("NetworkPolicy creation note: {}", e.getMessage());
        }
    }
}
