package com.knowledgeos.controller;

import com.knowledgeos.dto.CreateMemoryRequest;
import com.knowledgeos.dto.MemoryResponse;
import com.knowledgeos.dto.MemorySearchRequest;
import com.knowledgeos.service.MemoryService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@Controller("/api/v1/projects/{id}/memory")
@Validated
@Tag(name = "memory")
public class MemoryController {

    @Inject
    MemoryService memoryService;

    @Post
    @Operation(summary = "Write a memory entry")
    public HttpResponse<MemoryResponse> write(UUID id, @Valid @Body CreateMemoryRequest req) {
        return HttpResponse.status(HttpStatus.CREATED).body(memoryService.write(id, req));
    }

    @Get
    @Operation(summary = "List memory entries")
    public HttpResponse<List<MemoryResponse>> list(UUID id, @Nullable @QueryValue String layer) {
        return HttpResponse.ok(memoryService.list(id, layer));
    }

    @Delete("/{mid}")
    @Operation(summary = "Delete a memory entry")
    public HttpResponse<Void> delete(UUID id, UUID mid) {
        memoryService.delete(id, mid);
        return HttpResponse.noContent();
    }

    @Post("/search")
    @Operation(summary = "Semantic search memory entries")
    public HttpResponse<List<MemoryResponse>> search(UUID id, @Valid @Body MemorySearchRequest req) {
        return HttpResponse.ok(memoryService.search(id, req));
    }
}
