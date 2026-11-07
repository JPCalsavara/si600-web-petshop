package br.unicamp.ft.si600.eventos.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Relógio dos testes de prazo: permite "avançar os dias" entre execuções da rotina. */
public class MutableTestClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableTestClock(Instant start) {
        this(new AtomicReference<>(start), ZoneOffset.UTC);
    }

    private MutableTestClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void set(Instant instant) { now.set(instant); }

    public void advanceDays(long days) { now.updateAndGet(i -> i.plusSeconds(days * 86_400)); }

    @Override public ZoneId getZone() { return zone; }

    @Override public Clock withZone(ZoneId zone) { return new MutableTestClock(now, zone); }

    @Override public Instant instant() { return now.get(); }
}
