package br.unicamp.ft.si600.eventos.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** US-10: dispara a verificação de prazos uma vez por dia (padrão 8h, no fuso do evento). */
@Component
@ConditionalOnProperty(name = "notification.deadlines.enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineNotificationScheduler {
    private final DeadlineNotificationService service;

    public DeadlineNotificationScheduler(DeadlineNotificationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${notification.deadlines.cron:0 0 8 * * *}",
            zone = "America/Sao_Paulo")
    public void runDaily() {
        service.run();
    }
}
