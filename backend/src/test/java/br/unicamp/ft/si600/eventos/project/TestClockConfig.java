package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.config.TimeConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;

@TestConfiguration
public class TestClockConfig {

    public static final Instant FIXED_INSTANT = Instant.parse("2026-10-20T12:00:00Z");

    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(FIXED_INSTANT, TimeConfiguration.DEFAULT_ZONE);
    }
}
