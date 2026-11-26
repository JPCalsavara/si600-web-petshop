package br.unicamp.ft.si600.eventos.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * US-14: pedido de recuperação de acesso encaminhado ao painel do Admin.
 * Nunca guarda senha: a senha temporária só aparece uma vez, na resposta ao Admin.
 */
@Entity
@Table(name = "password_reset_requests", indexes = {
        @Index(name = "idx_password_reset_status", columnList = "status, requested_at")
})
public class PasswordResetRequest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordResetStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by", length = 254)
    private String resolvedBy;

    protected PasswordResetRequest() {}

    public PasswordResetRequest(UUID userId, String email, OffsetDateTime requestedAt) {
        this.userId = userId;
        this.email = email;
        this.status = PasswordResetStatus.PENDENTE;
        this.requestedAt = requestedAt;
    }

    public void markResolved(OffsetDateTime at, String adminId) {
        this.status = PasswordResetStatus.ATENDIDO;
        this.resolvedAt = at;
        this.resolvedBy = adminId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public PasswordResetStatus getStatus() { return status; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
}
