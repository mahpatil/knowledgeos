package com.knowledgeos.controller;

import com.knowledgeos.dto.ChangeSetResponse;
import com.knowledgeos.dto.CreateChangeSetRequest;
import com.knowledgeos.service.ChangeSetService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller("/api/v1/projects/{id}/changesets")
@Validated
@Tag(name = "changesets")
public class ChangeSetController {

    @Inject
    ChangeSetService changeSetService;

    @Post
    @Operation(summary = "Submit a changeset")
    public HttpResponse<ChangeSetResponse> submit(UUID id, @Valid @Body CreateChangeSetRequest req) {
        return HttpResponse.status(HttpStatus.CREATED).body(changeSetService.submit(id, req));
    }

    @Get
    @Operation(summary = "List changesets")
    public HttpResponse<List<ChangeSetResponse>> list(UUID id) {
        return HttpResponse.ok(changeSetService.listForProject(id));
    }

    @Delete("/{csid}")
    @Operation(summary = "Delete a pending changeset")
    public HttpResponse<Void> delete(UUID id, UUID csid) {
        changeSetService.delete(id, csid);
        return HttpResponse.noContent();
    }

    @Put("/{csid}/approve")
    @Operation(summary = "Approve a changeset")
    public HttpResponse<ChangeSetResponse> approve(UUID id, UUID csid) {
        return HttpResponse.ok(changeSetService.approve(id, csid));
    }

    @Put("/{csid}/reject")
    @Operation(summary = "Reject a changeset")
    public HttpResponse<ChangeSetResponse> reject(UUID id, UUID csid, @Body Map<String, String> body) {
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        return HttpResponse.ok(changeSetService.reject(id, csid, reason));
    }

    @Put("/{csid}/apply")
    @Operation(summary = "Apply an approved changeset")
    public HttpResponse<ChangeSetResponse> apply(UUID id, UUID csid) {
        return HttpResponse.ok(changeSetService.apply(id, csid));
    }

    @Put("/{csid}/rollback")
    @Operation(summary = "Roll back an applied changeset")
    public HttpResponse<ChangeSetResponse> rollback(UUID id, UUID csid) {
        return HttpResponse.ok(changeSetService.rollback(id, csid));
    }
}
