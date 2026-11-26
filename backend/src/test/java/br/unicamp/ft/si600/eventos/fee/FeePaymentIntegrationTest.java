package br.unicamp.ft.si600.eventos.fee;

import br.unicamp.ft.si600.eventos.entity.*;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.PaymentRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectHistoryEventRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoProperties;
import br.unicamp.ft.si600.eventos.service.ProjectService;
import jakarta.validation.Validator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-11: o Cliente gera o boleto de uma taxa. O Mercado Pago é o único componente simulado (servidor HTTP local);
 * banco, serviços e controllers são reais.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.fingerprint-secret=fixture-only-32-byte-secret-do-not-use-in-production")
@Import(FeePaymentIntegrationTest.StubGatewayConfig.class)
class FeePaymentIntegrationTest {
    private static final String CLIENT = "cliente-us11@exemplo.com";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpServer STUB;
    private static final AtomicInteger POSTS = new AtomicInteger();
    private static final List<String> POST_BODIES = new CopyOnWriteArrayList<>();
    private static final java.util.Map<String, ObjectNode> ORDERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int createStatus = 201;
    private static volatile long createDelayMillis;

    static {
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB.setExecutor(Executors.newCachedThreadPool());
            STUB.createContext("/v1/orders", exchange -> {
                try {
                    int status;
                    ObjectNode response;
                    if ("POST".equals(exchange.getRequestMethod())) {
                        int sequence = POSTS.incrementAndGet();
                        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        POST_BODIES.add(raw);
                        JsonNode request = JSON.readTree(raw);
                        response = JSON.createObjectNode();
                        String id = "ORDUS11" + sequence;
                        response.put("id", id)
                                .put("external_reference", request.path("external_reference").asString())
                                .put("total_amount", request.path("total_amount").asString())
                                .put("status", "action_required").put("status_detail", "waiting_payment");
                        ObjectNode payment = response.putObject("transactions").putArray("payments").addObject();
                        payment.put("id", "PAY" + sequence).put("status", "action_required")
                                .put("status_detail", "waiting_payment");
                        payment.putObject("payment_method").put("id", "boleto").put("type", "ticket")
                                .put("ticket_url", "https://www.mercadopago.com.br/ticket/us11")
                                .put("digitable_line", "34191790010104351004791020150008291070026000")
                                .put("barcode_content", "34191070026000000000000000000000000000000000");
                        ORDERS.put(id, response);
                        if (createDelayMillis > 0) Thread.sleep(createDelayMillis);
                        status = createStatus;
                        if (status >= 400) {
                            response = JSON.createObjectNode();
                            response.putArray("errors").addObject().put("code", "simulated_failure");
                        }
                    } else {
                        String path = exchange.getRequestURI().getPath();
                        ObjectNode known = ORDERS.get(path.substring(path.lastIndexOf('/') + 1));
                        response = known == null ? JSON.createObjectNode() : known.deepCopy();
                        status = known == null ? 404 : 200;
                    }
                    byte[] body = JSON.writeValueAsBytes(response);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.io.IOException ex) {
                    // O cliente desistiu (timeout simulado): nada a responder.
                } finally {
                    exchange.close();
                }
            });
            STUB.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> STUB.stop(0)));
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    /**
     * O contexto de teste já contém outro MercadoPagoGateway (de PaymentIntegrationSupport, capturado pelo scan).
     * Este é @Primary para que o PaymentService use o servidor simulado desta classe, com timeout curto.
     */
    @TestConfiguration
    static class StubGatewayConfig {
        @Bean
        @Primary
        MercadoPagoGateway us11Gateway(ObjectMapper mapper, Validator validator) {
            return new MercadoPagoGateway(new MercadoPagoProperties(true,
                    "http://127.0.0.1:" + STUB.getAddress().getPort(), "fixture-token",
                    Duration.ofSeconds(1), Duration.ofSeconds(1)), mapper, validator);
        }
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired ProjectRepository projects;
    @Autowired FeeRepository fees;
    @Autowired ProjectFeeRepository projectFees;
    @Autowired PaymentRepository payments;
    @Autowired ProjectHistoryEventRepository history;

    Project project;
    ProjectFee rent;

    @BeforeEach
    void setUp() {
        cleanUp();
        POSTS.set(0);
        POST_BODIES.clear();
        ORDERS.clear();
        createStatus = 201;
        createDelayMillis = 0;
        project = approvedProject(daysFromToday(10), address());
        Fee rentFee = fees.save(new Fee("Aluguel", null, FeeType.FIXA, null, new BigDecimal("100.00"),
                null, null, null, null));
        rent = projectFees.save(new ProjectFee(project, rentFee));
    }

    @AfterEach
    void cleanUp() {
        history.deleteAll();
        payments.deleteAll();
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
    }

    // ---- Casos bons ----

    @Test
    void deveGerarBoletoDaTaxaFixaMarcarComoPagamentoGeradoERegistrarHistorico() throws Exception {
        ResponseEntity<JsonNode> response = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status").asString()).isEqualTo("GERADO");
        assertThat(response.getBody().get("amount").decimalValue()).isEqualByComparingTo("100.00");
        // O boleto em si só é entregue na consulta; o POST não o devolve.
        assertThat(response.getBody().has("ticketUrl")).isFalse();
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectFeeStatus.PAGAMENTO_GERADO);
        assertThat(POSTS.get()).isEqualTo(1);
        assertThat(payments.findAll()).singleElement().satisfies(p -> {
            assertThat(p.getObligationReference()).isEqualTo("fee:" + rent.getId() + ":1");
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.GERADO);
        });
        assertThat(paymentEvents()).isEqualTo(1);
    }

    @Test
    void deveEnviarAoMercadoPagoValorEDadosDoPagadorDoProjeto() throws Exception {
        generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        JsonNode sent = JSON.readTree(POST_BODIES.get(0));
        assertThat(sent.path("total_amount").asString()).isEqualTo("100.00");
        assertThat(sent.path("payer").path("email").asString()).isEqualTo(CLIENT);
        assertThat(sent.path("payer").path("first_name").asString()).isEqualTo("Maria");
        assertThat(sent.path("payer").path("last_name").asString()).isEqualTo("Souza Lima");
        assertThat(sent.path("payer").path("identification").path("type").asString()).isEqualTo("CNPJ");
        assertThat(sent.path("payer").path("identification").path("number").asString()).isEqualTo("54018395000189");
        assertThat(sent.path("payer").path("address").path("zip_code").asString()).isEqualTo("13083852");
        assertThat(sent.path("payer").path("address").path("state").asString()).isEqualTo("SP");
        assertThat(sent.path("transactions").path("payments").get(0).path("payment_method").path("id").asString())
                .isEqualTo("boleto");
    }

    @Test
    void deveGerarBoletoDaTaxaVariavelCotadaComOValorCotado() throws Exception {
        ProjectFee energy = quotedVariableFee("Energia", "12.00", "480.50");

        ResponseEntity<JsonNode> response = generate(project.getId(), energy.getId(), CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(JSON.readTree(POST_BODIES.get(0)).path("total_amount").asString()).isEqualTo("480.50");
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectFeeStatus.PAGAMENTO_GERADO);
    }

    @Test
    void deveGerarBoletoDaTaxaPorMetragemComAAreaDoProjeto() throws Exception {
        Fee perArea = fees.save(new Fee("Limpeza", null, FeeType.POR_METRAGEM, AreaPricingMode.VALOR_POR_M2,
                null, new BigDecimal("3.00"), null, null, null));
        ProjectFee cleaning = projectFees.save(new ProjectFee(project, perArea));

        ResponseEntity<JsonNode> response = generate(project.getId(), cleaning.getId(), CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // 50 m² x R$ 3,00
        assertThat(JSON.readTree(POST_BODIES.get(0)).path("total_amount").asString()).isEqualTo("150.00");
    }

    @ParameterizedTest
    @CsvSource({"10,P10D", "1,P3D", "-5,P3D", "90,P30D"})
    void vencimentoDoBoletoSegueAOPrazoDePagamentoDentroDosLimitesDoProvedor(int daysToDeadline, String expected)
            throws Exception {
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
        project = approvedProject(daysFromToday(daysToDeadline), address());
        Fee rentFee = fees.save(new Fee("Aluguel", null, FeeType.FIXA, null, new BigDecimal("100.00"),
                null, null, null, null));
        rent = projectFees.save(new ProjectFee(project, rentFee));

        generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertThat(JSON.readTree(POST_BODIES.get(0)).path("transactions").path("payments").get(0)
                .path("expiration_time").asString()).isEqualTo(expected);
    }

    @Test
    void clienteDeveConseguirOBoletoDaTaxaComPagamentoGerado() {
        generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        ResponseEntity<JsonNode> response = getPayment(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("ticketUrl").asString()).isEqualTo("https://www.mercadopago.com.br/ticket/us11");
        assertThat(response.getBody().get("digitableLine").asString()).startsWith("34191790010104351004");
        assertThat(response.getBody().get("barcodeContent").asString()).isNotBlank();
        assertThat(response.getBody().get("status").asString()).isEqualTo("GERADO");
    }

    @Test
    void falhaDefinitivaDoProvedorDeveManterATaxaEPermitirNovaTentativa() {
        createStatus = 400;

        ResponseEntity<JsonNode> failed = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertProblem(failed, HttpStatus.BAD_GATEWAY);
        ProjectFee unchanged = projectFees.findById(rent.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
        assertThat(paymentEvents()).isZero();
        assertThat(payments.findAll()).singleElement()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.FALHOU));

        createStatus = 201;
        ResponseEntity<JsonNode> retry = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectFeeStatus.PAGAMENTO_GERADO);
        assertThat(payments.findAll()).extracting(Payment::getObligationReference)
                .containsExactlyInAnyOrder("fee:" + rent.getId() + ":1", "fee:" + rent.getId() + ":2");
        assertThat(paymentEvents()).isEqualTo(1);
    }

    @Test
    void cliquesSimultaneosDevemEmitirUmUnicoBoleto() throws Exception {
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        try {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return generate(project.getId(), rent.getId(), CLIENT, "CLIENT").getStatusCode();
                }));
            }
            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> future : futures) statuses.add(future.get(30, TimeUnit.SECONDS));

            assertThat(POSTS.get()).isEqualTo(1);
            assertThat(payments.count()).isEqualTo(1);
            assertThat(paymentEvents()).isEqualTo(1);
            assertThat(statuses).contains(HttpStatus.CREATED);
            assertThat(statuses).allMatch(s -> s.equals(HttpStatus.CREATED) || s.equals(HttpStatus.CONFLICT));
            assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus())
                    .isEqualTo(ProjectFeeStatus.PAGAMENTO_GERADO);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- Casos ruins ----

    @Test
    void naoDeveGerarSegundoPagamentoParaTaxaComPagamentoAtivo() {
        generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        ResponseEntity<JsonNode> second = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertProblem(second, HttpStatus.CONFLICT);
        assertThat(POSTS.get()).isEqualTo(1);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(paymentEvents()).isEqualTo(1);
    }

    @Test
    void erroDoProvedorDeveManterATaxaNoStatusAnteriorSemAlterarDados() {
        createStatus = 500;

        ResponseEntity<JsonNode> response = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
        assertThat(paymentEvents()).isZero();
    }

    @Test
    void timeoutDoProvedorDeveManterATaxaEBloquearNovaEmissaoAteConfirmacao() {
        createDelayMillis = 2500;

        ResponseEntity<JsonNode> response = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
        assertThat(payments.findAll()).singleElement()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo(PaymentStatus.INCERTO));

        // Resultado incerto: o boleto pode existir. Não se emite outro.
        createDelayMillis = 0;
        ResponseEntity<JsonNode> retry = generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertProblem(retry, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(POSTS.get()).isEqualTo(1);
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
    }

    @Test
    void naoDeveGerarPagamentoDeTaxaVariavelAindaNaoCotada() {
        Fee energyFee = fees.save(new Fee("Energia", null, FeeType.VARIAVEL, null, null, null, null, null, "kVA"));
        ProjectFee waiting = new ProjectFee(project, energyFee);
        waiting.submitQuantity(new BigDecimal("12.00"), OffsetDateTime.now());
        waiting = projectFees.save(waiting);

        ResponseEntity<JsonNode> response = generate(project.getId(), waiting.getId(), CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(POSTS.get()).isZero();
        assertThat(projectFees.findById(waiting.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectFeeStatus.AGUARDANDO_COTACAO);
    }

    @ParameterizedTest
    @CsvSource({"AGUARDANDO_PDF", "PDF_EM_ANALISE", "REPROVADO"})
    void naoDeveGerarPagamentoComPdfNaoAprovado(ProjectStatus status) {
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
        Project p = Project.create(CLIENT, "Empresa Pendente", "54.018.395/0001-89", "54018395000189", "Maria Souza",
                "B2C", "texto", address(), CLIENT, OffsetDateTime.now().plusDays(5), daysFromToday(10), "admin-1",
                OffsetDateTime.now());
        if (status == ProjectStatus.PDF_EM_ANALISE) {
            p.submitPdf("key", "planta.pdf", "application/pdf", 10, new BigDecimal("50.00"), OffsetDateTime.now());
        } else if (status == ProjectStatus.REPROVADO) {
            p.reject("admin-1", OffsetDateTime.now(), "fora do padrão");
        }
        p = projects.save(p);
        Fee fee = fees.save(new Fee("Aluguel", null, FeeType.FIXA, null, new BigDecimal("100.00"),
                null, null, null, null));
        ProjectFee link = projectFees.save(new ProjectFee(p, fee));

        ResponseEntity<JsonNode> response = generate(p.getId(), link.getId(), CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(POSTS.get()).isZero();
    }

    @Test
    void naoDeveGerarPagamentoParaProjetoSemEnderecoEstruturado() {
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
        Project legacy = approvedProject(daysFromToday(10), null);
        Fee fee = fees.save(new Fee("Aluguel", null, FeeType.FIXA, null, new BigDecimal("100.00"),
                null, null, null, null));
        ProjectFee link = projectFees.save(new ProjectFee(legacy, fee));

        ResponseEntity<JsonNode> response = generate(legacy.getId(), link.getId(), CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("detail").asString()).contains("endereço");
        assertThat(POSTS.get()).isZero();
        assertThat(projectFees.findById(link.getId()).orElseThrow().getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
    }

    @Test
    void clienteNaoDeveGerarPagamentoDeProjetoDeOutroCliente() {
        ResponseEntity<JsonNode> response = generate(project.getId(), rent.getId(), "outro@exemplo.com", "CLIENT");

        assertProblem(response, HttpStatus.NOT_FOUND);
        assertThat(POSTS.get()).isZero();
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getStatus()).isEqualTo(ProjectFeeStatus.DISPONIVEL);
    }

    @Test
    void adminNaoDeveGerarPagamentoDeTaxaDoCliente() {
        assertProblem(generate(project.getId(), rent.getId(), "admin-1", "ADMIN"), HttpStatus.FORBIDDEN);
        assertThat(POSTS.get()).isZero();
    }

    @Test
    void semAutenticacaoDeveRetornar401() {
        assertThat(generate(project.getId(), rent.getId(), null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void taxaInexistenteDeveRetornar404() {
        assertProblem(generate(project.getId(), UUID.randomUUID(), CLIENT, "CLIENT"), HttpStatus.NOT_FOUND);
    }

    @Test
    void consultaDoBoletoSemPagamentoGeradoDeveRetornar404() {
        assertProblem(getPayment(project.getId(), rent.getId(), CLIENT, "CLIENT"), HttpStatus.NOT_FOUND);
    }

    @Test
    void clienteNaoDeveConsultarBoletoDeOutroCliente() {
        generate(project.getId(), rent.getId(), CLIENT, "CLIENT");

        assertProblem(getPayment(project.getId(), rent.getId(), "outro@exemplo.com", "CLIENT"), HttpStatus.NOT_FOUND);
    }

    @Test
    void cotacaoBloqueadaAposGeracaoDoPagamento() throws Exception {
        ProjectFee energy = quotedVariableFee("Energia", "12.00", "480.50");
        generate(project.getId(), energy.getId(), CLIENT, "CLIENT");

        ResponseEntity<JsonNode> quantity = restTemplate.exchange(
                "/projects/" + project.getId() + "/fees/" + energy.getId() + "/quantity", HttpMethod.PUT,
                new HttpEntity<>("{\"quantity\":99}", headers(CLIENT, "CLIENT")), JsonNode.class);

        assertProblem(quantity, HttpStatus.CONFLICT);
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("12.00");
    }

    // ---- Casos incompletos ----

    @Test
    void idsMalformadosDevemRetornar400SemChamarOProvedor() {
        ResponseEntity<JsonNode> badFee = restTemplate.exchange(
                "/projects/" + project.getId() + "/fees/nao-e-uuid/payment", HttpMethod.POST,
                new HttpEntity<>(headers(CLIENT, "CLIENT")), JsonNode.class);
        ResponseEntity<JsonNode> badProject = restTemplate.exchange(
                "/projects/nao-e-uuid/fees/" + rent.getId() + "/payment", HttpMethod.POST,
                new HttpEntity<>(headers(CLIENT, "CLIENT")), JsonNode.class);

        assertProblem(badFee, HttpStatus.BAD_REQUEST);
        assertProblem(badProject, HttpStatus.BAD_REQUEST);
        assertThat(POSTS.get()).isZero();
    }

    // ---- Auxiliares ----

    private ProjectFee quotedVariableFee(String name, String quantity, String amount) {
        Fee fee = fees.save(new Fee(name, null, FeeType.VARIAVEL, null, null, null, null, null, "kVA"));
        ProjectFee link = new ProjectFee(project, fee);
        link.submitQuantity(new BigDecimal(quantity), OffsetDateTime.now());
        link.quote(new BigDecimal(amount), "admin-1", OffsetDateTime.now());
        return projectFees.save(link);
    }

    private long paymentEvents() {
        return history.findAllByProjectIdOrderByCreatedAtAscSequenceOrderAsc(project.getId()).stream()
                .filter(e -> e.getEventType() == ProjectHistoryEventType.PAGAMENTO_GERADO).count();
    }

    private Project approvedProject(OffsetDateTime paymentDeadline, ProjectAddress address) {
        Project p = Project.create(CLIENT, "Empresa US11", "54.018.395/0001-89", "54018395000189", "Maria Souza Lima",
                "B2C", "texto livre", address, CLIENT, OffsetDateTime.now().plusDays(5), paymentDeadline,
                "admin-1", OffsetDateTime.now());
        p.approve("admin-1", OffsetDateTime.now(), new BigDecimal("50.00"));
        return projects.save(p);
    }

    private static ProjectAddress address() {
        return new ProjectAddress("13083852", "Av. Brasil", "100", null, "Centro", "Campinas", "SP");
    }

    private static OffsetDateTime daysFromToday(int days) {
        return LocalDate.now(ProjectService.EVENT_ZONE).plusDays(days).atTime(23, 59, 59)
                .atZone(ProjectService.EVENT_ZONE).toOffsetDateTime();
    }

    private ResponseEntity<JsonNode> generate(UUID projectId, UUID projectFeeId, String user, String role) {
        return restTemplate.exchange("/projects/" + projectId + "/fees/" + projectFeeId + "/payment",
                HttpMethod.POST, new HttpEntity<>(headers(user, role)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getPayment(UUID projectId, UUID projectFeeId, String user, String role) {
        return restTemplate.exchange("/projects/" + projectId + "/fees/" + projectFeeId + "/payment",
                HttpMethod.GET, new HttpEntity<>(headers(user, role)), JsonNode.class);
    }

    private HttpHeaders headers(String user, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (role != null) {
            headers.set("X-Authenticated-User-Id", user);
            headers.set("X-Authenticated-User-Role", role);
        }
        return headers;
    }

    private void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody().get("status").asInt()).isEqualTo(status.value());
    }
}
