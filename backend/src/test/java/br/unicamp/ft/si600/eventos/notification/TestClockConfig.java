package br.unicamp.ft.si600.eventos.notification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class TestClockConfig {
    /** 10/11/2026 08:00 em America/Sao_Paulo (horário da rotina diária). */
    public static final Instant START = Instant.parse("2026-11-10T11:00:00Z");

    @Bean
    @Primary
    MutableTestClock testClock() {
        return new MutableTestClock(START);
    }
}
