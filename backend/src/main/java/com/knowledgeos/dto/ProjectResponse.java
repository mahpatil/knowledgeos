package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Serdeable
@Schema(description = "Project details")
public record ProjectResponse(
    UUID id,
    String name,
    String type,
    String namespace,
    String status,
    OffsetDateTime createdAt
) {}
