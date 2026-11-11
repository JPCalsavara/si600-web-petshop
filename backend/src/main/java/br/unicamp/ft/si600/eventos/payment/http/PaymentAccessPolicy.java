package br.unicamp.ft.si600.eventos.payment.http;

import br.unicamp.ft.si600.eventos.config.PaymentInternalProperties;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentAccessPolicy {
    private final PaymentInternalProperties properties;
    private final boolean development;
    public PaymentAccessPolicy(PaymentInternalProperties properties, Environment environment) {
        this.properties = properties;
        development = environment.acceptsProfiles(Profiles.of("dev & !prod"));
    }
    public boolean isDevelopment(String integration) { return development && "development".equals(integration); }
    public void authorize(String integration, String obligation) {
        if (isDevelopment(integration) && obligation.startsWith("dev-")) return;
        if (!java.util.Objects.equals(properties.integrationId(), integration)
                || !properties.allowedObligations().contains(obligation)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Obrigação fora do escopo delegado à integração.");
        }
    }
}
