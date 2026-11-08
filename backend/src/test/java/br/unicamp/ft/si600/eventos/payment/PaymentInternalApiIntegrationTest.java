package br.unicamp.ft.si600.eventos.payment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentInternalApiIntegrationTest extends PaymentHttpIntegrationSupport {
    @Test void recoveryBindsOnlyVerifiedOrderWithoutAnotherCreation() {
        createStatus = 503;
        var created = post(payload(UUID.randomUUID()), technicalHeaders()).getBody();
        String id = created.path("id").asText();
        var recovered = http.exchange(url("/internal/payments/" + id + "/synchronize"), HttpMethod.POST,
                new HttpEntity<>(JSON.createObjectNode().put("orderId", "ORDTST1"), technicalHeaders()), ObjectNode.class);
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(recovered.getBody().path("orderId").asText()).isEqualTo("ORDTST1");
        assertThat(posts).hasValue(1);
        assertThat(gets).hasValue(1);
    }

    @Test void recoveryWithoutKnownOrderOrCandidateRequiresAssistance() {
        createStatus = 503;
        var created = post(payload(UUID.randomUUID()), technicalHeaders()).getBody();
        var response = http.exchange(url("/internal/payments/" + created.path("id").asText() + "/synchronize"),
                HttpMethod.POST, new HttpEntity<>(technicalHeaders()), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().path("code").asText()).isEqualTo("RECOVERY_REQUIRED");
        assertThat(gets).hasValue(0);
    }

    @Test void malformedRecoveryCandidateDoesNotCallProvider() {
        createStatus = 503;
        var created = post(payload(UUID.randomUUID()), technicalHeaders()).getBody();
        var response = http.exchange(url("/internal/payments/" + created.path("id").asText() + "/synchronize"),
                HttpMethod.POST, new HttpEntity<>(JSON.createObjectNode().put("orderId", "../orders"), technicalHeaders()), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(gets).hasValue(0);
    }

    @Test void recoveryRejectsMismatchedProviderAmountWithoutAssociation() {
        createStatus = 503;
        var created = post(payload(UUID.randomUUID()), technicalHeaders()).getBody();
        orders.get("ORDTST1").put("total_amount", "999.00");
        var response = http.exchange(url("/internal/payments/" + created.path("id").asText() + "/synchronize"), HttpMethod.POST,
                new HttpEntity<>(JSON.createObjectNode().put("orderId", "ORDTST1"), technicalHeaders()), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(service.getPayment("integration-a", UUID.fromString(created.path("id").asText())).orderId()).isNull();
        assertThat(posts).hasValue(1);
    }

    ObjectNode payload(UUID requestId) {
        var request = command(requestId, "obligation-1");
        var json = JSON.valueToTree(request);
        ((ObjectNode) json).remove("integrationId");
        ((ObjectNode) json).remove("currency");
        ((ObjectNode) json).remove("obligationReference");
        ((ObjectNode) json).put("chargeReference", "obligation-1");
        return (ObjectNode) json;
    }

    @Test void authenticatedBackendGeneratesBoleto() {
        var response = http.exchange(url("/internal/payments/boleto"), HttpMethod.POST,
                new HttpEntity<>(payload(UUID.randomUUID()), technicalHeaders()), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("status").asText()).isEqualTo("GERADO");
        assertThat(posts).hasValue(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    ResponseEntity<ObjectNode> post(ObjectNode payload, HttpHeaders headers) {
        return http.exchange(url("/internal/payments/boleto"), HttpMethod.POST, new HttpEntity<>(payload, headers), ObjectNode.class);
    }
    ResponseEntity<ObjectNode> get(String path, HttpHeaders headers) {
        return http.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), ObjectNode.class);
    }
    @Test void missingCredentialRejectsSpoofedLegacyActor() {
        var headers = new HttpHeaders();
        headers.set("X-Authenticated-User-Id", "admin"); headers.set("X-Authenticated-User-Role", "ADMIN");
        assertThat(post(payload(UUID.randomUUID()), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(repository.count()).isZero(); assertThat(posts).hasValue(0);
    }
    @Test void invalidCredentialIsRejected() {
        var headers = technicalHeaders(); headers.setBearerAuth("incorrect-private-token");
        assertThat(post(payload(UUID.randomUUID()), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(repository.count()).isZero();
    }
    @Test void queryStringCannotAuthenticate() {
        var response = http.exchange(url("/internal/payments/boleto?token=fixture-only"), HttpMethod.POST,
                new HttpEntity<>(payload(UUID.randomUUID()), new HttpHeaders()), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }
    @Test void browserOriginAndPreflightAreForbidden() {
        var headers = technicalHeaders(); headers.setOrigin("http://localhost:5173");
        assertThat(post(payload(UUID.randomUUID()), headers).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        headers.set("Access-Control-Request-Method", "POST");
        var response = http.exchange(url("/internal/payments/boleto"), HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isNull();
        assertThat(posts).hasValue(0);
    }
    @Test void integrationCannotBeSelectedByBodyOrHeaders() {
        var body = payload(UUID.randomUUID()).put("integrationId", "integration-b");
        var headers = technicalHeaders(); headers.set("X-Integration-Id", "integration-b");
        var response = post(body, headers);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("integrationId").asText()).isEqualTo("integration-a");
    }
    @Test void obligationMustHaveExplicitServerDelegation() {
        var body = payload(UUID.randomUUID()).put("chargeReference", "untrusted-reference");
        assertThat(post(body, technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(repository.count()).isZero(); assertThat(posts).hasValue(0);
    }
    @Test void repeatedRequestReturnsLocalPaymentWithoutAnotherPost() {
        var body = payload(UUID.randomUUID());
        var created = post(body, technicalHeaders());
        var repeated = post(body, technicalHeaders());
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeated.getBody().path("id")).isEqualTo(created.getBody().path("id"));
        assertThat(posts).hasValue(1); assertThat(gets).hasValue(0); assertThat(repository.count()).isEqualTo(1);
    }
    @Test void localReadAndExplicitBoletoGetHaveDistinctEffects() {
        var created = post(payload(UUID.randomUUID()), technicalHeaders()).getBody();
        String id = created.path("id").asText();
        var local = get("/internal/payments/" + id, technicalHeaders());
        assertThat(local.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(local.getBody().path("status").asText()).isEqualTo("GERADO"); assertThat(gets).hasValue(0);
        var boleto = get("/internal/payments/" + id + "/boleto", technicalHeaders());
        assertThat(boleto.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(boleto.getBody().path("ticketUrl").asText()).isNotBlank();
        assertThat(gets).hasValue(1); assertThat(posts).hasValue(1);
        assertThat(boleto.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
    @Test void nonexistentPaymentReturnsProblemDetail() {
        var response = get("/internal/payments/" + UUID.randomUUID(), technicalHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(gets).hasValue(0);
    }
    @Test void anotherIntegrationIsNotReadable() {
        var command = command(UUID.randomUUID(), "obligation-1");
        var other = service.generateBoleto(new br.unicamp.ft.si600.eventos.dto.payment.GenerateBoletoCommand("integration-b",
                command.obligationReference(), command.requestId(), command.amount(), "BRL", command.description(), 7, command.payer()));
        assertThat(get("/internal/payments/" + other.id(), technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gets).hasValue(0);
    }
    @Test void nonDelegatedObligationCannotBeReadOrSynchronized() {
        var other = service.generateBoleto(command(UUID.randomUUID(), "outside-scope"));
        assertThat(get("/internal/payments/" + other.id(), technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/internal/payments/" + other.id() + "/boleto", technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(http.exchange(url("/internal/payments/" + other.id() + "/synchronize"), HttpMethod.POST,
                new HttpEntity<>(technicalHeaders()), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(gets).hasValue(0);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"requestId", "chargeReference", "amount", "payer", "description"})
    void requiredFieldsAreValidatedWithoutPersistence(String field) {
        var body = payload(UUID.randomUUID()); body.remove(field);
        var response = post(body, technicalHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("invalidFields").has(field)).isTrue();
        assertThat(repository.count()).isZero(); assertThat(posts).hasValue(0);
    }
    @Test void malformedTypesAndUuidRemain400() {
        var body = payload(UUID.randomUUID()).put("amount", "invalid");
        assertThat(post(body, technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/internal/payments/invalid-uuid", technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }
    @Test void changedRequestOrAlreadyReservedObligationConflicts() {
        var body = payload(UUID.randomUUID()); post(body, technicalHeaders());
        assertThat(post(body.deepCopy().put("amount", "51.00"), technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post(payload(UUID.randomUUID()), technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(posts).hasValue(1); assertThat(repository.count()).isEqualTo(1);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={409,500,503})
    void uncertainResultRetainsCanonicalPaymentAndBlocksReissue(int providerStatus) {
        createStatus = providerStatus;
        var body = payload(UUID.randomUUID()); var response = post(body, technicalHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().path("status").asText()).isEqualTo("INCERTO");
        assertThat(response.getHeaders().getFirst("X-Payment-Next-Action")).isEqualTo("QUERY_EXISTING_PAYMENT");
        assertThat(post(body, technicalHeaders()).getBody().path("id")).isEqualTo(response.getBody().path("id"));
        assertThat(post(payload(UUID.randomUUID()), technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get("/internal/payments/"+response.getBody().path("id").asText()+"/boleto", technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(posts).hasValue(1); assertThat(gets).hasValue(0);
    }
    @Test void timeoutDoesNotDuplicateCharge() {
        delayMillis = 2500;
        var body = payload(UUID.randomUUID()); var response = post(body, technicalHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().path("status").asText()).isEqualTo("INCERTO");
        assertThat(post(body, technicalHeaders()).getBody().path("id")).isEqualTo(response.getBody().path("id"));
        assertThat(posts).hasValue(1);
    }
    @Test void conclusiveProviderRejectionUsesProblemAndPreservesReservation() {
        createStatus = 400;
        var body = payload(UUID.randomUUID()); var response = post(body, technicalHeaders());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().path("paymentId").asText()).isNotBlank();
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(post(body, technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(posts).hasValue(1); assertThat(repository.count()).isEqualTo(1);
    }
    @Test void providerGetFailurePreservesKnownState() {
        var created = post(payload(UUID.randomUUID()), technicalHeaders()).getBody();
        getStatus = 503;
        assertThat(get("/internal/payments/"+created.path("id").asText()+"/boleto", technicalHeaders()).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(service.getPayment("integration-a", UUID.fromString(created.path("id").asText())).status().name()).isEqualTo("GERADO");
        assertThat(posts).hasValue(1);
    }

    @Test void unavailablePersistenceReturns503WithoutExternalCall() {
        var created = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        jdbc.execute("alter table payments rename to payments_unavailable_fixture");
        try {
            var response = get("/internal/payments/" + created.id(), technicalHeaders());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().path("code").asText()).isEqualTo("STORAGE_FAILURE");
        } finally { jdbc.execute("alter table payments_unavailable_fixture rename to payments"); }
        assertThat(gets).hasValue(0); assertThat(posts).hasValue(1);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("GERADO");
    }
    @Test void oversizedJsonIsRejectedBeforeProviderOrPersistence() {
        var headers = technicalHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.exchange(url("/internal/payments/boleto"), HttpMethod.POST,
                new HttpEntity<>("{\"large\":\"" + "x".repeat(70_000) + "\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(repository.count()).isZero(); assertThat(posts).hasValue(0);
    }
}
