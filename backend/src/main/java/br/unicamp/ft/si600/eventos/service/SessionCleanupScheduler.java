package br.unicamp.ft.si600.eventos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * US-01: apaga periodicamente as sessões de login expiradas (padrão: a cada hora).
 * Fica fora do {@code AuthService.login} para não gerar disputa de lock na tabela {@code auth_sessions}.
 * Usa fixedDelay: uma execução só começa depois que a anterior terminou, sem sobreposição.
 * O agendamento é habilitado por {@code DeadlineSchedulingConfiguration} (@EnableScheduling).
 */
@Component
@ConditionalOnProperty(name = "app.auth.session-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final AuthService authService;

    public SessionCleanupScheduler(AuthService authService) {
        this.authService = authService;
    }

    @Scheduled(fixedDelayString = "${app.auth.session-cleanup.interval-ms:3600000}",
            initialDelayString = "${app.auth.session-cleanup.initial-delay-ms:60000}")
    public void purgeExpiredSessions() {
        try {
            int removed = authService.purgeExpiredSessions();
            if (removed > 0) log.info("Sessões expiradas removidas: {}", removed);
        } catch (RuntimeException ex) {
            // Uma falha pontual não pode derrubar o agendador: a próxima rodada tenta de novo.
            log.warn("Falha ao limpar sessões expiradas: {}", ex.getMessage());
        }
    }
}
