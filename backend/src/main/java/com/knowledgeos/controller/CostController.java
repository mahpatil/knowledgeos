package com.knowledgeos.controller;

import com.knowledgeos.dto.CostSummaryResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Controller("/api/v1/projects/{id}/cost-summary")
@Tag(name = "costs")
public class CostController {

    @Get
    @Operation(summary = "Get cost summary per agent and total")
    public HttpResponse<CostSummaryResponse> getSummary(UUID id) {
        return HttpResponse.status(HttpStatus.NOT_IMPLEMENTED);
    }
}
