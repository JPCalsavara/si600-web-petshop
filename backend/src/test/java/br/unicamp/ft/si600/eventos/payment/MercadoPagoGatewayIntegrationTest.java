package br.unicamp.ft.si600.eventos.payment;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.*;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import br.unicamp.ft.si600.eventos.gateway.RestMercadoPagoGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import br.unicamp.ft.si600.eventos.config.MercadoPagoConfiguration;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoException;

import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** Exercita o gateway real e HTTP; somente o provedor externo é substituído. */
class MercadoPagoGatewayIntegrationTest {
    private static final String ORDER_ID = "ORD01J6TC8BYRR0T4ZKY0QR39WGYE";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> keys = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private ValidatorFactory validators;
    private MercadoPagoGateway gateway;
    private volatile int responseStatus = 201;
    private volatile String responseBody;
    private volatile String retryAfter;
    private volatile String responseLocation;
    private volatile long delayMillis;

    @BeforeEach
    void setUp() throws Exception {
        responseBody = response("action_required", "waiting_payment");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
                authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
                String key = exchange.getRequestHeaders().getFirst("X-Idempotency-Key");
                if (key != null) keys.add(key);
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (delayMillis > 0) Thread.sleep(delayMillis);
                if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
                if (responseLocation != null) exchange.getResponseHeaders().set("Location", responseLocation);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseStatus, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        validators = Validation.buildDefaultValidatorFactory();
        gateway = gateway(Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        validators.close();
    }

    private MercadoPagoGateway gateway(Duration timeout) {
        return new RestMercadoPagoGateway(new MercadoPagoProperties(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), "stub-token",
                Duration.ofSeconds(1), timeout), mapper, validators.getValidator());
    }

    @Test
    void deveCriarBoletoComContratoOrdersEConsultarSemPostAdicional() throws Exception {
        BoletoRequest request = request();
        OrderResult created = gateway.createBoleto(request);
        assertThat(created.providerHttpStatus()).isEqualTo(201);
        assertThat(created.orderId()).isEqualTo(ORDER_ID);
        assertThat(created.paymentId()).isEqualTo("PAY01J6TC8BYRR0T4ZKY0QRTZ0E24");
        assertThat(created.status()).isEqualTo("action_required");
        assertThat(created.paymentStatusDetail()).isEqualTo("waiting_payment");
        assertThat(created.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(created.ticketUrl()).isEqualTo("https://www.mercadopago.com.br/ticket/example");
        assertThat(created.digitableLine()).isEqualTo("1234567890");
        assertThat(created.barcodeContent()).isEqualTo("0987654321");
        JsonNode body = mapper.readTree(bodies.getFirst());
        assertThat(body.path("type").asText()).isEqualTo("online");
        assertThat(body.path("processing_mode").asText()).isEqualTo("automatic");
        assertThat(body.path("external_reference").asText()).isEqualTo(request.externalReference().toString());
        assertThat(body.path("total_amount").asText()).isEqualTo("50.00");
        assertThat(body.has("expiration_time")).isFalse();
        JsonNode payments = body.path("transactions").path("payments");
        assertThat(payments.size()).isEqualTo(1);
        assertThat(payments.get(0).path("amount").asText()).isEqualTo("50.00");
        assertThat(payments.get(0).path("expiration_time").asText()).isEqualTo("P7D");
        assertThat(payments.get(0).path("payment_method").path("id").asText()).isEqualTo("boleto");
        assertThat(payments.get(0).path("payment_method").path("type").asText()).isEqualTo("ticket");
        assertThat(body.path("payer").path("email").asText()).isEqualTo("test@example.com");
        assertThat(body.path("payer").path("first_name").asText()).isEqualTo("Pessoa");
        assertThat(body.path("payer").path("last_name").asText()).isEqualTo("Teste");
        assertThat(body.path("payer").path("identification").path("type").asText()).isEqualTo("CPF");
        assertThat(body.path("payer").path("identification").path("number").asText()).isEqualTo("99999999999");
        assertThat(body.path("payer").path("address").path("street_number").asText()).isEqualTo("S/N");
        assertThat(body.path("payer").path("address").path("state").asText()).isEqualTo("SP");
        assertThat(keys).containsExactly(request.idempotencyKey().toString());
        responseStatus = 200;
        OrderResult queried = gateway.getOrder(ORDER_ID);
        assertThat(queried.status()).isEqualTo("action_required");
        assertThat(queried.providerHttpStatus()).isEqualTo(200);
        assertThat(paths).containsExactly("POST /v1/orders", "GET /v1/orders/" + ORDER_ID);
        assertThat(authorizations).containsOnly("Bearer stub-token");
    }

    @ParameterizedTest
    @CsvSource({"400,INVALID_REQUEST", "422,INVALID_REQUEST", "401,AUTHENTICATION", "403,AUTHENTICATION",
            "404,NOT_FOUND", "409,CONFLICT", "402,PROCESSING_FAILED", "423,LOCKED", "429,RATE_LIMIT",
            "500,UNAVAILABLE", "503,UNAVAILABLE"})
    void deveClassificarErrosSemExporCorpoOuRepetir(int status, MercadoPagoException.Kind expected) {
        responseStatus = status;
        responseBody = "{\"message\":\"stub-token CPF 99999999999 payer test@example.com\"}";
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.kind()).isEqualTo(expected);
        assertThat(error.providerStatus()).isEqualTo(status);
        assertThat(error.creationMayHaveSucceeded()).isEqualTo(status >= 500 || status == 402 || status == 409 || status == 423);
        assertThat(error).hasMessageNotContaining("stub-token").hasMessageNotContaining("99999999999")
                .hasMessageNotContaining("test@example.com").hasNoCause();
        assertThat(paths).hasSize(1);
    }

    @Test
    void deveInformarRetryAfterSemRepetirAutomaticamente() {
        responseStatus = 429;
        retryAfter = "120";
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(paths).hasSize(1);
    }

    @Test
    void deveMarcarTimeoutDeCriacaoComoResultadoIncerto() {
        delayMillis = 800;
        gateway = gateway(Duration.ofMillis(100));
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.kind()).isEqualTo(MercadoPagoException.Kind.UNAVAILABLE);
        assertThat(error.creationMayHaveSucceeded()).isTrue();
        assertThat(error).hasNoCause();
        assertThat(paths).hasSize(1);
    }

    @Test
    void devePreservarChaveECorpoQuandoChamadorRepeteTentativa() {
        BoletoRequest request = request();
        responseStatus = 503;
        assertThatThrownBy(() -> gateway.createBoleto(request)).isInstanceOf(MercadoPagoException.class);
        responseStatus = 201;
        gateway.createBoleto(request);
        assertThat(keys).containsExactly(request.idempotencyKey().toString(), request.idempotencyKey().toString());
        assertThat(bodies.get(1)).isEqualTo(bodies.get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"processed", "canceled", "failed", "provider_future_status"})
    void devePreservarEstadosExternosSemCriarMaquinaDeEstadosFinanceira(String status) {
        responseStatus = 200;
        responseBody = response(status, "provider_detail");
        OrderResult result = gateway.getOrder(ORDER_ID);
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.statusDetail()).isEqualTo("provider_detail");
        assertThat(result.paymentStatus()).isEqualTo(status);
        assertThat(paths).hasSize(1);
    }

    @Test
    void deveAceitarProcessamentoAssincronoSemInventarDadosDeBoleto() {
        responseBody = """
                {"id":"%s","external_reference":"c1b2a3d4-0000-4000-8000-000000000001",
                 "total_amount":"50.00","status":"processing"}
                """.formatted(ORDER_ID);
        OrderResult result = gateway.createBoleto(request());
        assertThat(result.status()).isEqualTo("processing");
        assertThat(result.paymentId()).isNull();
        assertThat(result.ticketUrl()).isNull();
        assertThat(paths).hasSize(1);
    }

    @Test
    void deveExporVencimentoRetornadoPelaApiSemRecalcular() {
        responseBody = responseBody.replace("\"payments\":[{",
                "\"payments\":[{\"date_of_expiration\":\"2026-11-10T23:59:59-03:00\",");
        OrderResult result = gateway.createBoleto(request());
        assertThat(result.paymentExpiresAt()).isEqualTo(OffsetDateTime.parse("2026-11-10T23:59:59-03:00"));
    }

    @Test
    void devePermitirVencimentoAusenteSemInventarData() {
        assertThat(gateway.createBoleto(request()).paymentExpiresAt()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "2026-11-99T23:59:59-03:00", "2026-11-10"})
    void deveRejeitarVencimentoMalformadoSemExporResposta(String date) {
        responseBody = responseBody.replace("\"payments\":[{",
                "\"payments\":[{\"date_of_expiration\":\"" + date + "\",");
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.kind()).isEqualTo(MercadoPagoException.Kind.INVALID_RESPONSE);
        assertThat(error.creationMayHaveSucceeded()).isTrue();
        assertThat(error).hasNoCause().hasMessageNotContaining(date);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "{invalid", "{\"id\":\"ORD123\",\"status\":\"processed\"}"})
    void deveRejeitarRespostaIncompletaOuMalformadaComoResultadoIncerto(String body) {
        responseBody = body;
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.kind()).isEqualTo(MercadoPagoException.Kind.INVALID_RESPONSE);
        assertThat(error.creationMayHaveSucceeded()).isTrue();
    }

    @Test
    void deveRejeitarRespostaQueNaoCorrespondeAReferenciaOuValorSolicitado() {
        responseBody = responseBody.replace("50.00", "51.00");
        assertThatThrownBy(() -> gateway.createBoleto(request())).isInstanceOf(MercadoPagoException.class);
        responseBody = response("action_required", "waiting_payment")
                .replace("c1b2a3d4-0000-4000-8000-000000000001", UUID.randomUUID().toString());
        assertThatThrownBy(() -> gateway.createBoleto(request())).isInstanceOf(MercadoPagoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORD123/other", "https://attacker.example", "ORD123?token=secret", "", "PAY123"})
    void deveRejeitarIdInvalidoAntesDeConsultar(String id) {
        assertThatThrownBy(() -> gateway.getOrder(id)).isInstanceOf(IllegalArgumentException.class);
        assertThat(paths).isEmpty();
    }

    @Test
    void deveRejeitarConsultaComOrderDiferenteDaSolicitada() {
        responseStatus = 200;
        assertThatThrownBy(() -> gateway.getOrder("ORD999"))
                .isInstanceOf(MercadoPagoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "50.001", "100000000000000"})
    void deveRejeitarValorInvalidoSemChamadaExterna(String amount) {
        BoletoRequest valid = request();
        BoletoRequest invalid = new BoletoRequest(valid.externalReference(), valid.idempotencyKey(),
                new BigDecimal(amount), valid.description(), valid.expirationDays(), valid.payer());
        assertThatThrownBy(() -> gateway.createBoleto(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(paths).isEmpty();
    }

    @Test
    void deveRejeitarDadosAusentesEEnderecoIncompletoSemChamadaExterna() {
        assertThatThrownBy(() -> gateway.createBoleto(null)).isInstanceOf(IllegalArgumentException.class);
        BoletoRequest valid = request();
        BoletoRequest invalid = new BoletoRequest(valid.externalReference(), null, valid.amount(),
                valid.description(), 2, new Payer("invalid", "", "", "CPF", "1", null));
        assertThatThrownBy(() -> gateway.createBoleto(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(paths).isEmpty();
    }

    @Test
    void deveRejeitarRedirecionamentoSemEnviarCredencialAOutroDestino() {
        responseStatus = 302;
        responseLocation = "http://127.0.0.1:" + server.getAddress().getPort() + "/unexpected";
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.providerStatus()).isEqualTo(302);
        assertThat(paths).hasSize(1);
    }

    @Test
    void deveManterIntegracaoDesligadaSemCredenciais() {
        new ApplicationContextRunner().withUserConfiguration(MercadoPagoConfiguration.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(MercadoPagoGateway.class));
    }

    @Test
    void deveRecusarIntegracaoHabilitadaSemToken() {
        new ApplicationContextRunner().withUserConfiguration(MercadoPagoConfiguration.class)
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withPropertyValues("mercadopago.enabled=true", "mercadopago.base-url=https://api.mercadopago.com",
                        "mercadopago.connect-timeout=3s", "mercadopago.read-timeout=10s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void deveConfigurarGatewayHabilitadoPelasPropriedades() {
        new ApplicationContextRunner().withUserConfiguration(MercadoPagoConfiguration.class)
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
                .withPropertyValues("mercadopago.enabled=true", "mercadopago.base-url=http://127.0.0.1:" + server.getAddress().getPort(),
                        "mercadopago.access-token=stub-token", "mercadopago.connect-timeout=1s", "mercadopago.read-timeout=2s")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(MercadoPagoGateway.class);
                    assertThat(context.getBean(MercadoPagoGateway.class).createBoleto(request()).status())
                            .isEqualTo("action_required");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "https://user:password@example.com", "https://example.com?token=x",
            "https://example.com/path", "invalid"})
    void deveRejeitarConfiguracaoInseguraAntesDeEnviarToken(String baseUrl) {
        assertThatThrownBy(() -> new RestMercadoPagoGateway(new MercadoPagoProperties(true, baseUrl, "stub-token",
                Duration.ofSeconds(1), Duration.ofSeconds(1)), mapper, validators.getValidator()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("password");
        assertThat(paths).isEmpty();
    }

    @Test
    void deveRecusarRespostaComMaisDeUmaTransacaoOuMeioDiferente() {
        responseBody = responseBody.replace("\"id\":\"boleto\"", "\"id\":\"pix\"");
        assertThatThrownBy(() -> gateway.createBoleto(request())).isInstanceOf(MercadoPagoException.class);
        responseBody = response("action_required", "waiting_payment")
                .replace("\"payments\":[", "\"payments\":[{},");
        assertThatThrownBy(() -> gateway.createBoleto(request())).isInstanceOf(MercadoPagoException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "not-a-date"})
    void deveIgnorarRetryAfterInvalidoSemMascararErroOriginal(String header) {
        responseStatus = 429;
        retryAfter = header;
        MercadoPagoException error = catchThrowableOfType(() -> gateway.createBoleto(request()), MercadoPagoException.class);
        assertThat(error.kind()).isEqualTo(MercadoPagoException.Kind.RATE_LIMIT);
        assertThat(error.retryAfter()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 31})
    void deveRecusarDuracaoForaDaPoliticaDoAdr(int days) {
        BoletoRequest valid = request();
        BoletoRequest invalid = new BoletoRequest(valid.externalReference(), valid.idempotencyKey(), valid.amount(),
                valid.description(), days, valid.payer());
        assertThatThrownBy(() -> gateway.createBoleto(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(paths).isEmpty();
    }

    private BoletoRequest request() {
        return new BoletoRequest(UUID.fromString("c1b2a3d4-0000-4000-8000-000000000001"),
                UUID.fromString("6f1d2c0e-3b7a-4c58-9a41-2d5f8e7b1a90"), new BigDecimal("50"),
                "Taxa de teste", 7, new Payer("test@example.com", "Pessoa", "Teste", "CPF",
                "99999999999", new Address("06233903", "Rua Teste", "S/N", "Bairro", "Osasco", "SP")));
    }

    private String response(String status, String detail) {
        return """
                {"id":"%s","external_reference":"c1b2a3d4-0000-4000-8000-000000000001",
                 "total_amount":"50.00","status":"%s","status_detail":"%s","live_mode":false,
                 "transactions":{"payments":[{"id":"PAY01J6TC8BYRR0T4ZKY0QRTZ0E24",
                  "status":"%s","status_detail":"%s","payment_method":{"id":"boleto","type":"ticket",
                  "ticket_url":"https://www.mercadopago.com.br/ticket/example",
                  "digitable_line":"1234567890","barcode_content":"0987654321"}}]}}
                """.formatted(ORDER_ID, status, detail, status, detail);
    }
}
