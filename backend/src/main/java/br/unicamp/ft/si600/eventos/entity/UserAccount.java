package br.unicamp.ft.si600.eventos.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.unicamp.ft.si600.eventos.security.ActorRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** US-01: conta de acesso (login por e-mail e senha). O e-mail do Cliente é o mesmo do Projeto. */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActorRole role;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UserAccount() {}

    public UserAccount(String email, String passwordHash, ActorRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = OffsetDateTime.now();
    }

    /** US-14: troca de senha (pelo próprio usuário ou por redefinição do Admin). Recebe sempre o hash. */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public ActorRole getRole() { return role; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
