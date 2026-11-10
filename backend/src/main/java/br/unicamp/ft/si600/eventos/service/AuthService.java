package br.unicamp.ft.si600.eventos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.unicamp.ft.si600.eventos.dto.LoginResponse;
import br.unicamp.ft.si600.eventos.dto.MeResponse;
import br.unicamp.ft.si600.eventos.entity.AuthSession;
import br.unicamp.ft.si600.eventos.entity.UserAccount;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.notification.EmailDeliveryException;
import br.unicamp.ft.si600.eventos.notification.EmailSender;
import br.unicamp.ft.si600.eventos.repository.AuthSessionRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.repository.UserAccountRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.security.PasswordHasher;

/** US-01: login, logout, validação de sessão e criação de contas. */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    // Mesma mensagem para e-mail inexistente e senha errada: não revela quais e-mails têm conta.
    private static final String INVALID_CREDENTIALS = "E-mail ou senha inválidos.";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PROVISIONAL_PASSWORD_LENGTH = 12;

    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final ProjectRepository projects;
    private final PasswordHasher hasher;
    private final EmailSender emailSender;
    private final boolean logProvisionalPassword;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(UserAccountRepository users, AuthSessionRepository sessions, ProjectRepository projects,
                       PasswordHasher hasher, EmailSender emailSender,
                       @Value("${app.auth.log-provisional-password:${LOG_PROVISIONAL_PASSWORD:false}}")
                       boolean logProvisionalPassword) {
        this.users = users;
        this.sessions = sessions;
        this.projects = projects;
        this.hasher = hasher;
        this.emailSender = emailSender;
        this.logProvisionalPassword = logProvisionalPassword;
        this.dummyHash = hasher.hash(randomToken());
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        UserAccount user = users.findByEmail(normalize(email)).orElse(null);
        // Compara sempre, mesmo sem conta, para o tempo de resposta não denunciar e-mails cadastrados.
        boolean passwordOk = hasher.matches(password, user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !passwordOk) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
        }
        // A limpeza de sessões expiradas NÃO é feita aqui: ela roda no SessionCleanupScheduler, fora do login,
        // para que logins simultâneos não disputem lock na tabela auth_sessions.
        String token = randomToken();
        sessions.save(new AuthSession(sha256(token), user.getId(), OffsetDateTime.now().plus(SESSION_TTL)));
        return new LoginResponse(token, user.getEmail(), user.getRole().name(),
                projectIdFor(user.getEmail(), user.getRole()));
    }

    /**
     * Remove as sessões expiradas. Chamado periodicamente pelo {@link SessionCleanupScheduler}.
     *
     * @return quantas sessões foram removidas
     */
    @Transactional
    public int purgeExpiredSessions() {
        return sessions.deleteExpired(OffsetDateTime.now());
    }

    @Transactional
    public Optional<Actor> authenticate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String hash = sha256(token.trim());
        Optional<AuthSession> session = sessions.findById(hash);
        if (session.isEmpty()) return Optional.empty();
        if (session.get().getExpiresAt().isBefore(OffsetDateTime.now())) {
            sessions.deleteById(hash);
            return Optional.empty();
        }
        return users.findById(session.get().getUserId()).map(u -> new Actor(u.getEmail(), u.getRole()));
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        sessions.deleteById(sha256(token.trim()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(Actor actor) {
        return new MeResponse(actor.id(), actor.role().name(), projectIdFor(actor.id(), actor.role()));
    }

    /**
     * Cria a conta do Cliente com senha provisória e a envia por e-mail após o commit da transação
     * (se a criação do projeto falhar e der rollback, nenhum e-mail sai).
     *
     * @return a senha provisória, para o Admin repassá-la ao Cliente na tela; vazio se o e-mail já tinha conta
     *         (nesse caso nada é criado nem enviado).
     */
    @Transactional
    public Optional<String> provisionClientAccount(String email) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent()) return Optional.empty();
        String provisional = randomPassword();
        users.save(new UserAccount(normalized, hasher.hash(provisional), ActorRole.CLIENT));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendWelcome(normalized, provisional);
                }
            });
        } else {
            sendWelcome(normalized, provisional);
        }
        return Optional.of(provisional);
    }

    /** Cria o Admin inicial a partir de variáveis de ambiente (ver AdminSeed). */
    @Transactional
    public void ensureAdmin(String email, String password) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isEmpty()) {
            users.save(new UserAccount(normalized, hasher.hash(password), ActorRole.ADMIN));
            log.info("Conta de administrador inicial criada.");
        }
    }

    private void sendWelcome(String email, String provisionalPassword) {
        String body = "Seu acesso ao Sistema de Eventos foi criado.\n\n"
                + "E-mail: " + email + "\n"
                + "Senha provisória: " + provisionalPassword + "\n\n"
                + "Use esses dados para entrar e acompanhar o seu projeto.";
        try {
            emailSender.send(email, "Seu acesso ao Sistema de Eventos", body);
        } catch (EmailDeliveryException ex) {
            // O projeto já foi criado; a falha de e-mail não pode desfazê-lo nem vazar a senha no log.
            log.warn("Falha ao enviar a senha provisória para uma conta de Cliente.");
        }
        if (logProvisionalPassword) {
            log.info("[DEV] Senha provisória de {}: {}", email, provisionalPassword);
        }
    }

    private String projectIdFor(String email, ActorRole role) {
        if (role != ActorRole.CLIENT) return null;
        return projects.findFirstByOwnerId(email).map(p -> p.getId().toString()).orElse(null);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(PROVISIONAL_PASSWORD_LENGTH);
        for (int i = 0; i < PROVISIONAL_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível", ex);
        }
    }
}
