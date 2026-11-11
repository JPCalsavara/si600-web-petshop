package br.unicamp.ft.si600.eventos.payment;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties="payment.internal.token=")
class PaymentInternalUnconfiguredIntegrationTest extends PaymentHttpIntegrationSupport {
    @Test void absentSecretFailsClosedEvenWithLegacyIdentity() {
        var headers = technicalHeaders(); headers.set("X-Authenticated-User-Id", "admin"); headers.set("X-Authenticated-User-Role", "ADMIN");
        var response = http.exchange(url("/internal/payments/00000000-0000-4000-8000-000000000000"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0);
    }
}
