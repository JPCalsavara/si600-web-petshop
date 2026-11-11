package br.unicamp.ft.si600.eventos.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mercadopago")
public record MercadoPagoProperties(boolean enabled, String baseUrl, String accessToken,
                                    Duration connectTimeout, Duration readTimeout) {
    @Override public String toString() { return "MercadoPagoProperties[redacted]"; }
}
