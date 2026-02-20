package com.knowledgeos.domain;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "memory_entries")
public class MemoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String justification;

    @Column(nullable = false, length = 20)
    private String layer;

    @Nullable
    @Column(name = "scope_key", length = 255)
    private String scopeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String tags = "[]";

    @Nullable
    @Column(name = "qdrant_id")
    private UUID qdrantId;

    @Nullable
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    // ── Getters and setters ────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    @Nullable
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(@Nullable String scopeKey) { this.scopeKey = scopeKey; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    @Nullable
    public UUID getQdrantId() { return qdrantId; }
    public void setQdrantId(@Nullable UUID qdrantId) { this.qdrantId = qdrantId; }

    @Nullable
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(@Nullable OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
