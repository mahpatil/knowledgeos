package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Serdeable
@Schema(description = "ChangeSet details")
public record ChangeSetResponse(
    UUID id,
    @Nullable UUID agentId,
    String intent,
    List<String> filesChanged,
    String diff,
    String status,
    @Nullable ValidatorResultResponse validatorResults,
    OffsetDateTime createdAt
) {}
