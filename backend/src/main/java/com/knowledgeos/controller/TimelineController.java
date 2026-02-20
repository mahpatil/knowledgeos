package com.knowledgeos.controller;

import com.knowledgeos.dto.TimelineEventResponse;
import com.knowledgeos.dto.TimelinePage;
import com.knowledgeos.service.TimelineService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.UUID;

@Controller("/api/v1/projects/{id}/timeline")
@Tag(name = "timeline")
public class TimelineController {

    @Inject TimelineService timelineService;

    @Get
    @Operation(summary = "List timeline events (cursor-paginated, newest first)")
    public HttpResponse<TimelinePage> list(
            UUID id,
            @Nullable @QueryValue String cursor,
            @Nullable @QueryValue Integer limit,
            @Nullable @QueryValue String type) {
        return HttpResponse.ok(timelineService.list(id, cursor, limit, type));
    }

    @Get("/{eid}")
    @Operation(summary = "Get a specific timeline event")
    public HttpResponse<TimelineEventResponse> get(UUID id, UUID eid) {
        return HttpResponse.ok(timelineService.getById(id, eid));
    }
}
