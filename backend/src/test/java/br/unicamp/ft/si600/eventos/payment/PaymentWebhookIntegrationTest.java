package br.unicamp.ft.si600.eventos.payment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import java.time.*;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@Import(PaymentWebhookIntegrationTest.FixedTime.class)
@TestPropertySource(properties = {"payment.webhook.enabled=true", "payment.webhook.secret=fixture-webhook-secret-only"})
class PaymentWebhookIntegrationTest extends PaymentHttpIntegrationSupport {
    static final String ORDER = "ORDTST01M6YDSZ3XMX43XBTZZKGQYZM2";
    static final String TS = "1794169963000";
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock webhookClock() { return Clock.fixed(Instant.parse("2026-11-08T20:32:43Z"), ZoneOffset.UTC); }
    }
    ObjectNode fixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/payment/order-action-required.json")) {
            return (ObjectNode) JSON.readTree(stream);
        }
    }
    String signature(String id, String ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("fixture-webhook-secret-only".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(
                ("id:" + id.toLowerCase(java.util.Locale.ROOT) + ";request-id:fixture-request;ts:" + ts + ";").getBytes(StandardCharsets.UTF_8)));
    }
    HttpHeaders signed(String id, String ts) throws Exception {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-request-id", "fixture-request"); headers.set("x-signature", signature(id, ts)); return headers;
    }
    ResponseEntity<ObjectNode> send(String id, Object body, HttpHeaders headers) {
        return http.exchange(url("/webhooks/mercadopago?data.id=" + id + "&type=order"), HttpMethod.POST,
                new HttpEntity<>(body, headers), ObjectNode.class);
    }
    br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse create() {
        forcedOrderId = ORDER;
        return service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
    }
    @Test void officialManifestWithKnownSignatureAcceptsFullFixtureWithoutTrustingItsValues() throws Exception {
        var created = create();
        var headers = signed(ORDER, TS);
        // Vetor fixo calculado independentemente da implementação Java.
        headers.set("x-signature", "ts=" + TS + ",v1=a8b6e05d88a73f7728bd55f741bcc6f70790787423d502fc7fcb92edfeae19b0");
        var response = send(ORDER, fixture(), headers);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var persisted = service.getPayment("integration-a", created.id());
        assertThat(persisted.status().name()).isEqualTo("GERADO");
        assertThat(persisted.amount()).isEqualByComparingTo("50.00");
        assertThat(posts).hasValue(1); assertThat(gets).hasValue(1);
    }
    @Test void ordersHeaderPreservingUppercaseIdAsOfficialSdkIsAccepted() throws Exception {
        create();
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", "2066ca19-c6f1-498a-be75-1923005edd06");
        // Vetor independente: mesmo manifesto do SDK Java, sem converter o ID da query.
        headers.set("X-Signature", "ts=" + TS + ", v1=e2eb1b8a6008429fcfe29ec32053d2a96535e063cd09a08e1064f8be0ad6866a");
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(gets).hasValue(1);
    }
    @Test void rejectionReasonsAreLoggedWithoutSecretSignatureOrManifest(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var headers = signed(ORDER, TS);
        String signatureValue = headers.getFirst("x-signature");
        headers.set("x-signature", "ts=" + TS + ",v1=" + "0".repeat(64));
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers.set("x-signature", "ts=invalid,v1=" + "0".repeat(64));
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers.remove("x-signature");
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String old = Long.toString(Long.parseLong(TS) - 86_400_001L);
        assertThat(send(ORDER, fixture(), signed(ORDER, old)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var missingId = http.exchange(url("/webhooks/mercadopago"), HttpMethod.POST,
                new HttpEntity<>(fixture(), signed(ORDER, TS)), ObjectNode.class);
        assertThat(missingId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(output.getAll()).contains("reason=HMAC_MISMATCH", "reason=TIMESTAMP_INVALID", "reason=SIGNATURE_MISSING",
                "reason=TIMESTAMP_OUT_OF_WINDOW", "reason=PARAMETERS_INVALID")
                .doesNotContain("fixture-webhook-secret-only", signatureValue, "id:" + ORDER, "0".repeat(64));
        assertThat(gets).hasValue(0); assertThat(repository.count()).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(longs = {-86_400_000L, 300_000L})
    void timestampWindowBoundariesRemainAccepted(long offsetMillis) throws Exception {
        create(); String ts = Long.toString(Long.parseLong(TS) + offsetMillis);
        assertThat(send(ORDER, fixture(), signed(ORDER, ts)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void alteredRequestIdAndOtherSecretCannotAuthenticate() throws Exception {
        var headers = signed(ORDER, TS); headers.set("X-Request-Id", "different-request-id");
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("another-fictitious-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(("id:" + ORDER + ";request-id:fixture-request;ts:" + TS + ";").getBytes(StandardCharsets.UTF_8)));
        headers = signed(ORDER, TS); headers.set("x-signature", "ts=" + TS + ",v1=" + hash);
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(gets).hasValue(0); assertThat(repository.count()).isZero();
    }
    @Test void unsignedBodyCannotDeclarePaymentPaid() throws Exception {
        var created = create(); var body = fixture();
        ((ObjectNode) body.path("data")).put("status", "processed").put("status_detail", "accredited").put("total_amount", "99999.00");
        body.put("action", "order.processed");
        assertThat(send(ORDER, body, signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("GERADO");
    }
    @Test void duplicateSignatureFieldsAndHeadersAreRejected() throws Exception {
        create(); var headers = signed(ORDER, TS);
        headers.set("x-signature", signature(ORDER, TS) + ",ts=" + TS);
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers = signed(ORDER, TS); headers.add("x-signature", signature(ORDER, TS));
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(gets).hasValue(0);
    }
    @Test void oversizedOrAmbiguousJsonIsRejectedWithoutProviderCall() throws Exception {
        create();
        assertThat(send(ORDER, "{\"padding\":\"" + "x".repeat(70_000) + "\"}", signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        String duplicate = "{\"data\":{\"id\":\"" + ORDER + "\",\"id\":\"ORDOTHER\"}}";
        assertThat(send(ORDER, duplicate, signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(gets).hasValue(0);
    }
    @Test void delayedRedeliveryWithinWindowIsAccepted() throws Exception {
        create();
        String delayed = Long.toString(Long.parseLong(TS) - 30 * 60 * 1000);
        assertThat(send(ORDER, fixture(), signed(ORDER, delayed)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void unassociatedReservationRequiresManualRecoveryInsteadOfWebhookAssociation() throws Exception {
        createStatus = 503; var created = create();
        assertThat(created.orderId()).isNull();
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).orderId()).isEqualTo(ORDER);
        assertThat(gets).hasValue(1); assertThat(posts).hasValue(1);
    }
    @Test void orderFailedNotificationUsesVerifiedProviderState() throws Exception {
        var created = create();
        var provider = orders.get(ORDER);
        provider.put("status", "failed").put("status_detail", "processing_error");
        var body = fixture(); body.put("action", "order.failed");

        assertThat(send(ORDER, body, signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("FALHOU");
        assertThat(gets).hasValue(1);
    }
    @Test void orderActionRequiredNotificationUsesVerifiedProviderState() throws Exception {
        var created = create();
        var body = fixture(); body.put("action", "order.action_required");

        assertThat(send(ORDER, body, signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("GERADO");
        assertThat(gets).hasValue(1);
    }
    @Test void orderProcessedNotificationUsesVerifiedProviderState() throws Exception {
        var created = create();
        markPaid(orders.get(ORDER));
        var body = fixture(); body.put("action", "order.processed");

        assertThat(send(ORDER, body, signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("PAGO");
        assertThat(gets).hasValue(1);
    }
    @Test void duplicateNotificationsAreReadOnlyAndDoNotCreatePayments() throws Exception {
        var created = create();
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        var before = repository.findById(created.id()).orElseThrow();
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        var after = repository.findById(created.id()).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(before.getVersion());
        assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(repository.count()).isEqualTo(1); assertThat(posts).hasValue(1);
    }
    @Test void paidProviderStateWinsOverPendingNotificationAndLatePendingCannotRegressIt() throws Exception {
        var created = create(); markPaid(orders.get(ORDER));
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("PAGO");
        var pending = orders.get(ORDER); pending.put("status", "action_required").put("status_detail", "waiting_payment");
        ((ObjectNode) pending.path("transactions").path("payments").get(0)).put("status", "action_required").put("status_detail", "waiting_payment");
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("PAGO");
    }
    @Test void invalidAndMissingSignaturesDoNotCallProviderOrWrite() throws Exception {
        var created = create(); var before = repository.findById(created.id()).orElseThrow().getVersion();
        var headers = signed(ORDER, TS); headers.set("x-signature", "ts=" + TS + ",v1=" + "0".repeat(64));
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers.remove("x-signature");
        assertThat(send(ORDER, fixture(), headers).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(repository.findById(created.id()).orElseThrow().getVersion()).isEqualTo(before);
        assertThat(gets).hasValue(0);
    }
    @Test void orderMissingAtProviderHasDistinctLogAndIsNotAcknowledgedAsProcessed(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(repository.count()).isZero(); assertThat(posts).hasValue(0); assertThat(gets).hasValue(1);
        assertThat(output.getAll()).contains("synchronization=provider_order_not_found");
    }
    @Test void providerOrderWithoutLocalPaymentHasDistinctLog(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var created = create();
        repository.deleteById(created.id()); repository.flush();

        var response = send(ORDER, fixture(), signed(ORDER, TS));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().path("detail").asText()).contains("payment_not_found_local");
        assertThat(gets).hasValue(1); assertThat(posts).hasValue(1); assertThat(repository.count()).isZero();
        assertThat(output.getAll()).contains("resolution=payment_not_found_local");
    }
    @Test void temporaryProviderFailureIsLoggedAndCanBeRetriedWithSameNotification(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var created = create(); getStatus = 503;
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(output.getAll()).contains("synchronization=provider_temporary_failure");
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("GERADO");
        getStatus = 200; markPaid(orders.get(ORDER));
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("PAGO");
        assertThat(posts).hasValue(1);
    }
    @Test void providerCannotAssociateKnownOrderWithAnotherPayment() throws Exception {
        var created = create(); forcedOrderId = "ORDOTHER";
        var other = service.generateBoleto(command(UUID.randomUUID(), "obligation-2"));
        orders.get(ORDER).put("external_reference", other.id().toString()); markPaid(orders.get(ORDER));
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(service.getPayment("integration-a", created.id()).status().name()).isEqualTo("GERADO");
        assertThat(service.getPayment("integration-a", other.id()).status().name()).isEqualTo("GERADO");
    }
    @Test void providerAmountMismatchIsRejected() throws Exception {
        var created = create(); orders.get(ORDER).put("total_amount", "999.00");
        assertThat(send(ORDER, fixture(), signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(service.getPayment("integration-a", created.id()).amount()).isEqualByComparingTo("50.00");
    }
    @Test void malformedPayloadAndMismatchedBodyIdDoNotCallProvider() throws Exception {
        create();
        assertThat(send(ORDER, "{broken", signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = fixture(); ((ObjectNode) body.path("data")).put("id", "ORDOTHER");
        assertThat(send(ORDER, body, signed(ORDER, TS)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(gets).hasValue(0);
    }
    @Test void minimalBodyAndSecondsTimestampAreAccepted() throws Exception {
        create(); var body = JSON.createObjectNode(); body.putObject("data").put("id", ORDER);
        assertThat(send(ORDER, body, signed(ORDER, TS.substring(0, 10))).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void staleFutureOrOverflowingTimestampsAreRejected() throws Exception {
        create();
        for (var ts : new String[]{"1793997163000", "1794259963000", "99999999999999999999"}) {
            assertThat(send(ORDER, fixture(), signed(ORDER, ts)).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        assertThat(gets).hasValue(0);
    }
    @Test void presentButInvalidOrderIdIsNotReportedAsMissing() {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.exchange(url("/webhooks/mercadopago?data.id=TESTE&type=order"), HttpMethod.POST,
                new HttpEntity<>("{\"type\":\"order\",\"data\":{\"id\":\"TESTE\"}}", headers), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("detail").asText()).contains("data.id inválido").doesNotContain("obrigatório");
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0); assertThat(repository.count()).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "?data.id=ORDTEST&type=order", "?type=order&data.id=ORDTEST",
            "?data%2Eid=ORDTEST&type=order", "?data.id=ordtest&type=order"
    })
    void validDottedQueryParameterWithoutSignatureReachesAuthentication(String query) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.exchange(java.net.URI.create(url("/webhooks/mercadopago" + query)), HttpMethod.POST,
                new HttpEntity<>("{\"type\":\"order\",\"data\":{\"id\":\"ORDTEST\"}}", headers), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().path("detail").asText()).contains("Assinatura");
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0); assertThat(repository.count()).isZero();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "?type=order", "?data.id=&type=order", "?data_id=ORDTEST&type=order",
            "?data.id=ORDTEST&data.id=ORDOTHER", "?data.id=ORDTEST&data.id=ORDTEST"
    })
    void missingEmptyWrongNameOrRepeatedQueryIdRemainsBadRequest(String query) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.exchange(java.net.URI.create(url("/webhooks/mercadopago" + query)), HttpMethod.POST,
                new HttpEntity<>("{\"type\":\"order\",\"data\":{\"id\":\"ORDTEST\"}}", headers), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(posts).hasValue(0); assertThat(gets).hasValue(0); assertThat(repository.count()).isZero();
    }
    @Test void missingOrAmbiguousSignedQueryIdIsRejected() throws Exception {
        create();
        for (String suffix : new String[]{"", "?data.id=" + ORDER + "&data.id=ORDOTHER"}) {
            var response = http.exchange(url("/webhooks/mercadopago" + suffix), HttpMethod.POST,
                    new HttpEntity<>(fixture(), signed(ORDER, TS)), ObjectNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(gets).hasValue(0);
    }
}
