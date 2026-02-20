package com.knowledgeos.dto;

import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Serdeable
@Schema(description = "Workspace details")
public record WorkspaceResponse(
    UUID id,
    String name,
    String type,
    String mode,
    String path,
    String pvcName,
    List<String> fileTree
) {}
