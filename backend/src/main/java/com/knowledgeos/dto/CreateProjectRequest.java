package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Serdeable
@Schema(description = "Request to create a new project")
public record CreateProjectRequest(
    @NotBlank
    @Schema(description = "Project name", example = "E-Commerce Platform")
    String name,

    @NotBlank
    @Pattern(regexp = "software|content|marketing|migration|research",
             message = "type must be one of: software, content, marketing, migration, research")
    @Schema(description = "Project type", allowableValues = {"software", "content", "marketing", "migration", "research"})
    String type,

    @Schema(description = "Optional template hint for workspace initialization")
    String templateHint
) {}
