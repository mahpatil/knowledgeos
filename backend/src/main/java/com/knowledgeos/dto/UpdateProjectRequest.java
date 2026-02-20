package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Serdeable
@Schema(description = "Request to update a project")
public record UpdateProjectRequest(
    @Nullable
    @Schema(description = "Updated project name")
    String name,

    @Nullable
    @Schema(description = "Updated status", allowableValues = {"active", "paused", "archived"})
    String status
) {}
