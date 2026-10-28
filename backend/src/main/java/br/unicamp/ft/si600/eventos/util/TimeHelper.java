package br.unicamp.ft.si600.eventos.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Component
public class TimeHelper {

    private final Clock clock;

    public TimeHelper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Clock não pode ser nulo.");
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public boolean isOverdue(OffsetDateTime deadline) {
        if (deadline == null) {
            return false;
        }
        return now().isAfter(deadline);
    }

    public boolean isDueSoon(OffsetDateTime deadline, long daysThreshold) {
        if (deadline == null) {
            return false;
        }
        OffsetDateTime current = now();
        if (current.isAfter(deadline)) {
            return false;
        }
        OffsetDateTime thresholdLimit = current.plusDays(daysThreshold);
        return !deadline.isAfter(thresholdLimit);
    }

    public long daysUntil(OffsetDateTime deadline) {
        if (deadline == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(today(), deadline.toLocalDate());
    }

    public Clock getClock() {
        return clock;
    }
}
