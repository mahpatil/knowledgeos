package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Serdeable
@Schema(description = "Request to create a workspace (provisions PVC + directory tree)")
public record CreateWorkspaceRequest(
    @NotBlank
    @Schema(description = "Workspace name")
    String name,

    @NotBlank
    @Pattern(regexp = "code|content|legacy|target|mapping",
             message = "type must be one of: code, content, legacy, target, mapping")
    @Schema(description = "Workspace type", allowableValues = {"code", "content", "legacy", "target", "mapping"})
    String type,

    @Nullable
    @Pattern(regexp = "read-write|read-only",
             message = "mode must be read-write or read-only")
    @Schema(description = "Access mode", allowableValues = {"read-write", "read-only"}, defaultValue = "read-write")
    String mode
) {}
