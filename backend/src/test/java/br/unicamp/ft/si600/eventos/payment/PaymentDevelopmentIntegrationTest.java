package br.unicamp.ft.si600.eventos.payment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("dev")
@TestPropertySource(properties = {"payment.development.enabled=true", "payment.development.username=developer",
        "payment.development.password=local-test-password-only", "payment.internal.enabled=false"})
class PaymentDevelopmentIntegrationTest extends PaymentHttpIntegrationSupport {
    HttpHeaders credentials() {
        var headers = new HttpHeaders();
        headers.setBasicAuth("developer", "local-test-password-only");
        headers.setOrigin("http://localhost:5173");
        return headers;
    }
    ObjectNode payload(UUID requestId) {
        var body = (ObjectNode) JSON.valueToTree(command(requestId, "dev-charge"));
        body.remove("integrationId"); body.remove("currency"); body.remove("obligationReference");
        body.put("chargeReference", "dev-charge");
        ((ObjectNode) body.path("payer")).put("email", "test_user_br@testuser.com");
        return body;
    }
    ResponseEntity<ObjectNode> create(ObjectNode body, HttpHeaders headers) {
        return http.exchange(url("/internal/payments/boleto"), HttpMethod.POST, new HttpEntity<>(body, headers), ObjectNode.class);
    }
    @Test void consecutiveDevelopmentActionsHaveDistinctIdsKeysAndPreservedHistory() {
        var firstRequest = payload(UUID.randomUUID()); var secondRequest = payload(UUID.randomUUID());
        var first = create(firstRequest, credentials()); var second = create(secondRequest, credentials());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK); assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().path("id")).isNotEqualTo(second.getBody().path("id"));
        assertThat(first.getBody().path("orderId")).isNotEqualTo(second.getBody().path("orderId"));
        var one = repository.findById(UUID.fromString(first.getBody().path("id").asText())).orElseThrow();
        var two = repository.findById(UUID.fromString(second.getBody().path("id").asText())).orElseThrow();
        assertThat(one.getIdempotencyKey()).isNotEqualTo(two.getIdempotencyKey());
        assertThat(one.getObligationReference()).isNotEqualTo(two.getObligationReference());
        assertThat(create(firstRequest, credentials()).getBody().path("id")).isEqualTo(first.getBody().path("id"));
        assertThat(create(secondRequest, credentials()).getBody().path("id")).isEqualTo(second.getBody().path("id"));
        assertThat(posts).hasValue(2); assertThat(repository.count()).isEqualTo(2);
        var history = http.exchange(url("/internal/payments/history/0"), HttpMethod.GET,
                new HttpEntity<>(credentials()), ObjectNode.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody().path("payments").size()).isEqualTo(2);
        assertThat(history.getBody().path("hasNext").asBoolean()).isFalse();
        markPaid(orders.get(first.getBody().path("orderId").asText()));
        var synced = http.exchange(url("/internal/payments/" + one.getId() + "/synchronize"), HttpMethod.POST,
                new HttpEntity<>(credentials()), ObjectNode.class);
        assertThat(synced.getBody().path("status").asText()).isEqualTo("PAGO");
        assertThat(service.getPayment("development", two.getId()).status().name()).isEqualTo("GERADO");
    }
    @Test void rejectionDoesNotBlockAnotherExplicitDevelopmentAction() {
        createStatus = 400; var body = payload(UUID.randomUUID()); var rejected = create(body, credentials());
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejected.getBody().path("status").asText()).isEqualTo("FALHOU");
        assertThat(create(body, credentials()).getBody().path("id")).isEqualTo(rejected.getBody().path("id"));
        createStatus = 201; var next = create(payload(UUID.randomUUID()), credentials());
        assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(next.getBody().path("status").asText()).isEqualTo("GERADO");
        assertThat(posts).hasValue(2); assertThat(repository.count()).isEqualTo(2);
    }
    @Test void timeoutDoesNotAutomaticallyCreateAnotherChargeButNewActionIsAllowed() {
        delayMillis = 2500; var body = payload(UUID.randomUUID()); var timedOut = create(body, credentials());
        assertThat(timedOut.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(create(body, credentials()).getBody().path("id")).isEqualTo(timedOut.getBody().path("id"));
        assertThat(posts).hasValue(1);
        delayMillis = 0; var next = create(payload(UUID.randomUUID()), credentials());
        assertThat(next.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(posts).hasValue(2); assertThat(repository.count()).isEqualTo(2);
    }
    @Test void historyIsLocalPaginatedAndExcludesOtherIntegrations() {
        for (int i = 0; i < 21; i++) {
            var body = payload(UUID.randomUUID()); assertThat(create(body, credentials()).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        service.generateBoleto(command(UUID.randomUUID(), "outside-development"));
        var first = http.exchange(url("/internal/payments/history/0"), HttpMethod.GET, new HttpEntity<>(credentials()), ObjectNode.class);
        var second = http.exchange(url("/internal/payments/history/1"), HttpMethod.GET, new HttpEntity<>(credentials()), ObjectNode.class);
        assertThat(first.getBody().path("payments").size()).isEqualTo(20); assertThat(first.getBody().path("hasNext").asBoolean()).isTrue();
        assertThat(second.getBody().path("payments").size()).isEqualTo(1); assertThat(second.getBody().path("hasNext").asBoolean()).isFalse();
        assertThat(first.getBody().path("payments").findValuesAsText("integrationId")).containsOnly("development");
        assertThat(gets).hasValue(0);
    }
    @Test void developmentHistoryRequiresAuthenticationAndValidPage() {
        assertThat(http.getForEntity(url("/internal/payments/history/0"), ObjectNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var invalid = http.exchange(url("/internal/payments/history/-1"), HttpMethod.GET, new HttpEntity<>(credentials()), ObjectNode.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(posts).hasValue(0);
    }
    @Test void developmentRejectsNonTestPayerWithoutWriting() {
        var body = payload(UUID.randomUUID()); ((ObjectNode) body.path("payer")).put("email", "real@example.com");
        assertThat(create(body, credentials()).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(repository.count()).isZero(); assertThat(posts).hasValue(0);
    }
    @Test void webhookConfigurationIsAuthenticatedAndContainsNoSecret() {
        String path = url("/internal/payments/webhook-status");
        assertThat(http.getForEntity(path, ObjectNode.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var response = http.exchange(path, HttpMethod.GET, new HttpEntity<>(credentials()), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isEqualTo(2);
        assertThat(response.getBody().path("enabled").asBoolean()).isFalse();
        assertThat(response.getBody().path("configured").asBoolean()).isFalse();
    }
    @Test void developerCanCreateConsultAndUpdatePersistedPaymentWithoutAnotherCharge() {
        var body = payload(UUID.randomUUID());
        var created = create(body, credentials());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(create(body, credentials()).getBody().path("id")).isEqualTo(created.getBody().path("id"));
        markPaid(orders.get(created.getBody().path("orderId").asText()));
        var details = http.exchange(url("/internal/payments/" + created.getBody().path("id").asText() + "/boleto"),
                HttpMethod.GET, new HttpEntity<>(credentials()), ObjectNode.class);
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(details.getBody().path("payment").path("status").asText()).isEqualTo("PAGO");
        assertThat(details.getBody().path("ticketUrl").asText()).startsWith("https://");
        assertThat(repository.findById(UUID.fromString(created.getBody().path("id").asText())).orElseThrow().getStatus().name()).isEqualTo("PAGO");
        assertThat(posts).hasValue(1); assertThat(gets).hasValue(1);
    }
    @Test void invalidPasswordAndCrossOriginCannotCreateCharges() {
        var headers = credentials(); headers.setBasicAuth("developer", "incorrect");
        assertThat(create(payload(UUID.randomUUID()), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers = credentials(); headers.setOrigin("https://untrusted.example");
        assertThat(create(payload(UUID.randomUUID()), headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(create(payload(UUID.randomUUID()), new HttpHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(posts).hasValue(0); assertThat(repository.count()).isZero();
    }
    @Test void incompletePayloadDoesNotWriteOrCallProvider() {
        var body = payload(UUID.randomUUID()); body.remove("payer");
        assertThat(create(body, credentials()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(posts).hasValue(0); assertThat(repository.count()).isZero();
    }
    @Test void uncertainCreationPreservesReservationAndAllowsExplicitNewDevelopmentAttempt() {
        createStatus = 503;
        var body = payload(UUID.randomUUID());
        var created = create(body, credentials());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(created.getBody().path("status").asText()).isEqualTo("INCERTO");
        assertThat(create(body, credentials()).getBody().path("id")).isEqualTo(created.getBody().path("id"));
        var next = create(payload(UUID.randomUUID()), credentials());
        assertThat(next.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(next.getBody().path("id")).isNotEqualTo(created.getBody().path("id"));
        assertThat(posts).hasValue(2); assertThat(repository.count()).isEqualTo(2);
    }
}
