package com.knowledgeos.controller;

import com.knowledgeos.dto.CreateWorkspaceRequest;
import com.knowledgeos.dto.WorkspaceResponse;
import com.knowledgeos.service.ProjectService;
import com.knowledgeos.service.WorkspaceService;
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

@Controller("/api/v1/projects/{id}/workspaces")
@Validated
@Tag(name = "workspaces")
public class WorkspaceController {

    @Inject
    WorkspaceService workspaceService;

    @Inject
    ProjectService projectService;

    @Post
    @Operation(summary = "Create workspace (provisions PVC + directory tree)")
    public HttpResponse<WorkspaceResponse> create(UUID id, @Valid @Body CreateWorkspaceRequest req) {
        var project = projectService.findEntityById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Project not found: " + id));
        WorkspaceResponse ws = workspaceService.create(project, req);
        return HttpResponse.created(ws);
    }

    @Get
    @Operation(summary = "List project workspaces")
    public HttpResponse<List<WorkspaceResponse>> list(UUID id) {
        return HttpResponse.ok(workspaceService.listForProject(id));
    }

    @Get("/{wid}")
    @Operation(summary = "Get workspace by ID")
    public HttpResponse<WorkspaceResponse> get(UUID id, UUID wid) {
        return workspaceService.findById(wid)
            .map(HttpResponse::ok)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Workspace not found: " + wid));
    }
}
