package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;

@Serdeable
@Schema(description = "Request to update agent configuration")
public record UpdateAgentRequest(
    @Nullable String permissions,
    @Nullable String prompt
) {}
