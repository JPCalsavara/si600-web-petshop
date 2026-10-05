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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import(TestStorageConfig.class)
class ProjectCreationIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired ProjectRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void adminDeveCriarProjetoAguardandoPdfPersistidoNoBanco() {
        ResponseEntity<String> response = post("admin-1", "ADMIN", body("Empresa A", "54.018.395/0001-89",
                "a@exemplo.com", "2026-11-10", "2026-11-20"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Project saved = repository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.AGUARDANDO_PDF);
        assertThat(saved.getOwnerId()).isEqualTo("a@exemplo.com");
        assertThat(saved.getCreatedBy()).isEqualTo("admin-1");
        assertThat(saved.getPaymentDeadline()).isAfter(saved.getPdfDeadline());
    }

    @Test
    void clienteCriadoDeveConseguirConsultarOProprioProjeto() {
        post("admin-1", "ADMIN", body("Empresa A", "54.018.395/0001-89", "A@Exemplo.com", "2026-11-10", "2026-11-20"));
        Project saved = repository.findAll().get(0);

        HttpHeaders h = headers("a@exemplo.com", "CLIENT");
        ResponseEntity<String> response = restTemplate.exchange(url("/projects/" + saved.getId()),
                HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deveRejeitarEmailOuDocumentoDuplicado() {
        post("admin-1", "ADMIN", body("Empresa A", "54.018.395/0001-89", "a@exemplo.com", "2026-11-10", "2026-11-20"));

        ResponseEntity<String> sameDoc = post("admin-1", "ADMIN",
                body("Empresa B", "54018395000189", "b@exemplo.com", "2026-11-10", "2026-11-20"));
        ResponseEntity<String> sameEmail = post("admin-1", "ADMIN",
                body("Empresa C", "11.222.333/0001-81", "A@EXEMPLO.com", "2026-11-10", "2026-11-20"));

        assertThat(sameDoc.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(sameEmail.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deveRejeitarPagamentoAnteriorAoEnvioDoPdf() {
        ResponseEntity<String> response = post("admin-1", "ADMIN",
                body("Empresa A", "54.018.395/0001-89", "a@exemplo.com", "2026-11-20", "2026-11-10"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarClienteCriandoProjeto() {
        ResponseEntity<String> response = post("client-1", "CLIENT",
                body("Empresa A", "54.018.395/0001-89", "a@exemplo.com", "2026-11-10", "2026-11-20"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveRejeitarDocumentoComTamanhoInvalido() {
        ResponseEntity<String> response = post("admin-1", "ADMIN",
                body("Empresa A", "123", "a@exemplo.com", "2026-11-10", "2026-11-20"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deveRejeitarCamposObrigatoriosAusentesEJsonMalformado() {
        ResponseEntity<String> missing = post("admin-1", "ADMIN", "{\"name\":\"Empresa A\"}");
        ResponseEntity<String> malformed = post("admin-1", "ADMIN", "{nao e json");
        ResponseEntity<String> badArea = post("admin-1", "ADMIN",
                body("Empresa A", "54.018.395/0001-89", "a@exemplo.com", "2026-11-10", "2026-11-20")
                        .replace("B2C", "Outra"));
        ResponseEntity<String> badDate = post("admin-1", "ADMIN",
                body("Empresa A", "54.018.395/0001-89", "a@exemplo.com", "10/11/2026", "2026-11-20"));

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badArea.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveExigirAutenticacao() {
        ResponseEntity<String> response = restTemplate.postForEntity(url("/projects"),
                new HttpEntity<>(body("Empresa A", "54.018.395/0001-89", "a@exemplo.com", "2026-11-10", "2026-11-20"),
                        jsonHeaders(new HttpHeaders())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String body(String name, String doc, String email, String pdf, String payment) {
        return "{\"name\":\"" + name + "\",\"document\":\"" + doc + "\",\"address\":\"Rua X, 100\","
                + "\"area\":\"B2C\",\"email\":\"" + email + "\",\"pdfDeadline\":\"" + pdf
                + "\",\"paymentDeadline\":\"" + payment + "\"}";
    }

    private ResponseEntity<String> post(String id, String role, String json) {
        return restTemplate.postForEntity(url("/projects"),
                new HttpEntity<>(json, jsonHeaders(headers(id, role))), String.class);
    }

    private HttpHeaders jsonHeaders(HttpHeaders h) {
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders headers(String id, String role) {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Authenticated-User-Id", id);
        h.set("X-Authenticated-User-Role", role);
        return h;
    }

    private String url(String path) { return "http://localhost:" + port + "/api" + path; }
}
