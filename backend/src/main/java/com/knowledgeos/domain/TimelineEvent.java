package com.knowledgeos.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "timeline_events")
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload = "{}";

    @Column(nullable = false)
    private String source = "user";

    @Column(nullable = false)
    private boolean reversible = false;

    @Column(name = "replay_cmd", columnDefinition = "TEXT")
    private String replayCmd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public TimelineEvent() {}

    // Getters
    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public Agent getAgent() { return agent; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public String getSource() { return source; }
    public boolean isReversible() { return reversible; }
    public String getReplayCmd() { return replayCmd; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    // Setters
    public void setProject(Project project) { this.project = project; }
    public void setAgent(Agent agent) { this.agent = agent; }
    public void setType(String type) { this.type = type; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setSource(String source) { this.source = source; }
    public void setReversible(boolean reversible) { this.reversible = reversible; }
    public void setReplayCmd(String replayCmd) { this.replayCmd = replayCmd; }
    public void setCreatedAt(OffsetDateTime t) { this.createdAt = t; }
}
