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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import({TestStorageConfig.class, TestClockConfig.class})
class AdminDashboardIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
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

    private static int docCounter = 5000;

    private Project createProject(String owner, String company, OffsetDateTime pdfDeadline,
                                  OffsetDateTime paymentDeadline, ProjectStatus status,
                                  OffsetDateTime createdAt, OffsetDateTime lastAccessAt) {
        String docDigits = "54018395" + (docCounter++);
        String doc = "54.018.395/" + docDigits.substring(8);
        Project p = Project.create(owner, company, doc, docDigits,
                "Representante", "Categoria B2C", "Rua 1", owner, pdfDeadline, paymentDeadline,
                "admin-1", createdAt != null ? createdAt : FIXED_NOW.minusDays(5));
        if (lastAccessAt != null) {
            p.recordAccess(lastAccessAt);
        }
        if (status == ProjectStatus.PDF_EM_ANALISE) {
            p.submitPdf("key", "planta_estande.pdf", "application/pdf", 2048L, BigDecimal.valueOf(60), FIXED_NOW.minusDays(1));
        } else if (status == ProjectStatus.APROVADO) {
            p.approve("admin-1", FIXED_NOW.minusDays(1), BigDecimal.valueOf(60));
        } else if (status == ProjectStatus.REPROVADO) {
            p.reject("admin-1", FIXED_NOW.minusDays(1), "Planta fora dos padrões.");
        } else if (status == ProjectStatus.CONCLUIDO) {
            p.approve("admin-1", FIXED_NOW.minusDays(2), BigDecimal.valueOf(60));
            p.markConcluido();
        }
        return repository.save(p);
    }

    @Nested
    @DisplayName("1. Casos Bons (Happy Path)")
    class HappyPathTests {

        @Test
        @DisplayName("Deve retornar 200 OK com os três painéis preenchidos no Dashboard do Admin")
        void deveRetornarDashboardCompletoParaAdmin() throws Exception {
            // Projeto 1: Nunca acessou (lastAccessAt null), criado há 10 dias, com prazo de PDF vencido
            Project p1 = createProject("cliente1@teste.com", "Empresa Alpha",
                    FIXED_NOW.minusDays(1), null, ProjectStatus.AGUARDANDO_PDF,
                    FIXED_NOW.minusDays(10), null);

            // Projeto 2: Acessou há 3 dias, criado há 8 dias, com PDF submetido em análise
            Project p2 = createProject("cliente2@teste.com", "Empresa Beta",
                    FIXED_NOW.plusDays(5), null, ProjectStatus.PDF_EM_ANALISE,
                    FIXED_NOW.minusDays(8), FIXED_NOW.minusDays(3));

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/admin/dashboard"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("admin-1", "ADMIN")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = objectMapper.readTree(response.getBody());

            // 1. Painel: Expositores Inativos
            JsonNode inactiveList = body.get("inactiveExhibitors");
            assertThat(inactiveList.isArray()).isTrue();
            assertThat(inactiveList.size()).isEqualTo(2);

            // Verifica o expositor que nunca acessou
            JsonNode alphaInactive = null;
            JsonNode betaInactive = null;
            for (JsonNode item : inactiveList) {
                if (item.get("companyName").asText().equals("Empresa Alpha")) {
                    alphaInactive = item;
                } else if (item.get("companyName").asText().equals("Empresa Beta")) {
                    betaInactive = item;
                }
            }
            assertThat(alphaInactive).isNotNull();
            assertThat(alphaInactive.get("neverAccessed").asBoolean()).isTrue();
            assertThat(alphaInactive.get("daysSinceLastAccess").isNull()).isTrue();
            assertThat(alphaInactive.get("createdAt")).isNotNull();

            assertThat(betaInactive).isNotNull();
            assertThat(betaInactive.get("neverAccessed").asBoolean()).isFalse();
            assertThat(betaInactive.get("daysSinceLastAccess").asLong()).isEqualTo(3);

            // 2. Painel: Projetos com Pendências e Prazos (reaproveitando lógica US-09)
            JsonNode pendingDeadlines = body.get("pendingDeadlines");
            assertThat(pendingDeadlines.isArray()).isTrue();
            assertThat(pendingDeadlines.size()).isEqualTo(2);

            // 3. Painel: Aprovações Pendentes (apenas status PDF_EM_ANALISE)
            JsonNode pendingApprovals = body.get("pendingApprovals");
            assertThat(pendingApprovals.isArray()).isTrue();
            assertThat(pendingApprovals.size()).isEqualTo(1);
            assertThat(pendingApprovals.get(0).get("companyName").asText()).isEqualTo("Empresa Beta");
            assertThat(pendingApprovals.get(0).get("pdfOriginalFilename").asText()).isEqualTo("planta_estande.pdf");
            assertThat(pendingApprovals.get(0).get("status").asText()).isEqualTo(ProjectStatus.PDF_EM_ANALISE.name());
        }

        @Test
        @DisplayName("Deve registrar e atualizar lastAccessAt quando o cliente consultar seu projeto")
        void deveRegistrarAcessoDoClienteAoConsultarProjeto() throws Exception {
            Project p = createProject("cliente@teste.com", "Empresa Cliente",
                    FIXED_NOW.plusDays(5), null, ProjectStatus.AGUARDANDO_PDF,
                    FIXED_NOW.minusDays(5), null);
            assertThat(p.getLastAccessAt()).isNull();

            // Cliente consulta seu projeto
            ResponseEntity<String> getResponse = restTemplate.exchange(
                    url("/projects/" + p.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("cliente@teste.com", "CLIENT")),
                    String.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Verifica no banco de dados se lastAccessAt foi persistido
            Project updated = repository.findById(p.getId()).orElseThrow();
            assertThat(updated.getLastAccessAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("2. Casos Ruins (Sad Path / Regras de Negócio)")
    class SadPathTests {

        @Test
        @DisplayName("Cliente comum tentando acessar dashboard deve receber 403 Forbidden")
        void clienteNaoPodeAcessarDashboard() {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/admin/dashboard"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("cliente@teste.com", "CLIENT")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Requisição anônima sem credenciais deve receber 401 Unauthorized")
        void anonimoNaoPodeAcessarDashboard() {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/admin/dashboard"),
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("3. Casos Incompletos e Limites de Borda (Boundary)")
    class BoundaryTests {

        @Test
        @DisplayName("Base sem projetos deve retornar 200 OK com listas vazias no dashboard")
        void baseSemProjetosDeveRetornarListasVazias() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/admin/dashboard"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("admin-1", "ADMIN")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = objectMapper.readTree(response.getBody());

            assertThat(body.get("inactiveExhibitors").size()).isZero();
            assertThat(body.get("pendingDeadlines").size()).isZero();
            assertThat(body.get("pendingApprovals").size()).isZero();
        }

        @Test
        @DisplayName("Projeto acessado no mesmo instante deve retornar 0 dias de inatividade")
        void projetoAcessadoHojeRetornaZeroDias() throws Exception {
            createProject("cliente-recente@teste.com", "Empresa Recente",
                    FIXED_NOW.plusDays(5), null, ProjectStatus.AGUARDANDO_PDF,
                    FIXED_NOW.minusDays(1), FIXED_NOW.minusHours(2));

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/admin/dashboard"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("admin-1", "ADMIN")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = objectMapper.readTree(response.getBody());
            JsonNode inactives = body.get("inactiveExhibitors");
            assertThat(inactives.size()).isEqualTo(1);
            assertThat(inactives.get(0).get("neverAccessed").asBoolean()).isFalse();
            assertThat(inactives.get(0).get("daysSinceLastAccess").asLong()).isEqualTo(0);
        }
    }
}
