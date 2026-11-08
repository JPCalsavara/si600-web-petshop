package br.unicamp.ft.si600.eventos.payment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("prod")
@TestPropertySource(properties = {"payment.internal.integration-id=development", "payment.internal.allowed-obligations=dev-charge"})
class PaymentProductionCreationIntegrationTest extends PaymentHttpIntegrationSupport {
    ObjectNode payload(UUID requestId) {
        var body = (ObjectNode) JSON.valueToTree(command(requestId, "dev-charge"));
        body.remove("integrationId"); body.remove("currency"); body.remove("obligationReference");
        body.put("chargeReference", "dev-charge");
        return body;
    }
    ResponseEntity<ObjectNode> create(ObjectNode body) {
        return http.exchange(url("/internal/payments/boleto"), HttpMethod.POST, new HttpEntity<>(body, technicalHeaders()), ObjectNode.class);
    }
    @Test void productionKeepsObligationProtectionEvenForIntegrationNamedDevelopment() {
        var request = payload(UUID.randomUUID()); var first = create(request);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(create(request).getBody().path("id")).isEqualTo(first.getBody().path("id"));
        assertThat(create(payload(UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(posts).hasValue(1); assertThat(repository.count()).isEqualTo(1);
    }
    @Test void productionRejectsNewAttemptAfterProviderRejection() {
        createStatus = 400;
        assertThat(create(payload(UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(create(payload(UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(posts).hasValue(1); assertThat(repository.count()).isEqualTo(1);
    }
    @Test void developmentHistoryIsUnavailableInProduction() {
        assertThat(http.exchange(url("/internal/payments/history/0"), HttpMethod.GET,
                new HttpEntity<>(technicalHeaders()), ObjectNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

@ActiveProfiles(value = {"dev", "prod"}, inheritProfiles = false)
class PaymentMixedProfilesCreationIntegrationTest extends PaymentProductionCreationIntegrationTest {}
