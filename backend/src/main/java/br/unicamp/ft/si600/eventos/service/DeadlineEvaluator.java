package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.DeadlineMilestone;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * US-10: decide em qual marco um prazo está hoje. A comparação é por data no fuso do evento
 * (ProjectService.EVENT_ZONE), não por horário: um prazo "dia 30 às 23h" vence no dia 30 inteiro.
 *
 * <p>O marco é o mais avançado já atingido (3 dias ou menos → D_MENOS_3; 1 dia ou menos → D_MENOS_1...),
 * e não apenas o dia exato. Assim, se a rotina ficar um dia fora do ar, o aviso ainda sai no dia seguinte.
 */
@Component
public class DeadlineEvaluator {
    /** Mesmo fuso usado ao gravar os prazos (ProjectService), para nunca divergirem. */
    public static final ZoneId ZONE = ProjectService.EVENT_ZONE;
    static final int NEAR_DAYS = 3;

    /** Resultado da avaliação: marco atingido, dias restantes (negativo = atrasado) e a data do prazo. */
    public record Evaluation(DeadlineMilestone milestone, long daysRemaining, LocalDate deadlineDate) {}

    private final Clock clock;

    public DeadlineEvaluator(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    /** Vazio quando o prazo é nulo ou ainda está a mais de 3 dias. */
    public Optional<Evaluation> evaluate(OffsetDateTime deadline) {
        if (deadline == null) return Optional.empty();
        LocalDate deadlineDate = deadline.atZoneSameInstant(ZONE).toLocalDate();
        long days = ChronoUnit.DAYS.between(today(), deadlineDate);

        DeadlineMilestone milestone;
        if (days < 0) milestone = DeadlineMilestone.ATRASADO;
        else if (days == 0) milestone = DeadlineMilestone.NO_DIA;
        else if (days <= 1) milestone = DeadlineMilestone.D_MENOS_1;
        else if (days <= NEAR_DAYS) milestone = DeadlineMilestone.D_MENOS_3;
        else return Optional.empty();
        return Optional.of(new Evaluation(milestone, days, deadlineDate));
    }
}
