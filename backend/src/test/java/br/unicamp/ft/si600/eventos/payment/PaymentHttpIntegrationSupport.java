package br.unicamp.ft.si600.eventos.payment;

import br.unicamp.ft.si600.eventos.exception.GlobalExceptionHandler;
import br.unicamp.ft.si600.eventos.security.ActorFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import br.unicamp.ft.si600.eventos.service.AuthService;

@AutoConfigureTestRestTemplate
@SpringBootTest(classes = {PaymentIntegrationSupport.Application.class, GlobalExceptionHandler.class, ActorFilter.class,
        br.unicamp.ft.si600.eventos.config.PaymentHttpConfiguration.class,
        br.unicamp.ft.si600.eventos.controller.PaymentInternalController.class,
        br.unicamp.ft.si600.eventos.controller.MercadoPagoWebhookController.class,
        br.unicamp.ft.si600.eventos.exception.PaymentHttpExceptionHandler.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"payment.fingerprint-secret=fixture-only-32-byte-secret-do-not-use-in-production",
                "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=update",
                "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/payment-schema.sql",
                "payment.internal.enabled=true", "payment.internal.token=fixture-internal-token-32-bytes-only-for-tests",
                "payment.internal.integration-id=integration-a", "payment.internal.allowed-obligations=obligation-1,obligation-2"})
abstract class PaymentHttpIntegrationSupport extends PaymentIntegrationSupport {
    @org.junit.jupiter.api.BeforeEach
    void disableClientRetries() {
        http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()));
    }
    // ActorFilter (US-01) exige AuthService; estes testes só exercitam os caminhos de pagamento, que o filtro não autentica.
    @MockitoBean AuthService authService;
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    String url(String path) { return "http://127.0.0.1:" + port + "/api" + path; }
    HttpHeaders technicalHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("fixture-internal-token-32-bytes-only-for-tests");
        return headers;
    }
}
