package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

@Serdeable
@Schema(description = "Request to write a memory entry")
public record CreateMemoryRequest(
    @NotBlank
    @Schema(description = "Memory entry title")
    String title,

    @NotBlank
    @Schema(description = "Memory content body")
    String content,

    @NotBlank
    @Schema(description = "Why this memory is being stored — required, not blank")
    String justification,

    @NotBlank
    @Pattern(regexp = "canonical|feature|scratch",
             message = "layer must be canonical, feature, or scratch")
    @Schema(description = "Memory layer", allowableValues = {"canonical", "feature", "scratch"})
    String layer,

    @Nullable
    @Schema(description = "Scope key for grouping (e.g. feature branch, sprint)")
    String scopeKey,

    @Nullable
    @Schema(description = "Tags for retrieval")
    List<String> tags
) {}
