package br.unicamp.ft.si600.eventos.payment;

import br.unicamp.ft.si600.eventos.dto.payment.GenerateBoletoCommand;
import br.unicamp.ft.si600.eventos.entity.Payment;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.*;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import br.unicamp.ft.si600.eventos.repository.PaymentRepository;
import br.unicamp.ft.si600.eventos.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


@SpringBootTest(classes = PaymentIntegrationSupport.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"payment.fingerprint-secret=fixture-only-32-byte-secret-do-not-use-in-production",
                "spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=update",
                "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:db/payment-schema.sql"})
abstract class PaymentIntegrationSupport {
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
                        ObjectNode knownOrder = orders.get(id);
                        response = knownOrder == null ? JSON.createObjectNode() : knownOrder.deepCopy();
                        Consumer<ObjectNode> hook = onGet;
                        if (hook != null) hook.accept(response);
                        status = knownOrder == null ? 404 : getStatus;
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
            Runtime.getRuntime().addShutdownHook(new Thread(PaymentIntegrationSupport::close));
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
            return new MercadoPagoGateway(new MercadoPagoProperties(true,
                    "http://127.0.0.1:" + stub.getAddress().getPort(), "fixture-token",
                    Duration.ofSeconds(1), Duration.ofSeconds(2)), JSON, validator);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("payment.test.jdbc-url", "jdbc:h2:mem:payments;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> url.startsWith("jdbc:postgresql:")
                ? "org.postgresql.Driver" : "org.h2.Driver");
        properties.add("spring.datasource.username", () -> System.getProperty("payment.test.username", "sa"));
        properties.add("spring.datasource.password", () -> System.getProperty("payment.test.password",
                System.getenv().getOrDefault("PAYMENT_TEST_PASSWORD", "")));
    }

    @Autowired PaymentService service;
    @Autowired PaymentRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectProvider<MercadoPagoGateway> gateways;
    @Autowired Validator validator;
    @Autowired Clock clock;

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
    }

    // Shared by both suites for the lifetime of the test JVM.
    static void close() {
        stub.stop(0);
        httpThreads.shutdownNow();
    }

    protected Payment raw(String obligation, UUID requestId, UUID key) {
        return new Payment("integration-a", obligation, requestId, key, "a".repeat(64), new BigDecimal("50.00"),
                "BRL", java.time.OffsetDateTime.now());
    }

    protected List<Object> race(java.util.concurrent.Callable<Object> action) throws Exception {
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

    protected static void markPaid(ObjectNode response) {
        response.put("status", "processed").put("status_detail", "accredited");
        ((ObjectNode) response.path("transactions").path("payments").get(0)).put("status", "processed").put("status_detail", "accredited");
    }

    protected GenerateBoletoCommand command(UUID requestId, String obligation) {
        return new GenerateBoletoCommand("integration-a", obligation, requestId, new BigDecimal("50.00"),
                "BRL", "Taxa de teste", 7, new Payer("test@example.com", "Pessoa", "Teste", "CPF",
                "99999999999", new Address("06233903", "Rua Teste", "S/N", "Bairro", "Osasco", "SP")));
    }
}
