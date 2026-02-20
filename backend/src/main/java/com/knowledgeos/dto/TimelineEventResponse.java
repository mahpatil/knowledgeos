package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Serdeable
@Schema(description = "Timeline event")
public record TimelineEventResponse(
    UUID id,
    String type,
    Map<String, Object> payload,
    boolean reversible,
    @Nullable String replayCmd,
    @Nullable UUID agentId,
    OffsetDateTime createdAt
) {}
