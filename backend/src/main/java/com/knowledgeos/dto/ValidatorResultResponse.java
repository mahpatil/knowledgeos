package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Serdeable
@Schema(description = "Validator run result")
public record ValidatorResultResponse(
    boolean passed,
    List<String> failures,
    long durationMs
) {}
