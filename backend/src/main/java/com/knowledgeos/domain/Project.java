package com.knowledgeos.domain;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Serdeable
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, unique = true)
    private String namespace;

    @Column(nullable = false)
    private String status = "active";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_set", nullable = false, columnDefinition = "jsonb")
    private String policySet = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    public Project() {}

    public Project(String name, String type, String namespace) {
        this.name = name;
        this.type = type;
        this.namespace = namespace;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getNamespace() { return namespace; }
    public String getStatus() { return status; }
    public String getPolicySet() { return policySet; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    // Setters
    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public void setStatus(String status) { this.status = status; }
    public void setPolicySet(String policySet) { this.policySet = policySet; }
    public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
    public void setUpdatedAt(OffsetDateTime t) { this.updatedAt = t; }
}
