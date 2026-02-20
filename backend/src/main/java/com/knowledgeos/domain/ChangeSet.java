package com.knowledgeos.domain;

import com.knowledgeos.dto.ValidatorResultResponse;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "changesets")
public class ChangeSet {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String intent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "files_changed", nullable = false, columnDefinition = "jsonb")
    private String filesChanged = "[]";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diff;

    @Column(name = "reverse_diff", columnDefinition = "TEXT")
    private String reverseDiff;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "auto_apply_policy")
    private String autoApplyPolicy = "never";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tests_run", nullable = false, columnDefinition = "jsonb")
    private String testsRun = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validator_results", columnDefinition = "jsonb")
    private String validatorResultsJson;

    /** Transient DTO — populated by the service layer, not persisted directly. */
    @Transient
    private ValidatorResultResponse validatorResults;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // ── Getters and setters ────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public Agent getAgent() { return agent; }
    public void setAgent(Agent agent) { this.agent = agent; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getFilesChanged() { return filesChanged; }
    public void setFilesChanged(String filesChanged) { this.filesChanged = filesChanged; }

    public String getDiff() { return diff; }
    public void setDiff(String diff) { this.diff = diff; }

    public String getReverseDiff() { return reverseDiff; }
    public void setReverseDiff(String reverseDiff) { this.reverseDiff = reverseDiff; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAutoApplyPolicy() { return autoApplyPolicy; }
    public void setAutoApplyPolicy(String autoApplyPolicy) { this.autoApplyPolicy = autoApplyPolicy; }

    public String getTestsRun() { return testsRun; }
    public void setTestsRun(String testsRun) { this.testsRun = testsRun; }

    public String getValidatorResultsJson() { return validatorResultsJson; }
    public void setValidatorResultsJson(String validatorResultsJson) { this.validatorResultsJson = validatorResultsJson; }

    public ValidatorResultResponse getValidatorResults() { return validatorResults; }
    public void setValidatorResults(ValidatorResultResponse validatorResults) { this.validatorResults = validatorResults; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
