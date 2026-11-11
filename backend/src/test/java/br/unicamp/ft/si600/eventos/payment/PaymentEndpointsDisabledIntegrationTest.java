package br.unicamp.ft.si600.eventos.payment;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties={"payment.internal.enabled=false", "payment.development.enabled=true", "payment.development.username=developer", "payment.development.password=local-test-password-only"})
class PaymentEndpointsDisabledIntegrationTest extends PaymentHttpIntegrationSupport {
    @Test void developmentCredentialsDoNotEnableEndpointsOutsideDevProfile() {
        var headers = new HttpHeaders(); headers.setBasicAuth("developer", "local-test-password-only");
        var response = http.exchange(url("/internal/payments/boleto"), HttpMethod.POST, new HttpEntity<>("{}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(posts).hasValue(0);
    }
    @Test void flagDisablesPaymentEndpoints() {
        for (String path : new String[]{"/internal/payments/boleto"}) {
            var response = http.exchange(url(path), HttpMethod.POST, new HttpEntity<>("{}",technicalHeaders()), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0);
    }
}
