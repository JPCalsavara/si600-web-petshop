package br.unicamp.ft.si600.eventos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** US-10: habilita o agendamento e fornece o relógio usado pelo job (substituível nos testes). */
@Configuration
@EnableScheduling
public class DeadlineSchedulingConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }
}
