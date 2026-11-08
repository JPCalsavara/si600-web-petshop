package br.unicamp.ft.si600.eventos.payment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentWebhookConfigurationIntegrationTest extends PaymentHttpIntegrationSupport {
    @Test void disabledWebhookDoesNotRequireFrontendLoginOrProcessNotifications() {
        var response = http.postForEntity(url("/webhooks/mercadopago?data.id=ORDTEST"), "{}", ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0);
    }
}

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@TestPropertySource(properties = {"payment.webhook.enabled=true", "payment.webhook.secret="})
class PaymentWebhookUnconfiguredIntegrationTest extends PaymentHttpIntegrationSupport {
    @Test void enabledWithoutSecretFailsClosed(org.springframework.boot.test.system.CapturedOutput output) {
        var response = http.postForEntity(url("/webhooks/mercadopago?data.id=ORDTEST"), "{}", ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(output.getAll()).contains("reason=SECRET_MISSING");
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0);
        var configuration = http.exchange(url("/internal/payments/webhook-status"), HttpMethod.GET,
                new HttpEntity<>(technicalHeaders()), ObjectNode.class);
        assertThat(configuration.getBody().path("enabled").asBoolean()).isTrue();
        assertThat(configuration.getBody().path("configured").asBoolean()).isFalse();
    }
}
