package com.knowledgeos.k8s;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.Workspace;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages agent Kubernetes Pods.
 *
 * Creates pods from the agent-pod.yaml template, mounting the workspace PVC.
 * Polls pod status until Running or timeout.
 *
 * Critical: PVC mount spec and SA permissions must be correct — every safety
 * mechanism in Phases 2-4 depends on proper workspace isolation.
 */
@Singleton
public class AgentPodManager {

    private static final Logger log = LoggerFactory.getLogger(AgentPodManager.class);
    private static final int READY_POLL_MAX_SECONDS = 120;

    @Inject
    KubernetesClient k8s;

    @Value("${kubernetes.agent-image:knowledgeos/agent-runner:latest}")
    String agentImage;

    @Value("${kubernetes.backend-url:http://knowledgeos-backend.knowledgeos-system.svc.cluster.local:8080}")
    String backendUrl;

    @Value("${kubernetes.workspace-base-path:/workspaces}")
    String workspaceBasePath;

    @Value("${kubernetes.ai-secret-name:ai-api-keys}")
    String aiSecretName;

    /**
     * Spawn an agent pod in the project namespace.
     *
     * @param agent     the Agent entity (must have project/workspace set)
     * @param namespace the project's k8s namespace
     * @return the pod name assigned
     */
    public String spawnPod(Agent agent, String namespace, Workspace workspace) {
        String podName = buildPodName(agent);
        String workspacePath = workspace != null ? workspace.getPath() : workspaceBasePath + "/default";
        String pvcName = workspace != null ? workspace.getPvcName() : null;

        Pod pod = buildPodSpec(agent, namespace, podName, workspacePath, pvcName);

        log.info("Spawning agent pod {} in namespace {}", podName, namespace);
        try {
            k8s.pods().inNamespace(namespace).resource(pod).create();
        } catch (Exception e) {
            log.error("Failed to create pod {}: {}", podName, e.getMessage());
            throw new RuntimeException("Failed to spawn agent pod: " + e.getMessage(), e);
        }

        return podName;
    }

    public void deletePod(String podName, String namespace) {
        log.info("Deleting pod {} in namespace {}", podName, namespace);
        try {
            k8s.pods().inNamespace(namespace).withName(podName).delete();
        } catch (Exception e) {
            log.warn("Failed to delete pod {}: {}", podName, e.getMessage());
        }
    }

    public String getPodPhase(String podName, String namespace) {
        try {
            Pod pod = k8s.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) return "Unknown";
            return pod.getStatus() != null ? pod.getStatus().getPhase() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String buildPodName(Agent agent) {
        String shortId = agent.getId().toString().replace("-", "").substring(0, 8);
        String role = agent.getRole().toLowerCase().substring(0, Math.min(8, agent.getRole().length()));
        return "agent-" + role + "-" + shortId;
    }

    private Pod buildPodSpec(Agent agent, String namespace, String podName,
                              String workspacePath, String pvcName) {
        String projectId = agent.getProject().getId().toString();
        String agentId = agent.getId().toString();

        // Build environment variables
        List<EnvVar> envVars = new ArrayList<>(List.of(
            env("AGENT_ID", agentId),
            env("AGENT_ROLE", agent.getRole()),
            env("MODEL", agent.getModel()),
            env("PROJECT_ID", projectId),
            env("BACKEND_URL", backendUrl),
            env("WORKSPACE_PATH", workspacePath),
            envFromSecret("ANTHROPIC_API_KEY", aiSecretName, "ANTHROPIC_API_KEY", true),
            envFromSecret("OPENAI_API_KEY", aiSecretName, "OPENAI_API_KEY", true)
        ));

        // Build volume mounts
        List<VolumeMount> volumeMounts = new ArrayList<>();
        List<Volume> volumes = new ArrayList<>();

        if (pvcName != null) {
            volumeMounts.add(new VolumeMountBuilder()
                .withName("workspace")
                .withMountPath(workspacePath)
                .withReadOnly(!"read-write".equals(agent.getWorkspace() != null ?
                    agent.getWorkspace().getMode() : "read-write"))
                .build());

            volumes.add(new VolumeBuilder()
                .withName("workspace")
                .withNewPersistentVolumeClaim()
                    .withClaimName(pvcName)
                .endPersistentVolumeClaim()
                .build());
        }

        return new PodBuilder()
            .withNewMetadata()
                .withName(podName)
                .withNamespace(namespace)
                .withLabels(Map.of(
                    "role", "agent",
                    "agentId", agentId,
                    "agentRole", agent.getRole().toLowerCase(),
                    "model", agent.getModel(),
                    "projectId", projectId
                ))
                .withAnnotations(Map.of(
                    "knowledgeos.io/agent-id", agentId,
                    "knowledgeos.io/project-id", projectId
                ))
            .endMetadata()
            .withNewSpec()
                .withServiceAccountName("agent")
                .withRestartPolicy("Never")
                .withNewSecurityContext()
                    .withRunAsNonRoot(true)
                    .withRunAsUser(1000L)
                    .withFsGroup(1000L)
                .endSecurityContext()
                .withContainers(new ContainerBuilder()
                    .withName("agent")
                    .withImage(agentImage)
                    .withImagePullPolicy("IfNotPresent")
                    .withEnv(envVars)
                    .withVolumeMounts(volumeMounts)
                    .withNewResources()
                        .withRequests(Map.of(
                            "cpu", new Quantity("250m"),
                            "memory", new Quantity("512Mi")
                        ))
                        .withLimits(Map.of(
                            "cpu", new Quantity("2000m"),
                            "memory", new Quantity("4Gi")
                        ))
                    .endResources()
                    .build())
                .withVolumes(volumes)
            .endSpec()
            .build();
    }

    private EnvVar env(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private EnvVar envFromSecret(String name, String secretName, String key, boolean optional) {
        return new EnvVarBuilder()
            .withName(name)
            .withNewValueFrom()
                .withNewSecretKeyRef()
                    .withName(secretName)
                    .withKey(key)
                    .withOptional(optional)
                .endSecretKeyRef()
            .endValueFrom()
            .build();
    }
}
