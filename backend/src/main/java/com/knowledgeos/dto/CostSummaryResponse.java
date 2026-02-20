package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Serdeable
@Schema(description = "Cost summary for a project")
public record CostSummaryResponse(
    UUID projectId,
    BigDecimal totalUsd,
    List<AgentCost> perAgent
) {
    @Serdeable
    public record AgentCost(
        UUID agentId,
        String agentName,
        String model,
        long inputTokens,
        long outputTokens,
        BigDecimal costUsd
    ) {}
}
