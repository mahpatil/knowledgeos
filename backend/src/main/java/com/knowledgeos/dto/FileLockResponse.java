package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@Schema(description = "File lock details")
public record FileLockResponse(
    UUID id,
    String filePath,
    String lockType,
    @Nullable UUID lockedBy,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt
) {}
