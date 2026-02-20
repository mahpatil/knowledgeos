package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@Schema(description = "Agent details")
public record AgentResponse(
    UUID id,
    String name,
    String model,
    String role,
    String status,
    String podName,
    OffsetDateTime createdAt
) {}
