package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Serdeable
@Schema(description = "Request to acquire a file lock")
public record AcquireLockRequest(
    @NotBlank
    @Schema(description = "File path to lock (relative to workspace root)")
    String filePath,

    @Nullable
    @Schema(description = "Lock type", allowableValues = {"read", "write"}, defaultValue = "write")
    String lockType,

    @Positive
    @Max(3600)
    @Schema(description = "Lock duration in seconds", defaultValue = "300", maximum = "3600")
    int durationSeconds,

    @Nullable
    @Schema(description = "Agent acquiring the lock (null for system/user-level lock)")
    UUID agentId
) {}
