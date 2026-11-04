package br.unicamp.ft.si600.eventos.config;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import br.unicamp.ft.si600.eventos.gateway.RestMercadoPagoGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class MercadoPagoConfiguration {
    @Bean
    @ConditionalOnProperty(name = "mercadopago.enabled", havingValue = "true")
    MercadoPagoGateway mercadoPagoGateway(MercadoPagoProperties properties, ObjectMapper mapper, Validator validator) {
        return new RestMercadoPagoGateway(properties, mapper, validator);
    }
}
