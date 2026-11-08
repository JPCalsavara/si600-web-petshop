package br.unicamp.ft.si600.eventos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@ConfigurationProperties("payment.internal")
public record PaymentInternalProperties(boolean enabled, String token, String integrationId, Set<String> allowedObligations) {
    public PaymentInternalProperties {
        allowedObligations = allowedObligations == null ? Set.of() : Set.copyOf(allowedObligations);
    }
    public boolean configured() {
        return token != null && token.getBytes(StandardCharsets.UTF_8).length >= 32 && token.length() <= 512
                && !token.startsWith("COLOCAR_") && token.chars().noneMatch(Character::isWhitespace)
                && integrationId != null && !integrationId.isBlank() && integrationId.length() <= 100
                && !allowedObligations.isEmpty()
                && allowedObligations.stream().allMatch(value -> !value.isBlank() && value.length() <= 160);
    }
    @Override public String toString() { return "PaymentInternalProperties[redacted]"; }
}
