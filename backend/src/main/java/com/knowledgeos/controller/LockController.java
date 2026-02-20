package com.knowledgeos.controller;

import com.knowledgeos.dto.AcquireLockRequest;
import com.knowledgeos.dto.FileLockResponse;
import com.knowledgeos.service.FileLockService;
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

@Controller("/api/v1/projects/{id}/locks")
@Validated
@Tag(name = "locks")
public class LockController {

    @Inject
    FileLockService fileLockService;

    @Post
    @Operation(summary = "Acquire a file lock")
    public HttpResponse<FileLockResponse> acquire(UUID id, @Valid @Body AcquireLockRequest req) {
        return HttpResponse.status(HttpStatus.CREATED).body(fileLockService.acquire(id, req));
    }

    @Get
    @Operation(summary = "List active file locks")
    public HttpResponse<List<FileLockResponse>> list(UUID id) {
        return HttpResponse.ok(fileLockService.listForProject(id));
    }

    @Delete("/{lockId}")
    @Operation(summary = "Release a file lock")
    public HttpResponse<Void> release(UUID id, UUID lockId) {
        fileLockService.release(id, lockId);
        return HttpResponse.noContent();
    }

    @Post("/{lockId}/reclaim")
    @Operation(summary = "Force-reclaim a stale lock")
    public HttpResponse<FileLockResponse> reclaim(UUID id, UUID lockId) {
        return HttpResponse.ok(fileLockService.reclaim(id, lockId));
    }
}
