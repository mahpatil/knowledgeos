package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Serdeable
@Schema(description = "Request to submit a changeset")
public record CreateChangeSetRequest(
    @NotBlank
    @Schema(description = "Human-readable intent of this change")
    String intent,

    @NotEmpty
    @Schema(description = "List of files modified in this changeset")
    List<String> filesChanged,

    @NotBlank
    @Schema(description = "Unified diff content")
    String diff,

    @Nullable
    @Schema(description = "Tests executed against these changes")
    List<String> testsRun,

    @Nullable
    @Schema(description = "Auto-apply policy", allowableValues = {"always", "on_tests_pass", "never"})
    String autoApplyPolicy,

    @Nullable
    @Schema(description = "Agent submitting this changeset (null for user-submitted)")
    UUID agentId
) {}
