package com.knowledgeos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Serdeable
@Schema(description = "Error response")
public record ErrorResponse(
    String message,
    @Nullable String code,
    @Nullable JsonNode details
) {}
