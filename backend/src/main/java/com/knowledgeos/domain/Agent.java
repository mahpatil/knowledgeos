package com.knowledgeos.domain;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agents")
@Serdeable
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "agent_type", nullable = false)
    private String agentType = "pod";

    @Column(name = "pod_name")
    private String podName;

    @Column(columnDefinition = "text")
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String permissions = "{\"read\":[],\"write\":[],\"execute\":false}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workspace_mounts", nullable = false, columnDefinition = "jsonb")
    private String workspaceMounts = "[]";

    @Column(name = "token_count", nullable = false)
    private long tokenCount = 0L;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Agent() {}

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // Getters
    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public Workspace getWorkspace() { return workspace; }
    public String getName() { return name; }
    public String getModel() { return model; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getAgentType() { return agentType; }
    public String getPodName() { return podName; }
    public String getPrompt() { return prompt; }
    public String getPermissions() { return permissions; }
    public String getWorkspaceMounts() { return workspaceMounts; }
    public long getTokenCount() { return tokenCount; }
    public BigDecimal getCostUsd() { return costUsd; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setProject(Project project) { this.project = project; }
    public void setWorkspace(Workspace workspace) { this.workspace = workspace; }
    public void setName(String name) { this.name = name; }
    public void setModel(String model) { this.model = model; }
    public void setRole(String role) { this.role = role; }
    public void setStatus(String status) { this.status = status; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public void setPodName(String podName) { this.podName = podName; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    public void setWorkspaceMounts(String workspaceMounts) { this.workspaceMounts = workspaceMounts; }
    public void setTokenCount(long tokenCount) { this.tokenCount = tokenCount; }
    public void setCostUsd(BigDecimal costUsd) { this.costUsd = costUsd; }
    public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
