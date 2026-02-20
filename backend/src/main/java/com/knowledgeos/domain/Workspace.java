package com.knowledgeos.domain;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
@Serdeable
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String mode = "read-write";

    @Column(nullable = false)
    private String path;

    @Column(name = "pvc_name", nullable = false)
    private String pvcName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_tree", nullable = false, columnDefinition = "jsonb")
    private String fileTree = "[]";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Workspace() {}

    @PreUpdate
    void onUpdate() { this.updatedAt = OffsetDateTime.now(); }

    // Getters
    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getMode() { return mode; }
    public String getPath() { return path; }
    public String getPvcName() { return pvcName; }
    public String getFileTree() { return fileTree; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setProject(Project project) { this.project = project; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setMode(String mode) { this.mode = mode; }
    public void setPath(String path) { this.path = path; }
    public void setPvcName(String pvcName) { this.pvcName = pvcName; }
    public void setFileTree(String fileTree) { this.fileTree = fileTree; }
    public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
