package br.unicamp.ft.si600.eventos.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * US-10: registro de um e-mail de prazo já enviado. A chave única impede reenviar o mesmo marco.
 * {@code deadlineDate} faz parte da chave: se o Admin alterar o prazo do projeto (US-03), os marcos
 * da nova data voltam a valer sem precisar apagar o histórico.
 */
@Entity
@Table(name = "deadline_notifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_deadline_notifications_milestone",
                columnNames = {"project_id", "deadline_type", "milestone", "deadline_date", "recipient"}),
        indexes = @Index(name = "idx_deadline_notifications_project_id", columnList = "project_id"))
public class DeadlineNotification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_type", nullable = false, length = 40)
    private DeadlineType deadlineType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DeadlineMilestone milestone;

    @Column(name = "deadline_date", nullable = false)
    private LocalDate deadlineDate;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    protected DeadlineNotification() {}

    public DeadlineNotification(UUID projectId, DeadlineType deadlineType, DeadlineMilestone milestone,
                                LocalDate deadlineDate, String recipient, OffsetDateTime sentAt) {
        this.projectId = projectId;
        this.deadlineType = deadlineType;
        this.milestone = milestone;
        this.deadlineDate = deadlineDate;
        this.recipient = recipient;
        this.sentAt = sentAt;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public DeadlineType getDeadlineType() { return deadlineType; }
    public DeadlineMilestone getMilestone() { return milestone; }
    public LocalDate getDeadlineDate() { return deadlineDate; }
    public String getRecipient() { return recipient; }
    public OffsetDateTime getSentAt() { return sentAt; }
}
