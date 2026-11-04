package br.unicamp.ft.si600.eventos.payment;

import br.unicamp.ft.si600.eventos.dto.payment.GenerateBoletoCommand;
import br.unicamp.ft.si600.eventos.entity.Payment;
import br.unicamp.ft.si600.eventos.entity.PaymentStatus;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.*;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import br.unicamp.ft.si600.eventos.gateway.RestMercadoPagoGateway;
import br.unicamp.ft.si600.eventos.repository.PaymentRepository;
import br.unicamp.ft.si600.eventos.service.PaymentService;
import br.unicamp.ft.si600.eventos.service.PaymentStatusChanged;
import br.unicamp.ft.si600.eventos.exception.PaymentException;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest(classes = PaymentServiceIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"payment.fingerprint-secret=fixture-only-32-byte-secret-do-not-use-in-production",
                "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=update",
                "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/payment-schema.sql"})
class PaymentServiceIntegrationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final AtomicInteger posts = new AtomicInteger();
    static final AtomicInteger gets = new AtomicInteger();
    static final Map<String, ObjectNode> orders = new ConcurrentHashMap<>();
    static final HttpServer stub;
    static final ExecutorService httpThreads = Executors.newCachedThreadPool();
    static volatile int createStatus = 201;
    static volatile int getStatus = 200;
    static volatile long delayMillis;
    static volatile String forcedOrderId;
    static volatile Consumer<ObjectNode> onCreate;
    static volatile Consumer<ObjectNode> onGet;
    static volatile boolean paidOnCreate;

    static {
        try {
            stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            stub.setExecutor(httpThreads);
            stub.createContext("/v1/orders", exchange -> {
                try {
                    ObjectNode response;
                    int status;
                    if ("POST".equals(exchange.getRequestMethod())) {
                        int sequence = posts.incrementAndGet();
                        var request = JSON.readTree(exchange.getRequestBody());
                        String id = forcedOrderId == null ? "ORDTST" + sequence : forcedOrderId;
                        response = JSON.createObjectNode();
                        response.put("id", id).put("external_reference", request.path("external_reference").asText())
                                .put("total_amount", request.path("total_amount").asText())
                                .put("status", "action_required").put("status_detail", "waiting_payment");
                        ObjectNode payment = response.putObject("transactions").putArray("payments").addObject();
                        payment.put("id", "PAY" + sequence).put("status", "action_required")
                                .put("status_detail", "waiting_payment");
                        payment.putObject("payment_method").put("id", "boleto").put("type", "ticket")
                                .put("ticket_url", "https://www.mercadopago.com.br/ticket/example")
                                .put("digitable_line", "1234567890").put("barcode_content", "0987654321");
                        if (paidOnCreate) markPaid(response);
                        orders.put(id, response);
                        Consumer<ObjectNode> hook = onCreate;
                        if (hook != null) hook.accept(response);
                        status = createStatus;
                        if (delayMillis > 0) Thread.sleep(delayMillis);
                    } else {
                        gets.incrementAndGet();
                        String path = exchange.getRequestURI().getPath();
                        String id = path.substring(path.lastIndexOf('/') + 1);
                        response = orders.getOrDefault(id, JSON.createObjectNode()).deepCopy();
                        Consumer<ObjectNode> hook = onGet;
                        if (hook != null) hook.accept(response);
                        status = getStatus;
                    }
                    byte[] body = JSON.writeValueAsBytes(response);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            stub.start();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Payment.class)
    @EnableJpaRepositories(basePackageClasses = PaymentRepository.class)
    @Import(PaymentService.class)
    static class Application {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean MercadoPagoGateway gateway(Validator validator) {
            return new RestMercadoPagoGateway(new MercadoPagoProperties(true,
                    "http://127.0.0.1:" + stub.getAddress().getPort(), "fixture-token",
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), JSON, validator);
        }
        @Bean EventObserver observer(PaymentService service) { return new EventObserver(service); }
    }

    static class EventObserver {
        final List<PaymentStatusChanged> received = new CopyOnWriteArrayList<>();
        final AtomicBoolean committed = new AtomicBoolean(true);
        final PaymentService service;
        EventObserver(PaymentService service) { this.service = service; }
        @EventListener
        public void observe(PaymentStatusChanged event) {
            received.add(event);
            if (service.getPayment(event.integrationId(), event.paymentId()).status() != event.status()) committed.set(false);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("payment.test.jdbc-url", "jdbc:h2:mem:payments;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> url.startsWith("jdbc:postgresql:")
                ? "org.postgresql.Driver" : "org.h2.Driver");
        properties.add("spring.datasource.username", () -> System.getProperty("payment.test.username", "sa"));
        properties.add("spring.datasource.password", () -> System.getProperty("payment.test.password", ""));
    }

    @Autowired PaymentService service;
    @Autowired PaymentRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EventObserver observer;
    @Autowired ObjectProvider<MercadoPagoGateway> gateways;
    @Autowired Validator validator;
    @Autowired Clock clock;
    @Autowired ApplicationEventPublisher publisher;

    @BeforeEach
    void reset() {
        repository.deleteAll();
        posts.set(0);
        gets.set(0);
        orders.clear();
        createStatus = 201;
        getStatus = 200;
        delayMillis = 0;
        forcedOrderId = null;
        onCreate = null;
        onGet = null;
        paidOnCreate = false;
        observer.received.clear();
        observer.committed.set(true);
    }

    @AfterAll
    static void close() {
        stub.stop(0);
        httpThreads.shutdownNow();
    }

    @Test
    void devePersistirGeracaoEReutilizarMesmaRequisicaoSemOutroHttp() {
        var command = command(UUID.randomUUID(), "obligation-1");
        var created = service.generateBoleto(command);
        assertThat(created.status()).isEqualTo(PaymentStatus.GERADO);
        assertThat(created.orderId()).isEqualTo("ORDTST1");
        assertThat(created.currency()).isEqualTo("BRL");
        assertThat(created.amount()).isEqualByComparingTo("50.00");
        assertThat(service.generateBoleto(command).id()).isEqualTo(created.id());
        assertThat(service.getPayment("integration-a", created.id()).status()).isEqualTo(PaymentStatus.GERADO);
        assertThat(posts).hasValue(1);
        assertThat(gets).hasValue(0);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deveCommitarReservaAntesDaChamadaExterna() {
        AtomicBoolean committedBeforeHttp = new AtomicBoolean();
        onCreate = response -> committedBeforeHttp.set(repository.findById(
                UUID.fromString(response.path("external_reference").asText())).orElseThrow().getStatus() == PaymentStatus.CRIANDO);
        service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        assertThat(committedBeforeHttp).isTrue();
    }

    @Test
    void deveObterBoletoComUmUnicoGetSemPersistirDadosTransitorios() {
        var created = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        var boleto = service.getBoleto("integration-a", created.id());
        assertThat(boleto.ticketUrl()).isEqualTo("https://www.mercadopago.com.br/ticket/example");
        assertThat(boleto.digitableLine()).isEqualTo("1234567890");
        assertThat(boleto.barcodeContent()).isEqualTo("0987654321");
        assertThat(gets).hasValue(1);
        String persisted = jdbc.queryForList("select * from payments").toString();
        assertThat(persisted).doesNotContain("99999999999", "test@example.com", "Rua Teste", "fixture-token",
                "digitable_line", "barcode_content", "https://www.mercadopago.com.br/ticket/example");
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where lower(table_name)='payments'", String.class)
                .stream().map(String::toLowerCase).toList()).doesNotContain("payer", "access_token", "digitable_line", "barcode_content", "ticket_url");
    }

    @ParameterizedTest
    @CsvSource({"400,FALHOU", "422,FALHOU", "401,FALHOU", "429,FALHOU", "409,INCERTO", "423,INCERTO", "402,INCERTO", "500,INCERTO", "503,INCERTO"})
    void devePersistirFalhaSemReemitirNemLiberarObrigacao(int status, PaymentStatus expected) {
        createStatus = status;
        var command = command(UUID.randomUUID(), "obligation-1");
        var failed = service.generateBoleto(command);
        assertThat(failed.status()).isEqualTo(expected);
        assertThat(failed.lastHttpStatus()).isEqualTo(status);
        assertThat(failed.lastErrorCode()).isNotBlank();
        assertThat(failed.orderId()).isNull();
        assertThat(service.generateBoleto(command).id()).isEqualTo(failed.id());
        assertThat(service.synchronizeOrder("integration-a", failed.id()).orderId()).isNull();
        assertThatThrownBy(() -> service.getBoleto("integration-a", failed.id())).isInstanceOf(PaymentException.class);
        PaymentException conflict = catchThrowableOfType(() -> service.generateBoleto(command(UUID.randomUUID(), "obligation-1")), PaymentException.class);
        assertThat(conflict.kind()).isEqualTo(PaymentException.Kind.CONFLICT);
        assertThat(conflict.paymentId()).isEqualTo(failed.id());
        assertThat(posts).hasValue(1);
        assertThat(gets).hasValue(0);
    }

    @Test
    void deveRecuperarTimeoutSomentePorGetDeCandidatoVerificado() {
        delayMillis = 2500;
        var command = command(UUID.randomUUID(), "obligation-1");
        var uncertain = service.generateBoleto(command);
        assertThat(uncertain.status()).isEqualTo(PaymentStatus.INCERTO);
        assertThat(uncertain.orderId()).isNull();
        assertThat(service.generateBoleto(command).id()).isEqualTo(uncertain.id());
        var recovered = service.synchronizeOrder("integration-a", uncertain.id(), "ORDTST1");
        assertThat(recovered.status()).isEqualTo(PaymentStatus.GERADO);
        assertThat(recovered.orderId()).isEqualTo("ORDTST1");
        assertThat(recovered.id()).isEqualTo(uncertain.id());
        assertThat(posts).hasValue(1);
        assertThat(gets).hasValue(1);
    }

    @Test
    void devePreservarReservaSeRespostaExternaNaoPuderSerPersistida() {
        service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        forcedOrderId = "ORDTST1"; // Resposta inconsistente força violação real de UK no banco.
        var command = command(UUID.randomUUID(), "obligation-2");
        PaymentException failure = catchThrowableOfType(() -> service.generateBoleto(command), PaymentException.class);
        assertThat(failure.kind()).isEqualTo(PaymentException.Kind.STORAGE_FAILURE);
        var reserved = service.getPayment("integration-a", failure.paymentId());
        assertThat(reserved.status()).isEqualTo(PaymentStatus.CRIANDO);
        assertThat(reserved.orderId()).isNull();
        assertThat(service.generateBoleto(command).id()).isEqualTo(reserved.id());
        assertThat(posts).hasValue(2);
        ObjectNode candidate = orders.get("ORDTST1").deepCopy();
        candidate.put("id", "ORDRECOVERED");
        orders.put("ORDRECOVERED", candidate);
        assertThat(service.synchronizeOrder("integration-a", reserved.id(), "ORDRECOVERED").orderId()).isEqualTo("ORDRECOVERED");
        assertThat(posts).hasValue(2);
    }

    @Test
    void deveRecusarRecuperacaoComReferenciaOuValorDiferente() {
        createStatus = 409;
        var uncertain = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        ObjectNode candidate = orders.get("ORDTST1");
        candidate.put("external_reference", UUID.randomUUID().toString());
        assertThatThrownBy(() -> service.synchronizeOrder("integration-a", uncertain.id(), "ORDTST1"))
                .isInstanceOf(PaymentException.class);
        candidate.put("external_reference", uncertain.id().toString()).put("total_amount", "51.00");
        assertThatThrownBy(() -> service.synchronizeOrder("integration-a", uncertain.id(), "ORDTST1"))
                .isInstanceOf(PaymentException.class);
        assertThat(service.getPayment("integration-a", uncertain.id()).orderId()).isNull();
        assertThat(posts).hasValue(1);
    }

    @Test
    void deveConfirmarPagamentoSomentePorGetESemRegressao() {
        paidOnCreate = true;
        var payment = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.CRIANDO);
        assertThat(payment.providerStatus()).isEqualTo("processed");
        assertThat(service.synchronizeOrder("integration-a", payment.id()).status()).isEqualTo(PaymentStatus.PAGO);
        var remote = orders.get(payment.orderId());
        remote.put("status", "action_required").put("status_detail", "waiting_payment");
        var synced = service.synchronizeOrder("integration-a", payment.id());
        assertThat(synced.status()).isEqualTo(PaymentStatus.PAGO);
        assertThat(synced.providerStatus()).isEqualTo("processed");
        assertThat(observer.received).hasSize(1);
        assertThat(observer.committed).isTrue();
    }

    @Test
    void naoDeveConfirmarSeApenasOrderConstarComoPaga() {
        var payment = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        orders.get(payment.orderId()).put("status", "processed").put("status_detail", "accredited");
        assertThat(service.synchronizeOrder("integration-a", payment.id()).status()).isNotEqualTo(PaymentStatus.PAGO);
    }

    @ParameterizedTest
    @CsvSource({"processing,in_process,CRIANDO", "future_provider_status,future_detail,INCERTO",
            "canceled,expired,EXPIRADO", "canceled,canceled,CANCELADO", "failed,rejected,FALHOU"})
    void devePreservarEstadosExternos(String status, String detail, PaymentStatus expected) {
        var payment = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        orders.get(payment.orderId()).put("status", status).put("status_detail", detail);
        var synced = service.synchronizeOrder("integration-a", payment.id());
        assertThat(synced.status()).isEqualTo(expected);
        assertThat(synced.providerStatus()).isEqualTo(status);
        assertThat(synced.providerStatusDetail()).isEqualTo(detail);
    }

    @Test
    void falhaNaConsultaNaoDeveApagarEstadoConhecido() {
        var payment = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        getStatus = 503;
        assertThatThrownBy(() -> service.synchronizeOrder("integration-a", payment.id())).isInstanceOf(MercadoPagoException.class);
        assertThat(service.getPayment("integration-a", payment.id()).status()).isEqualTo(PaymentStatus.GERADO);
        assertThat(posts).hasValue(1);
    }

    @Test
    void deveRecusarEntradaAlteradaParaMesmaRequisicao() {
        var command = command(UUID.randomUUID(), "obligation-1");
        service.generateBoleto(command);
        var changed = new GenerateBoletoCommand(command.integrationId(), command.obligationReference(), command.requestId(),
                new BigDecimal("51.00"), "BRL", command.description(), 7, command.payer());
        assertThatThrownBy(() -> service.generateBoleto(changed)).isInstanceOf(PaymentException.class);
        var otherPayer = new Payer("other@example.com", "Outra", "Pessoa", "CPF", "99999999999", command.payer().address());
        var changedPayer = new GenerateBoletoCommand(command.integrationId(), command.obligationReference(), command.requestId(),
                command.amount(), "BRL", command.description(), 7, otherPayer);
        assertThatThrownBy(() -> service.generateBoleto(changedPayer)).isInstanceOf(PaymentException.class);
        assertThat(posts).hasValue(1);
    }

    @Test
    void deveIsolarIntegracoesMesmoComMesmaObrigacaoERequestId() {
        var command = command(UUID.randomUUID(), "obligation-1");
        var first = service.generateBoleto(command);
        var other = new GenerateBoletoCommand("integration-b", command.obligationReference(), command.requestId(),
                command.amount(), "BRL", command.description(), 7, command.payer());
        var second = service.generateBoleto(other);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThatThrownBy(() -> service.getPayment("integration-a", second.id())).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> service.synchronizeOrder("integration-b", first.id())).isInstanceOf(PaymentException.class);
        assertThat(posts).hasValue(2);
        assertThat(gets).hasValue(0);
    }

    @Test
    void deveRecusarTransacaoAmbienteAntesDeReservarOuChamarHttp() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            PaymentException failure = catchThrowableOfType(() -> service.generateBoleto(command(UUID.randomUUID(), "obligation-1")), PaymentException.class);
            assertThat(failure.kind()).isEqualTo(PaymentException.Kind.AMBIENT_TRANSACTION);
        });
        assertThat(posts).hasValue(0);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveManterUmaEmissaoNaCorridaDeMesmaRequisicao() throws Exception {
        var command = command(UUID.randomUUID(), "obligation-1");
        List<Object> results = race(() -> service.generateBoleto(command));
        assertThat(results).allMatch(value -> value instanceof br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse);
        assertThat(results.stream().map(value -> ((br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse) value).id()).distinct().count()).isEqualTo(1);
        assertThat(posts).hasValue(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void repeticaoDurantePostEmAndamentoDeveRetornarReservaSemNovoPost() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        onCreate = response -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            var command = command(UUID.randomUUID(), "obligation-1");
            var first = thread.submit(() -> service.generateBoleto(command));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var repeated = service.generateBoleto(command);
            assertThat(repeated.status()).isEqualTo(PaymentStatus.CRIANDO);
            assertThat(posts).hasValue(1);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).id()).isEqualTo(repeated.id());
        } finally {
            release.countDown();
            thread.shutdownNow();
        }
    }

    @Test
    void respostaPendenteConcorrenteNaoDeveRebaixarConfirmacao() throws Exception {
        var payment = service.generateBoleto(command(UUID.randomUUID(), "obligation-1"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        onGet = response -> {
            if (!"action_required".equals(response.path("status").asText())) return;
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        };
        ExecutorService thread = Executors.newSingleThreadExecutor();
        try {
            var pending = thread.submit(() -> service.synchronizeOrder("integration-a", payment.id()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            markPaid(orders.get(payment.orderId()));
            assertThat(service.synchronizeOrder("integration-a", payment.id()).status()).isEqualTo(PaymentStatus.PAGO);
            release.countDown();
            assertThat(pending.get(5, TimeUnit.SECONDS).status()).isEqualTo(PaymentStatus.PAGO);
        } finally {
            release.countDown();
            thread.shutdownNow();
        }
    }

    @Test
    void bancoDeveRecusarValorNegativoEMoedaDiferenteDeBrl() {
        var negative = new Payment("integration-a", "obligation-1", UUID.randomUUID(), UUID.randomUUID(),
                "a".repeat(64), new BigDecimal("-1.00"), "BRL", java.time.OffsetDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(negative)).isInstanceOf(DataIntegrityViolationException.class);
        var currency = new Payment("integration-a", "obligation-1", UUID.randomUUID(), UUID.randomUUID(),
                "a".repeat(64), new BigDecimal("50.00"), "USD", java.time.OffsetDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(currency)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveManterUmaEmissaoNaCorridaDeRequisicoesDiferentesParaMesmaObrigacao() throws Exception {
        List<Object> results = race(() -> {
            try { return service.generateBoleto(command(UUID.randomUUID(), "obligation-1")); }
            catch (PaymentException failure) { return failure.kind(); }
        });
        assertThat(results.stream().filter(value -> value instanceof br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse).count()).isEqualTo(1);
        assertThat(results.stream().filter(value -> value == PaymentException.Kind.CONFLICT).count()).isEqualTo(7);
        assertThat(posts).hasValue(1);
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"request", "obligation", "key", "order", "payment"})
    void bancoDeveImpedirDuplicidadeMesmoForaDoServico(String duplicate) {
        UUID requestId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        var first = raw("obligation-1", requestId, key);
        first.applyProviderSnapshot("ORD1", "PAY1", "action_required", "waiting_payment", "action_required", "waiting_payment",
                null, 201, PaymentStatus.GERADO, java.time.OffsetDateTime.now());
        repository.saveAndFlush(first);
        var second = raw("obligation".equals(duplicate) ? "obligation-1" : "obligation-2",
                "request".equals(duplicate) ? requestId : UUID.randomUUID(), "key".equals(duplicate) ? key : UUID.randomUUID());
        second.applyProviderSnapshot("order".equals(duplicate) ? "ORD1" : "ORD2", "payment".equals(duplicate) ? "PAY1" : "PAY2",
                "action_required", "waiting_payment", "action_required", "waiting_payment", null, 201,
                PaymentStatus.GERADO, java.time.OffsetDateTime.now());
        assertThatThrownBy(() -> repository.saveAndFlush(second)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "50.001"})
    void deveRecusarValoresInvalidosSemReservarOuChamarProvedor(String amount) {
        var valid = command(UUID.randomUUID(), "obligation-1");
        var invalid = new GenerateBoletoCommand(valid.integrationId(), valid.obligationReference(), valid.requestId(),
                new BigDecimal(amount), "BRL", valid.description(), 7, valid.payer());
        assertThatThrownBy(() -> service.generateBoleto(invalid)).isInstanceOf(PaymentException.class);
        assertThat(posts).hasValue(0);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRecusarDadosAusentesMoedaInvalidaERecursoInexistente() {
        assertThatThrownBy(() -> service.generateBoleto(null)).isInstanceOf(PaymentException.class);
        var invalid = new GenerateBoletoCommand("", "", null, null, "USD", "", 0, null);
        assertThatThrownBy(() -> service.generateBoleto(invalid)).isInstanceOf(PaymentException.class);
        assertThatThrownBy(() -> service.getPayment("integration-a", UUID.randomUUID())).isInstanceOf(PaymentException.class);
        assertThat(posts).hasValue(0);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "COLOCAR_SEGREDO_ALEATORIO_ESTAVEL_COM_32_BYTES_OU_MAIS"})
    void deveRecusarSegredoAusenteFracoOuPlaceholderAntesDaReserva(String secret) {
        PaymentService configured = new PaymentService(repository, gateways, validator, clock, publisher, transactions, secret);
        PaymentException failure = catchThrowableOfType(() -> configured.generateBoleto(command(UUID.randomUUID(), "obligation-1")), PaymentException.class);
        assertThat(failure.kind()).isEqualTo(PaymentException.Kind.CONFIGURATION);
        assertThat(repository.count()).isZero();
        assertThat(posts).hasValue(0);
    }

    private Payment raw(String obligation, UUID requestId, UUID key) {
        return new Payment("integration-a", obligation, requestId, key, "a".repeat(64), new BigDecimal("50.00"),
                "BRL", java.time.OffsetDateTime.now());
    }

    private List<Object> race(java.util.concurrent.Callable<Object> action) throws Exception {
        ExecutorService threads = Executors.newFixedThreadPool(8);
        CyclicBarrier start = new CyclicBarrier(8);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Object>>();
            for (int i = 0; i < 8; i++) futures.add(threads.submit(() -> { start.await(10, TimeUnit.SECONDS); return action.call(); }));
            List<Object> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(15, TimeUnit.SECONDS));
            return results;
        } finally { threads.shutdownNow(); }
    }

    private static void markPaid(ObjectNode response) {
        response.put("status", "processed").put("status_detail", "accredited");
        ((ObjectNode) response.path("transactions").path("payments").get(0)).put("status", "processed").put("status_detail", "accredited");
    }

    private GenerateBoletoCommand command(UUID requestId, String obligation) {
        return new GenerateBoletoCommand("integration-a", obligation, requestId, new BigDecimal("50.00"),
                "BRL", "Taxa de teste", 7, new Payer("test@example.com", "Pessoa", "Teste", "CPF",
                "99999999999", new Address("06233903", "Rua Teste", "S/N", "Bairro", "Osasco", "SP")));
    }
}
