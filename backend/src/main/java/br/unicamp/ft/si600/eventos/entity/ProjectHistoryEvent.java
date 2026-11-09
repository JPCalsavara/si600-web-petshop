package br.unicamp.ft.si600.eventos.entity;

import br.unicamp.ft.si600.eventos.security.ActorRole;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_history_events", indexes = {
        @Index(name = "idx_project_history_events_project_id", columnList = "project_id, created_at, sequence_order")
})
public class ProjectHistoryEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private ProjectHistoryEventType eventType;

    @Column(name = "actor_id", nullable = false, length = 200, updatable = false)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, length = 30, updatable = false)
    private ActorRole actorRole;

    @Column(name = "description", nullable = false, length = 500, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sequence_order", nullable = false, updatable = false)
    private long sequenceOrder;

    protected ProjectHistoryEvent() {
    }

    public ProjectHistoryEvent(UUID projectId,
                               ProjectHistoryEventType eventType,
                               String actorId,
                               ActorRole actorRole,
                               String description,
                               OffsetDateTime createdAt,
                               long sequenceOrder) {
        this.projectId = projectId;
        this.eventType = eventType;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.description = description;
        this.createdAt = createdAt;
        this.sequenceOrder = sequenceOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public ProjectHistoryEventType getEventType() {
        return eventType;
    }

    public String getActorId() {
        return actorId;
    }

    public ActorRole getActorRole() {
        return actorRole;
    }

    public String getDescription() {
        return description;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public long getSequenceOrder() {
        return sequenceOrder;
    }
}
