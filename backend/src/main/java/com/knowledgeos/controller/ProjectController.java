package com.knowledgeos.controller;

import com.knowledgeos.dto.CreateProjectRequest;
import com.knowledgeos.dto.ProjectResponse;
import com.knowledgeos.dto.UpdateProjectRequest;
import com.knowledgeos.service.ProjectService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Controller("/api/v1/projects")
@Validated
@Tag(name = "projects")
public class ProjectController {

    @Inject
    ProjectService projectService;

    @Post
    @Operation(summary = "Create a new project")
    public HttpResponse<ProjectResponse> create(@Valid @Body CreateProjectRequest req) {
        ProjectResponse response = projectService.create(req);
        return HttpResponse.created(response);
    }

    @Get
    @Operation(summary = "List all projects")
    public HttpResponse<List<ProjectResponse>> list() {
        return HttpResponse.ok(projectService.listAll());
    }

    @Get("/{id}")
    @Operation(summary = "Get project by ID")
    public HttpResponse<ProjectResponse> get(UUID id) {
        return HttpResponse.ok(projectService.getById(id));
    }

    @Put("/{id}")
    @Operation(summary = "Update project")
    public HttpResponse<ProjectResponse> update(UUID id, @Valid @Body UpdateProjectRequest req) {
        return HttpResponse.ok(projectService.update(id, req));
    }

    @Delete("/{id}")
    @Operation(summary = "Archive project (soft delete)")
    public HttpResponse<Void> delete(UUID id) {
        projectService.archive(id);
        return HttpResponse.noContent();
    }
}
