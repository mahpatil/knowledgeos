package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.util.List;

@Serdeable
@Schema(description = "Paginated timeline events")
public record TimelinePage(
    List<TimelineEventResponse> events,
    @Nullable String nextCursor,
    boolean hasMore
) {}
