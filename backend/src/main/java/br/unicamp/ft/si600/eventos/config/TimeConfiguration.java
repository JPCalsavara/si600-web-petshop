package br.unicamp.ft.si600.eventos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfiguration {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.system(DEFAULT_ZONE);
    }
}
