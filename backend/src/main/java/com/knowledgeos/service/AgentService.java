package com.knowledgeos.service;

import com.knowledgeos.domain.Agent;
import com.knowledgeos.domain.Project;
import com.knowledgeos.domain.Workspace;
import com.knowledgeos.dto.AgentResponse;
import com.knowledgeos.dto.CreateAgentRequest;
import com.knowledgeos.dto.UpdateAgentRequest;
import com.knowledgeos.k8s.AgentPodManager;
import com.knowledgeos.repository.AgentRepository;
import com.knowledgeos.repository.WorkspaceRepository;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    @Inject AgentRepository agentRepository;
    @Inject WorkspaceRepository workspaceRepository;
    @Inject AgentPodManager podManager;
    @Inject TimelineService timelineService;

    @Transactional
    public AgentResponse create(Project project, CreateAgentRequest req) {
        Agent agent = new Agent();
        agent.setProject(project);
        agent.setName(req.name());
        agent.setModel(req.model());
        agent.setRole(req.role());
        agent.setStatus("pending");
        agent.setAgentType(req.agentType() != null ? req.agentType() : "pod");

        if (req.prompt() != null) {
            agent.setPrompt(req.prompt());
        }

        // Associate workspace if provided
        Workspace workspace = null;
        if (req.workspaceId() != null) {
            workspace = workspaceRepository.findById(req.workspaceId())
                .orElse(null);
            if (workspace != null) {
                agent.setWorkspace(workspace);
            }
        }

        agentRepository.save(agent);

        // Spawn pod only for pod-type agents (local agents run on the developer machine)
        final Workspace finalWs = workspace;
        if (!"local".equals(agent.getAgentType())) {
            try {
                String podName = podManager.spawnPod(agent, project.getNamespace(), finalWs);
                agent.setPodName(podName);
                agent.setStatus("running");
                agentRepository.update(agent);
            } catch (Exception e) {
                log.warn("Pod spawn failed for agent {} — status remains pending: {}", agent.getId(), e.getMessage());
            }
        } else {
            // Local agents are immediately "running" (managed externally by Claude Code)
            agent.setStatus("running");
            agentRepository.update(agent);
        }

        timelineService.log(project.getId(), agent.getId(), "agent_created",
            java.util.Map.of("name", agent.getName(), "role", agent.getRole(),
                             "agentType", agent.getAgentType()));

        log.info("Agent created: id={} name={} pod={}", agent.getId(), agent.getName(), agent.getPodName());
        return toResponse(agent);
    }

    public List<AgentResponse> listForProject(UUID projectId) {
        return agentRepository.findByProjectId(projectId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public AgentResponse getById(UUID projectId, UUID agentId) {
        Agent agent = agentRepository.findById(agentId)
            .filter(a -> a.getProject().getId().equals(projectId))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "Agent not found: " + agentId));
        return toResponse(agent);
    }

    @Transactional
    public AgentResponse update(UUID projectId, UUID agentId, UpdateAgentRequest req) {
        Agent agent = getAgentEntity(projectId, agentId);
        if (req.permissions() != null) {
            agent.setPermissions(req.permissions());
        }
        if (req.prompt() != null) {
            agent.setPrompt(req.prompt());
        }
        agentRepository.update(agent);
        return toResponse(agent);
    }

    @Transactional
    public void delete(UUID projectId, UUID agentId) {
        Agent agent = getAgentEntity(projectId, agentId);
        // Stop the pod first
        if (agent.getPodName() != null) {
            try {
                podManager.deletePod(agent.getPodName(), agent.getProject().getNamespace());
            } catch (Exception e) {
                log.warn("Pod deletion failed: {}", e.getMessage());
            }
        }
        agentRepository.delete(agent);
    }

    @Transactional
    public AgentResponse stop(UUID projectId, UUID agentId) {
        Agent agent = getAgentEntity(projectId, agentId);
        if (agent.getPodName() != null) {
            try {
                podManager.deletePod(agent.getPodName(), agent.getProject().getNamespace());
            } catch (Exception e) {
                log.warn("Pod stop failed: {}", e.getMessage());
            }
        }
        agent.setStatus("stopped");
        agentRepository.update(agent);
        timelineService.log(agent.getProject().getId(), agentId, "agent_stopped",
            java.util.Map.of("name", agent.getName()));
        return toResponse(agent);
    }

    @Transactional
    public AgentResponse restart(UUID projectId, UUID agentId) {
        Agent agent = getAgentEntity(projectId, agentId);
        // Delete existing pod
        if (agent.getPodName() != null) {
            try {
                podManager.deletePod(agent.getPodName(), agent.getProject().getNamespace());
            } catch (Exception e) {
                log.warn("Pod delete for restart failed: {}", e.getMessage());
            }
        }
        // Spawn new pod
        agent.setStatus("pending");
        try {
            String podName = podManager.spawnPod(agent, agent.getProject().getNamespace(), agent.getWorkspace());
            agent.setPodName(podName);
            agent.setStatus("running");
        } catch (Exception e) {
            log.warn("Pod restart failed: {}", e.getMessage());
        }
        agentRepository.update(agent);
        timelineService.log(agent.getProject().getId(), agentId, "agent_restarted",
            java.util.Map.of("name", agent.getName()));
        return toResponse(agent);
    }

    private Agent getAgentEntity(UUID projectId, UUID agentId) {
        return agentRepository.findById(agentId)
            .filter(a -> a.getProject().getId().equals(projectId))
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND,
                "Agent not found: " + agentId));
    }

    private AgentResponse toResponse(Agent a) {
        return new AgentResponse(
            a.getId(),
            a.getName(),
            a.getModel(),
            a.getRole(),
            a.getStatus(),
            a.getAgentType(),
            a.getPodName(),
            a.getCreatedAt()
        );
    }
}
