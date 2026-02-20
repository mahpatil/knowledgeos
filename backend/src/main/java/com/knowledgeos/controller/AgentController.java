package com.knowledgeos.controller;

import com.knowledgeos.dto.AgentResponse;
import com.knowledgeos.dto.CreateAgentRequest;
import com.knowledgeos.dto.UpdateAgentRequest;
import com.knowledgeos.service.AgentService;
import com.knowledgeos.service.ProjectService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Controller("/api/v1/projects/{id}/agents")
@Validated
@Tag(name = "agents")
public class AgentController {

    @Inject
    AgentService agentService;

    @Inject
    ProjectService projectService;

    @Post
    @Operation(summary = "Spawn an agent pod")
    public HttpResponse<AgentResponse> create(UUID id, @Valid @Body CreateAgentRequest req) {
        var project = projectService.findEntityById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
        return HttpResponse.created(agentService.create(project, req));
    }

    @Get
    @Operation(summary = "List agents for project")
    public HttpResponse<List<AgentResponse>> list(UUID id) {
        return HttpResponse.ok(agentService.listForProject(id));
    }

    @Get("/{aid}")
    @Operation(summary = "Get agent by ID")
    public HttpResponse<AgentResponse> get(UUID id, UUID aid) {
        return HttpResponse.ok(agentService.getById(id, aid));
    }

    @Put("/{aid}")
    @Operation(summary = "Update agent configuration")
    public HttpResponse<AgentResponse> update(UUID id, UUID aid, @Valid @Body UpdateAgentRequest req) {
        return HttpResponse.ok(agentService.update(id, aid, req));
    }

    @Delete("/{aid}")
    @Operation(summary = "Delete agent and stop pod")
    public HttpResponse<Void> delete(UUID id, UUID aid) {
        agentService.delete(id, aid);
        return HttpResponse.noContent();
    }

    @Post("/{aid}/stop")
    @Operation(summary = "Gracefully stop agent pod")
    public HttpResponse<AgentResponse> stop(UUID id, UUID aid) {
        return HttpResponse.ok(agentService.stop(id, aid));
    }

    @Post("/{aid}/restart")
    @Operation(summary = "Restart agent pod")
    public HttpResponse<AgentResponse> restart(UUID id, UUID aid) {
        return HttpResponse.ok(agentService.restart(id, aid));
    }
}
