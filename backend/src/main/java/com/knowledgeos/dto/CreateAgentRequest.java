package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Serdeable
@Schema(description = "Request to spawn an agent pod")
public record CreateAgentRequest(
    @NotBlank
    @Schema(description = "Agent display name")
    String name,

    @NotBlank
    @Pattern(regexp = "claude|codex", message = "model must be claude or codex")
    @Schema(description = "AI model", allowableValues = {"claude", "codex"})
    String model,

    @NotBlank
    @Pattern(regexp = "Architect|Implementer|Reviewer|Tester|Debugger|Refactorer|" +
                      "DocumentationWriter|ReverseEngineer|DomainModeler|Translator|Verifier",
             message = "Invalid role")
    @Schema(description = "Agent role")
    String role,

    @Nullable
    @Schema(description = "Permission set JSON: {\"read\": [], \"write\": [], \"execute\": false}")
    String permissions,

    @Nullable
    @Schema(description = "Initial system prompt override")
    String prompt,

    @Nullable
    @Schema(description = "Workspace to mount for this agent")
    java.util.UUID workspaceId,

    @Nullable
    @Pattern(regexp = "pod|local", message = "agentType must be 'pod' or 'local'")
    @Schema(description = "Agent type: 'pod' (Kubernetes) or 'local' (Claude Code on host)", allowableValues = {"pod", "local"})
    String agentType
) {}
