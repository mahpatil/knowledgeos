package com.knowledgeos;

import io.micronaut.runtime.Micronaut;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(
    info = @Info(
        title = "Knowledge OS API",
        version = "1.0.0",
        description = "MCP Platform — project-centric knowledge-work operating system for AI agent collaboration"
    )
)
public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
