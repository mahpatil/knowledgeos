package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Serdeable
@Schema(description = "Memory entry details")
public record MemoryResponse(
    UUID id,
    String title,
    String content,
    String justification,
    String layer,
    @Nullable String scopeKey,
    @Nullable List<String> tags,
    @Nullable Double score,
    OffsetDateTime createdAt,
    @Nullable OffsetDateTime expiresAt
) {}
