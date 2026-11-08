package br.unicamp.ft.si600.eventos.payment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.context.TestPropertySource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercita os placeholders de produção com variáveis fictícias, sem ler arquivos privados. */
@Import(PaymentWebhookEnvironmentIntegrationTest.DeliveryTime.class)
@TestPropertySource(properties = {"spring.config.import=classpath:mercadopago.yml",
        "PAYMENT_WEBHOOK_ENABLED=true", "PAYMENT_WEBHOOK_SECRET=fixture-config-file-secret-only"})
class PaymentWebhookEnvironmentIntegrationTest extends PaymentHttpIntegrationSupport {
    @TestConfiguration static class DeliveryTime {
        @Bean @Primary Clock deliveryClock() { return Clock.fixed(Instant.parse("2026-11-08T21:38:58Z"), ZoneOffset.UTC); }
    }
    @Test void springResolvesSecretVariableThroughActualConfigurationAndAcceptsSignedHttp() throws Exception {
        forcedOrderId = "ORDTST01M6YDSZ3XMX43XBTZZKGQYZM2";
        var created = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        // Mesmo ID/timestamp da entrega relatada; request-id e segredo da fixture são fictícios.
        String ts = "1794173937";
        String requestId = "2066ca19-c6f1-498a-be75-1923005edd06";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("fixture-config-file-secret-only".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifest = "id:" + created.orderId() + ";request-id:" + requestId + ";ts:" + ts + ";";
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Request-Id", requestId);
        headers.set("X-Signature", "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8))));
        var body = JSON.createObjectNode().put("type", "order"); body.putObject("data").put("id", created.orderId());
        var response = http.exchange(url("/webhooks/mercadopago?data.id=" + created.orderId() + "&type=order"),
                HttpMethod.POST, new HttpEntity<>(body, headers), ObjectNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(posts).hasValue(1); assertThat(gets).hasValue(1);
    }
}
