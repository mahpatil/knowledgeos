package com.knowledgeos.controller;

import com.knowledgeos.dto.TimelineEventResponse;
import com.knowledgeos.dto.TimelinePage;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;

import java.util.UUID;

@Controller("/api/v1/projects/{id}/timeline")
@Tag(name = "timeline")
public class TimelineController {

    @Get
    @Operation(summary = "List timeline events (cursor-paginated)")
    public HttpResponse<TimelinePage> list(
            UUID id,
            @Nullable @QueryValue String cursor,
            @Nullable @QueryValue Integer limit,
            @Nullable @QueryValue String type,
            @Nullable @QueryValue UUID agentId) {
        return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED);
    }

    @Get("/{eid}")
    @Operation(summary = "Get a specific timeline event")
    public HttpResponse<TimelineEventResponse> get(UUID id, UUID eid) {
        return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED);
    }

    @Post("/{eid}/replay")
    @Operation(summary = "Replay a reversible timeline event")
    public HttpResponse<TimelineEventResponse> replay(UUID id, UUID eid) {
        return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED);
    }
}
