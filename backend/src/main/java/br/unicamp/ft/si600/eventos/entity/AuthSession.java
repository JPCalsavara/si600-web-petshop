package br.unicamp.ft.si600.eventos.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** US-01: sessão de login. Guarda apenas o hash (SHA-256) do token entregue ao navegador. */
@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_sessions_user_id", columnList = "user_id"))
public class AuthSession {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    protected AuthSession() {}

    public AuthSession(String tokenHash, UUID userId, OffsetDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getUserId() { return userId; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
