package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.dto.AlertTag;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
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
import org.springframework.http.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import({TestStorageConfig.class, TestClockConfig.class})
class ProjectAlertTagIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    // Baseado no TestClockConfig: Instant fixo 2026-10-20T12:00:00Z -> 2026-10-20T09:00:00-03:00
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.ofInstant(TestClockConfig.FIXED_INSTANT, ZONE);

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ProjectRepository repository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private HttpHeaders headers(String user, String role) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Authenticated-User-Id", user);
        h.set("X-Authenticated-User-Role", role);
        return h;
    }

    private static int docCounter = 1000;

    private Project createProject(String owner, String company, OffsetDateTime pdfDeadline,
                                  OffsetDateTime paymentDeadline, ProjectStatus status) {
        String docDigits = "54018395" + (docCounter++);
        String doc = "54.018.395/" + docDigits.substring(8);
        Project p = Project.create(owner, company, doc, docDigits,
                "Representante", "Categoria B2C", "Rua 1", owner, pdfDeadline, paymentDeadline,
                "admin-1", FIXED_NOW.minusDays(5));
        if (status == ProjectStatus.PDF_EM_ANALISE) {
            p.submitPdf("key", "planta.pdf", "application/pdf", 1024L, java.math.BigDecimal.valueOf(50), FIXED_NOW.minusDays(1));
        } else if (status == ProjectStatus.APROVADO) {
            p.approve("admin-1", FIXED_NOW.minusDays(1), java.math.BigDecimal.valueOf(50));
        } else if (status == ProjectStatus.REPROVADO) {
            p.reject("admin-1", FIXED_NOW.minusDays(1), "Planta fora dos padrões.");
        } else if (status == ProjectStatus.CONCLUIDO) {
            p.approve("admin-1", FIXED_NOW.minusDays(2), java.math.BigDecimal.valueOf(50));
            p.markConcluido();
        }
        return repository.save(p);
    }

    @Nested
    @DisplayName("1. Casos Bons (Happy Path)")
    class HappyPathTests {

        @Test
        @DisplayName("Deve retornar EM_DIA quando pdfDeadline for superior a 3 dias")
        void deveRetornarEmDiaQuandoPrazoSuperiorA3Dias() throws Exception {
            Project p = createProject("c1@teste.com", "Empresa A", FIXED_NOW.plusDays(10), null, ProjectStatus.AGUARDANDO_PDF);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.isArray()).isTrue();
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.EM_DIA.name());
        }

        @Test
        @DisplayName("Deve retornar PRAZO_PROXIMO quando pdfDeadline estiver entre 0 e 3 dias")
        void deveRetornarPrazoProximoQuandoPrazoMenorOuIgualA3Dias() throws Exception {
            Project p = createProject("c2@teste.com", "Empresa B", FIXED_NOW.plusDays(2), null, ProjectStatus.AGUARDANDO_PDF);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.PRAZO_PROXIMO.name());
        }

        @Test
        @DisplayName("Deve retornar ATRASADO quando pdfDeadline expirou sem envio do PDF")
        void deveRetornarAtrasadoQuandoPdfExpirado() throws Exception {
            Project p = createProject("c3@teste.com", "Empresa C", FIXED_NOW.minusDays(2), null, ProjectStatus.AGUARDANDO_PDF);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.ATRASADO.name());
        }

        @Test
        @DisplayName("Deve retornar PENDENTE quando PDF estiver em análise e prazos estiverem em dia")
        void deveRetornarPendenteQuandoPdfEmAnalise() throws Exception {
            Project p = createProject("c4@teste.com", "Empresa D", FIXED_NOW.plusDays(10), FIXED_NOW.plusDays(15), ProjectStatus.PDF_EM_ANALISE);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.PENDENTE.name());
        }

        @Test
        @DisplayName("Cliente deve consultar o próprio projeto contendo a alertTag correta")
        void clienteDeveConsultarProprioProjetoComAlertTag() throws Exception {
            Project p = createProject("cliente@teste.com", "Cliente Empresa", FIXED_NOW.plusDays(1), null, ProjectStatus.AGUARDANDO_PDF);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/" + p.getId()),
                    HttpMethod.GET, new HttpEntity<>(headers("cliente@teste.com", "CLIENT")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode json = objectMapper.readTree(response.getBody());
            assertThat(json.get("alertTag").asText()).isEqualTo(AlertTag.PRAZO_PROXIMO.name());
        }
    }

    @Nested
    @DisplayName("2. Casos de Precedência e Criticidade (Sad Path)")
    class PrecedenceTests {

        @Test
        @DisplayName("Deve prevalecer ATRASADO quando pagamento atrasado e PDF em análise")
        void devePrevalecerAtrasadoSobrePendente() throws Exception {
            Project p = createProject("c5@teste.com", "Empresa Conflito 1",
                    FIXED_NOW.plusDays(5), FIXED_NOW.minusDays(1), ProjectStatus.PDF_EM_ANALISE);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.ATRASADO.name());
        }

        @Test
        @DisplayName("Deve prevalecer PRAZO_PROXIMO sobre PENDENTE quando pagamento vence em 2 dias e PDF em análise")
        void devePrevalecerPrazoProximoSobrePendente() throws Exception {
            Project p = createProject("c6@teste.com", "Empresa Conflito 2",
                    FIXED_NOW.plusDays(10), FIXED_NOW.plusDays(2), ProjectStatus.PDF_EM_ANALISE);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.PRAZO_PROXIMO.name());
        }
    }

    @Nested
    @DisplayName("3. Casos Incompletos e Limites de Borda (Boundary)")
    class BoundaryTests {

        @Test
        @DisplayName("Projeto sem paymentDeadline deve calcular alertTag sem disparar NPE")
        void projetoSemPaymentDeadlineNaoDeveLancarExcecao() throws Exception {
            Project p = createProject("c7@teste.com", "Empresa Sem Pagamento",
                    FIXED_NOW.plusDays(8), null, ProjectStatus.AGUARDANDO_PDF);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.EM_DIA.name());
        }

        @Test
        @DisplayName("Projeto com status CONCLUIDO deve sempre retornar EM_DIA mesmo com datas passadas")
        void projetoConcluidoDeveRetornarEmDia() throws Exception {
            Project p = createProject("c8@teste.com", "Empresa Concluida",
                    FIXED_NOW.minusDays(20), FIXED_NOW.minusDays(10), ProjectStatus.CONCLUIDO);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.get(0).get("alertTag").asText()).isEqualTo(AlertTag.EM_DIA.name());
        }

        @Test
        @DisplayName("Limite de borda exato: 72h exatas retorna PRAZO_PROXIMO e 72h e 1s retorna EM_DIA")
        void limiteExatoDe72Horas() throws Exception {
            // Exatamente 3 dias (72h)
            createProject("c9@teste.com", "Empresa 72h", FIXED_NOW.plusDays(3), null, ProjectStatus.AGUARDANDO_PDF);
            // 3 dias e 1 segundo
            createProject("c10@teste.com", "Empresa 72h1s", FIXED_NOW.plusDays(3).plusSeconds(1), null, ProjectStatus.AGUARDANDO_PDF);

            ResponseEntity<String> response = restTemplate.exchange(url("/projects/admin"),
                    HttpMethod.GET, new HttpEntity<>(headers("admin-1", "ADMIN")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode array = objectMapper.readTree(response.getBody());
            assertThat(array.size()).isEqualTo(2);

            // Localiza cada projeto
            for (JsonNode item : array) {
                if (item.get("companyName").asText().equals("Empresa 72h")) {
                    assertThat(item.get("alertTag").asText()).isEqualTo(AlertTag.PRAZO_PROXIMO.name());
                } else if (item.get("companyName").asText().equals("Empresa 72h1s")) {
                    assertThat(item.get("alertTag").asText()).isEqualTo(AlertTag.EM_DIA.name());
                }
            }
        }
    }
}
