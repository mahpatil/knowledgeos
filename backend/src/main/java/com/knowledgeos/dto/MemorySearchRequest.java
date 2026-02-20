package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
@Schema(description = "Request to search memory via semantic similarity")
public record MemorySearchRequest(
    @NotBlank
    @Schema(description = "Search query text")
    String query,

    @Nullable
    @Schema(description = "Filter by layer", allowableValues = {"canonical", "feature", "scratch"})
    String layer,

    @Positive
    @Max(50)
    @Schema(description = "Maximum results to return", defaultValue = "10")
    int limit
) {}
