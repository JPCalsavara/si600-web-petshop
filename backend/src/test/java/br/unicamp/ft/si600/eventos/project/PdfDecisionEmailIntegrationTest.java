package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US-08: aprovação/reprovação do PDF e e-mail de notificação ao cliente. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import({TestStorageConfig.class, TestEmailConfig.class})
class PdfDecisionEmailIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired ProjectRepository repository;
    @Autowired CapturingEmailSender emails;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        emails.clear();
        projectId = seedProjectInAnalysis("cliente@exemplo.com");
    }

    @Test
    void aprovarDeveMudarStatusENotificarOCliente() {
        ResponseEntity<String> response = approve("admin-7", "ADMIN", projectId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repository.findById(projectId).orElseThrow().getStatus()).isEqualTo(ProjectStatus.APROVADO);
        assertThat(emails.sent()).hasSize(1);
        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("cliente@exemplo.com");
        assertThat(mail.subject()).contains("aprovado", "Empresa Exemplo Ltda.");
        assertThat(mail.body()).contains("42.50");
    }

    @Test
    void reprovarDeveEnviarAJustificativaPorEmail() {
        ResponseEntity<String> response = reject("admin-7", "ADMIN", projectId,
                "{\"justification\":\"Altura acima do máximo permitido.\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emails.sent()).hasSize(1);
        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("cliente@exemplo.com");
        assertThat(mail.subject()).contains("reprovado");
        assertThat(mail.body()).contains("Altura acima do máximo permitido.", "novamente");
    }

    @Test
    void falhaNoEnvioDeEmailNaoDesfazAAprovacao() {
        emails.failing(true);

        ResponseEntity<String> response = approve("admin-7", "ADMIN", projectId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repository.findById(projectId).orElseThrow().getStatus()).isEqualTo(ProjectStatus.APROVADO);
    }

    @Test
    void projetoSemEmailDeContatoEDecididoSemEnviarEmail() {
        UUID legacy = seedProjectInAnalysis(null);

        ResponseEntity<String> response = approve("admin-7", "ADMIN", legacy);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void naoDeveEnviarEmailQuandoDecisaoForRecusada() {
        // justificativa em branco
        assertThat(reject("admin-7", "ADMIN", projectId, "{\"justification\":\"   \"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // corpo ausente / malformado
        assertThat(reject("admin-7", "ADMIN", projectId, "{nao e json").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reject("admin-7", "ADMIN", projectId, "{}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        // cliente não decide
        assertThat(approve("cliente@exemplo.com", "CLIENT", projectId).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // projeto inexistente
        assertThat(approve("admin-7", "ADMIN", UUID.randomUUID()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(emails.sent()).isEmpty();
        assertThat(repository.findById(projectId).orElseThrow().getStatus()).isEqualTo(ProjectStatus.PDF_EM_ANALISE);
    }

    @Test
    void decidirDuasVezesDeveEnviarApenasUmEmail() {
        assertThat(approve("admin-7", "ADMIN", projectId).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second = approve("admin-7", "ADMIN", projectId);
        ResponseEntity<String> rejectAfter = reject("admin-7", "ADMIN", projectId,
                "{\"justification\":\"Tarde demais.\"}");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rejectAfter.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(emails.sent()).hasSize(1);
    }

    @Test
    void naoDeveDecidirProjetoQueNaoEstaEmAnalise() {
        Project waiting = repository.save(Project.create("novo@exemplo.com", "Outra Ltda.",
                "11.222.333/0001-81", "11222333000181", "Maria", "B2C", "Rua Y, 1", "novo@exemplo.com",
                OffsetDateTime.now().plusDays(5), OffsetDateTime.now().plusDays(9), "admin-1", OffsetDateTime.now()));

        ResponseEntity<String> response = approve("admin-7", "ADMIN", waiting.getId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(emails.sent()).isEmpty();
    }

    private UUID seedProjectInAnalysis(String contactEmail) {
        String key = contactEmail == null ? "legacy" : contactEmail;
        Project project = Project.create(key, "Empresa Exemplo Ltda.", "54.018.395/0001-89",
                contactEmail == null ? null : "54018395000189", "Joãozinho da Silva", "B2C", "Rua X, 100",
                contactEmail, OffsetDateTime.now().plusDays(10), OffsetDateTime.now().plusDays(20),
                "admin-1", OffsetDateTime.now());
        project.submitPdf("projects/x/pdf/y.pdf", "planta.pdf",
                "application/pdf", 100L, OffsetDateTime.now());
        return repository.save(project).getId();
    }

    private ResponseEntity<String> approve(String actorId, String role, UUID id) {
        HttpHeaders headers = headers(actorId, role);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {
                "approvedAreaM2": 50.00
            }
            """;
        return restTemplate.postForEntity(url("/projects/admin/" + id + "/approve"),
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> reject(String actorId, String role, UUID id, String json) {
        HttpHeaders headers = headers(actorId, role);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url("/projects/admin/" + id + "/reject"),
                new HttpEntity<>(json, headers), String.class);
    }

    private HttpHeaders headers(String id, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Authenticated-User-Id", id);
        headers.set("X-Authenticated-User-Role", role);
        return headers;
    }

    private String url(String path) { return "http://localhost:" + port + "/api" + path; }
}
