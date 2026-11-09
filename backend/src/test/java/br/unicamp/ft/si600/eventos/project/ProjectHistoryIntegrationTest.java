package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.service.ProjectHistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import(TestStorageConfig.class)
class ProjectHistoryIntegrationTest {

    private static final String ADMIN_ID = "admin-us16";
    private static final String CLIENT_EMAIL = "expositor-us16@exemplo.com";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ProjectRepository projectRepository;

    @Autowired
    FeeRepository feeRepository;

    @Autowired
    ProjectFeeRepository projectFeeRepository;

    @Autowired
    ProjectHistoryService historyService;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        try {
            jdbcTemplate.execute("DELETE FROM project_history_events");
        } catch (Exception ignored) {
            // Tabela pode ainda nao existir na fase Red do TDD.
        }
        projectFeeRepository.deleteAll();
        projectRepository.deleteAll();
        feeRepository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private HttpHeaders headers(String userId, String role) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Authenticated-User-Id", userId);
        h.set("X-Authenticated-User-Role", role);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private UUID createProjectViaHttp(String company, String document, String email) throws Exception {
        String body = """
                {
                  "name": "%s",
                  "address": "Av. das Feiras, 100",
                  "document": "%s",
                  "email": "%s",
                  "area": "B2C",
                  "pdfDeadline": "2026-12-10",
                  "paymentDeadline": "2026-12-20",
                  "representativeName": "Representante Legal"
                }
                """.formatted(company, document, email);
        ResponseEntity<String> res = restTemplate.postForEntity(
                url("/projects"),
                new HttpEntity<>(body, headers(ADMIN_ID, "ADMIN")),
                String.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode node = objectMapper.readTree(res.getBody());
        return UUID.fromString(node.get("id").asText());
    }

    private ResponseEntity<String> uploadPdf(UUID projectId, String clientEmail, String filename, byte[] content, String areaM2) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.APPLICATION_PDF);
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        multipart.add("file", new HttpEntity<>(resource, fileHeaders));

        HttpHeaders areaHeaders = new HttpHeaders();
        areaHeaders.setContentType(MediaType.TEXT_PLAIN);
        multipart.add("areaM2", new HttpEntity<>(areaM2, areaHeaders));

        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.set("X-Authenticated-User-Id", clientEmail);
        reqHeaders.set("X-Authenticated-User-Role", "CLIENT");

        return restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"),
                new HttpEntity<>(multipart, reqHeaders),
                String.class
        );
    }

    @Nested
    @DisplayName("1. Casos Bons (Happy Path)")
    class HappyPathTests {

        @Test
        @DisplayName("Deve registrar e listar em ordem cronologica toda a linha do tempo do projeto para Cliente e Admin")
        void deveRegistrarEListarLinhaDoTempoCompletaEmOrdemCronologica() throws Exception {
            // 1. Admin cria o projeto -> PROJETO_CRIADO
            UUID projectId = createProjectViaHttp("Expositora Alpha Ltda", "54.018.395/0001-89", CLIENT_EMAIL);

            // 2. Cliente envia o PDF inicial -> PDF_ENVIADO
            ResponseEntity<String> pdfRes1 = uploadPdf(projectId, CLIENT_EMAIL, "planta-v1.pdf",
                    "%PDF-1.7 planta inicial".getBytes(), "36.00");
            assertThat(pdfRes1.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 3. Admin reprova o PDF -> PDF_REPROVADO
            ResponseEntity<String> rejectRes = restTemplate.postForEntity(
                    url("/projects/admin/" + projectId + "/reject"),
                    new HttpEntity<>("{\"justification\":\"Recuo frontal fora da norma.\"}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(rejectRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 4. Cliente reenvia o PDF corrigido -> PDF_ENVIADO
            ResponseEntity<String> pdfRes2 = uploadPdf(projectId, CLIENT_EMAIL, "planta-v2.pdf",
                    "%PDF-1.7 planta corrigida".getBytes(), "40.00");
            assertThat(pdfRes2.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 5. Admin aprova o PDF -> PDF_APROVADO
            ResponseEntity<String> approveRes = restTemplate.postForEntity(
                    url("/projects/admin/" + projectId + "/approve"),
                    new HttpEntity<>("{\"approvedAreaM2\": 40.00}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(approveRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 6. Admin configura taxa variavel no projeto -> TAXAS_CONFIGURADAS
            Fee energyFee = feeRepository.saveAndFlush(
                    new Fee("Energia Elétrica", "Ponto de força", FeeType.VARIAVEL, null, null, null, null, null, "kVA"));
            ResponseEntity<String> configRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/fees/configuration"),
                    HttpMethod.PUT,
                    new HttpEntity<>("{\"feeIds\":[\"" + energyFee.getId() + "\"]}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(configRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            UUID projectFeeId = UUID.fromString(objectMapper.readTree(configRes.getBody()).get(0).get("projectFeeId").asText());

            // 7. Cliente informa quantidade da taxa variavel -> QUANTIDADE_INFORMADA
            ResponseEntity<String> qtyRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/fees/" + projectFeeId + "/quantity"),
                    HttpMethod.PUT,
                    new HttpEntity<>("{\"quantity\": 15.50}", headers(CLIENT_EMAIL, "CLIENT")),
                    String.class
            );
            assertThat(qtyRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 8. Admin cota a taxa variavel -> TAXA_COTADA
            ResponseEntity<String> quoteRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/fees/" + projectFeeId + "/quote"),
                    HttpMethod.PUT,
                    new HttpEntity<>("{\"amount\": 775.00}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(quoteRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Consulta pelo Cliente proprietario
            ResponseEntity<String> clientHistoryRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(CLIENT_EMAIL, "CLIENT")),
                    String.class
            );
            assertThat(clientHistoryRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode clientEvents = objectMapper.readTree(clientHistoryRes.getBody());
            assertThat(clientEvents.isArray()).isTrue();
            assertThat(clientEvents).hasSize(8);

            assertThat(clientEvents.get(0).get("eventType").asText()).isEqualTo("PROJETO_CRIADO");
            assertThat(clientEvents.get(0).get("actorId").asText()).isEqualTo(ADMIN_ID);
            assertThat(clientEvents.get(0).get("actorRole").asText()).isEqualTo("ADMIN");
            assertThat(clientEvents.get(0).get("description").asText()).contains("Expositora Alpha Ltda");
            assertThat(clientEvents.get(0).get("createdAt").asText()).isNotBlank();

            assertThat(clientEvents.get(1).get("eventType").asText()).isEqualTo("PDF_ENVIADO");
            assertThat(clientEvents.get(1).get("actorId").asText()).isEqualTo(CLIENT_EMAIL);
            assertThat(clientEvents.get(1).get("actorRole").asText()).isEqualTo("CLIENT");
            assertThat(clientEvents.get(1).get("description").asText()).contains("planta-v1.pdf");

            assertThat(clientEvents.get(2).get("eventType").asText()).isEqualTo("PDF_REPROVADO");
            assertThat(clientEvents.get(2).get("actorId").asText()).isEqualTo(ADMIN_ID);
            assertThat(clientEvents.get(2).get("description").asText()).contains("Recuo frontal fora da norma.");

            assertThat(clientEvents.get(3).get("eventType").asText()).isEqualTo("PDF_ENVIADO");
            assertThat(clientEvents.get(3).get("description").asText()).contains("planta-v2.pdf");

            assertThat(clientEvents.get(4).get("eventType").asText()).isEqualTo("PDF_APROVADO");
            assertThat(clientEvents.get(4).get("actorId").asText()).isEqualTo(ADMIN_ID);
            assertThat(clientEvents.get(4).get("description").asText()).contains("40.00");

            assertThat(clientEvents.get(5).get("eventType").asText()).isEqualTo("TAXAS_CONFIGURADAS");
            assertThat(clientEvents.get(5).get("actorId").asText()).isEqualTo(ADMIN_ID);

            assertThat(clientEvents.get(6).get("eventType").asText()).isEqualTo("QUANTIDADE_INFORMADA");
            assertThat(clientEvents.get(6).get("actorId").asText()).isEqualTo(CLIENT_EMAIL);
            assertThat(clientEvents.get(6).get("description").asText()).contains("Energia Elétrica").contains("15.50");

            assertThat(clientEvents.get(7).get("eventType").asText()).isEqualTo("TAXA_COTADA");
            assertThat(clientEvents.get(7).get("actorId").asText()).isEqualTo(ADMIN_ID);
            assertThat(clientEvents.get(7).get("description").asText()).contains("Energia Elétrica").contains("775.00");

            // Consulta pelo Administrador retorna exatamente a mesma linha do tempo
            ResponseEntity<String> adminHistoryRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("outro-admin", "ADMIN")),
                    String.class
            );
            assertThat(adminHistoryRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode adminEvents = objectMapper.readTree(adminHistoryRes.getBody());
            assertThat(adminEvents).hasSize(8);
        }

        @Test
        @DisplayName("Deve registrar e consultar eventos de pagamento (geracao, confirmacao e cancelamento)")
        void deveRegistrarEventosDePagamentoNaLinhaDoTempo() throws Exception {
            UUID projectId = createProjectViaHttp("Expositora Pagamento", "33.444.555/0001-66", CLIENT_EMAIL);

            historyService.recordPaymentGenerated(projectId, "Aluguel de Estande", new BigDecimal("1500.00"),
                    new Actor(CLIENT_EMAIL, ActorRole.CLIENT));
            historyService.recordPaymentCancelled(projectId, "Aluguel de Estande",
                    new Actor(ADMIN_ID, ActorRole.ADMIN));
            historyService.recordPaymentConfirmed(projectId, "Aluguel de Estande", new BigDecimal("1500.00"),
                    new Actor(ADMIN_ID, ActorRole.ADMIN));

            ResponseEntity<String> res = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(CLIENT_EMAIL, "CLIENT")),
                    String.class
            );

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode events = objectMapper.readTree(res.getBody());
            assertThat(events).hasSize(4);
            assertThat(events.get(0).get("eventType").asText()).isEqualTo("PROJETO_CRIADO");
            assertThat(events.get(1).get("eventType").asText()).isEqualTo("PAGAMENTO_GERADO");
            assertThat(events.get(2).get("eventType").asText()).isEqualTo("PAGAMENTO_CANCELADO");
            assertThat(events.get(3).get("eventType").asText()).isEqualTo("PAGAMENTO_CONFIRMADO");
        }
    }

    @Nested
    @DisplayName("2. Casos Ruins (Sad Path / Regras de Negócio)")
    class SadPathTests {

        @Test
        @DisplayName("Deve retornar 403 Forbidden quando um Cliente tenta acessar o historico de projeto de outro Cliente")
        void deveBloquearAcessoDeOutroClienteCom403() throws Exception {
            UUID projectId = createProjectViaHttp("Empresa Privada", "12.345.678/0001-99", CLIENT_EMAIL);

            ResponseEntity<JsonNode> res = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("intruso@exemplo.com", "CLIENT")),
                    JsonNode.class
            );

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(res.getBody()).isNotNull();
            assertThat(res.getBody().get("status").asInt()).isEqualTo(403);
        }

        @Test
        @DisplayName("Deve retornar 404 Not Found quando o projeto nao existe")
        void deveRetornar404QuandoProjetoNaoExiste() {
            ResponseEntity<JsonNode> res = restTemplate.exchange(
                    url("/projects/" + UUID.randomUUID() + "/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    JsonNode.class
            );

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(res.getBody()).isNotNull();
            assertThat(res.getBody().get("status").asInt()).isEqualTo(404);
        }

        @Test
        @DisplayName("Deve retornar 401 Unauthorized quando nao houver cabecalhos de autenticacao")
        void deveRetornar401SemAutenticacao() throws Exception {
            UUID projectId = createProjectViaHttp("Empresa Auth", "98.765.432/0001-10", CLIENT_EMAIL);

            ResponseEntity<JsonNode> res = restTemplate.getForEntity(
                    url("/projects/" + projectId + "/history"),
                    JsonNode.class
            );

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Operacoes que falham nao devem gravar eventos fantasmas no historico")
        void operacoesInvalidasNaoDevemGravarEventosNoHistorico() throws Exception {
            UUID projectId = createProjectViaHttp("Empresa Rollback", "11.222.333/0001-44", CLIENT_EMAIL);

            // Tentativa invalida de upload (conteudo nao e PDF)
            ResponseEntity<String> badPdf = uploadPdf(projectId, CLIENT_EMAIL, "falso.pdf",
                    "conteudo invalido".getBytes(), "25.00");
            assertThat(badPdf.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            // Tentativa invalida de aprovar sem estar em analise
            ResponseEntity<String> badApprove = restTemplate.postForEntity(
                    url("/projects/admin/" + projectId + "/approve"),
                    new HttpEntity<>("{\"approvedAreaM2\": 25.00}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(badApprove.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

            // Historico deve conter apenas o evento inicial PROJETO_CRIADO
            ResponseEntity<String> historyRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(historyRes.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode events = objectMapper.readTree(historyRes.getBody());
            assertThat(events).hasSize(1);
            assertThat(events.get(0).get("eventType").asText()).isEqualTo("PROJETO_CRIADO");
        }
    }

    @Nested
    @DisplayName("3. Casos Incompletos e Somente Leitura (Boundary & Malformed Payloads)")
    class BoundaryAndImmutabilityTests {

        @Test
        @DisplayName("Deve retornar 400 Bad Request com RFC 7807 quando projectId na URL nao for UUID valido")
        void deveRetornar400QuandoProjectIdMalformado() {
            ResponseEntity<JsonNode> res = restTemplate.exchange(
                    url("/projects/id-invalido-nao-uuid/history"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    JsonNode.class
            );

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).isNotNull();
            assertThat(res.getBody().get("status").asInt()).isEqualTo(400);
        }

        @Test
        @DisplayName("Deve bloquear tentativas de escrita ou exclusao no historico com 405 Method Not Allowed")
        void deveGarantirQueHistoricoSejaEstritamenteSomenteLeitura() throws Exception {
            UUID projectId = createProjectViaHttp("Empresa Imutavel", "22.333.444/0001-55", CLIENT_EMAIL);

            ResponseEntity<JsonNode> postRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.POST,
                    new HttpEntity<>("{\"description\":\"adulteracao\"}", headers(ADMIN_ID, "ADMIN")),
                    JsonNode.class
            );
            assertThat(postRes.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

            ResponseEntity<JsonNode> putRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.PUT,
                    new HttpEntity<>("{\"description\":\"adulteracao\"}", headers(ADMIN_ID, "ADMIN")),
                    JsonNode.class
            );
            assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

            ResponseEntity<JsonNode> deleteRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/history"),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    JsonNode.class
            );
            assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }
    }
}
