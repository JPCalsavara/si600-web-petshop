package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.storage.InMemoryObjectStorage;
import br.unicamp.ft.si600.eventos.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import(TestStorageConfig.class)
class ProjectPdfIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ProjectRepository repository;

    @Autowired
    ObjectStorage objectStorage;

    private UUID projectId;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // A metragem é definida pelo Admin na criação do projeto (BUG-04).
        Project project = Project.create(
                "client-1", "Empresa Exemplo Ltda.", "54.018.395/0001-89", "54018395000189",
                "Joãozinho Exemplo da Silva", "Categoria 4", "Rua X, 100", "client-1@exemplo.com",
                OffsetDateTime.now().plusDays(10), OffsetDateTime.now().plusDays(20),
                "admin-1", OffsetDateTime.now());
        project.defineBoothArea(new BigDecimal("42.50"));
        projectId = repository.save(project).getId();
    }

    @Test
    void deveEnviarPdfEColocarProjetoEmAnalise() {
        HttpEntity<MultiValueMap<String, Object>> request = multipartRequest("client-1", "CLIENT", "planta.pdf",
                "%PDF-1.7 fake pdf".getBytes(), "application/pdf");

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Project saved = repository.findById(projectId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.PDF_EM_ANALISE);
        assertThat(saved.getPdfUploadedAt()).isNotNull();
        assertThat(saved.getPdfObjectKey()).isNotBlank();
    }

    @Test
    void deveRejeitarPdfComConteudoQueNaoSejaPdf() {
        HttpEntity<MultiValueMap<String, Object>> request = multipartRequest("client-1", "CLIENT", "planta.pdf",
                "not a pdf".getBytes(), "application/pdf");

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(repository.findById(projectId).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.AGUARDANDO_PDF);
    }

    @Test
    void deveAprovarPdfERegistrarAuditoria() {
        submitValidPdf();

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/admin/" + projectId + "/approve"),
                new HttpEntity<>(actorHeaders("admin-7", "ADMIN")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Project saved = repository.findById(projectId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.APROVADO);
        assertThat(saved.getDecisionBy()).isEqualTo("admin-7");
        assertThat(saved.getDecisionAt()).isNotNull();
        assertThat(saved.getBoothAreaM2()).isEqualByComparingTo("42.50");
    }

    @Test
    void deveReprovarPdfComJustificativaEPermitirReenvio() {
        submitValidPdf();
        HttpHeaders headers = actorHeaders("admin-7", "ADMIN");
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> reject = restTemplate.postForEntity(
                url("/projects/admin/" + projectId + "/reject"),
                new HttpEntity<>("{\"justification\":\"Ajustar cotas e identificação das medidas.\"}", headers),
                String.class);

        assertThat(reject.getStatusCode()).isEqualTo(HttpStatus.OK);
        Project rejected = repository.findById(projectId).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ProjectStatus.REPROVADO);
        assertThat(rejected.getRejectionJustification()).contains("Ajustar cotas");

        HttpEntity<MultiValueMap<String, Object>> resend = multipartRequest("client-1", "CLIENT",
                "nova-planta.pdf", "%PDF-1.7 new".getBytes(), "application/pdf");
        ResponseEntity<String> resendResponse = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), resend, String.class);

        assertThat(resendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repository.findById(projectId).orElseThrow().getStatus())
                .isEqualTo(ProjectStatus.PDF_EM_ANALISE);
    }

    @Test
    void clienteNaoPodeAcessarProjetoDeOutroCliente() {
        HttpHeaders headers = actorHeaders("client-999", "CLIENT");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/projects/" + projectId), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminPodeFiltrarSomentePdfEmAnalise() {
        submitValidPdf();
        HttpHeaders headers = actorHeaders("admin-7", "ADMIN");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/projects/admin?status=PDF_EM_ANALISE"), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(projectId.toString());
    }

    @Test
    void deveRejeitarSemAutenticacao() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/projects/" + projectId), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rotaInexistenteDeveRetornar404EMetodoInvalido405() {
        HttpHeaders headers = actorHeaders("client-1", "CLIENT");
        ResponseEntity<String> notFound = restTemplate.exchange(
                url("/rota-inexistente"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<String> badMethod = restTemplate.exchange(
                url("/projects/" + projectId), HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        assertThat(badMethod.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void deveRejeitarPapelInvalidoComUnauthorized() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/projects/" + projectId), HttpMethod.GET,
                new HttpEntity<>(actorHeaders("client-1", "SUPERUSER")), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void deveTruncarNomeDeArquivoMuitoLongo() {
        // Acima de 255 para exercitar o truncamento, mas abaixo do limite de 512 bytes que o Tomcat impoe
        // aos cabecalhos de cada parte multipart (um nome de 400+ caracteres resulta em 413 antes de chegar ao servico).
        String longName = "a".repeat(300) + ".pdf";
        HttpEntity<MultiValueMap<String, Object>> request = multipartRequest("client-1", "CLIENT", longName,
                "%PDF-1.7 fake pdf".getBytes(), "application/pdf");

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String stored = repository.findById(projectId).orElseThrow().getPdfOriginalFilename();
        assertThat(stored).hasSize(255).endsWith(".pdf");
    }

    @Test
    void reenvioDeveRemoverObjetoAntigoAposCommit() {
        submitValidPdf();
        String oldKey = repository.findById(projectId).orElseThrow().getPdfObjectKey();

        HttpHeaders admin = actorHeaders("admin-7", "ADMIN");
        admin.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(url("/projects/admin/" + projectId + "/reject"),
                new HttpEntity<>("{\"justification\":\"Ajustar.\"}", admin), String.class);

        HttpEntity<MultiValueMap<String, Object>> resend = multipartRequest("client-1", "CLIENT",
                "nova.pdf", "%PDF-1.7 new".getBytes(), "application/pdf");
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), resend, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newKey = repository.findById(projectId).orElseThrow().getPdfObjectKey();
        InMemoryObjectStorage storage = (InMemoryObjectStorage) objectStorage;
        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(storage.contains(newKey)).isTrue();
        assertThat(storage.contains(oldKey)).isFalse();
    }

    @Test
    void adminDeveListarTodosOsStatusSemFiltro() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/projects/admin"), HttpMethod.GET,
                new HttpEntity<>(actorHeaders("admin-7", "ADMIN")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(projectId.toString());
    }

    @Test
    void uploadPdfDeveManterAMetragemDefinidaNaCriacao() {
        submitValidPdf();
        assertThat(repository.findById(projectId).orElseThrow().getBoothAreaM2())
                .isEqualByComparingTo("42.50");
    }

    @Test
    void uploadPdfDeveIgnorarMetragemEnviadaPeloCliente() {
        HttpEntity<MultiValueMap<String, Object>> request = multipartRequest("client-1", "CLIENT", "planta.pdf",
                "%PDF-1.7 fake pdf".getBytes(), "application/pdf");
        HttpHeaders areaHeaders = new HttpHeaders();
        areaHeaders.setContentType(MediaType.TEXT_PLAIN);
        request.getBody().add("areaM2", new HttpEntity<>("999.00", areaHeaders));

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repository.findById(projectId).orElseThrow().getBoothAreaM2())
                .isEqualByComparingTo("42.50");
    }

    private void submitValidPdf() {
        HttpEntity<MultiValueMap<String, Object>> request = multipartRequest("client-1", "CLIENT",
                "planta.pdf", "%PDF-1.7 fake pdf".getBytes(), "application/pdf");
        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"), request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartRequest(
            String id, String role, String filename, byte[] bytes, String contentType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        body.add("file", new HttpEntity<>(resource(bytes, filename), fileHeaders));

        HttpHeaders headers = actorHeaders(id, role);
        return new HttpEntity<>(body, headers);
    }

    private ByteArrayResource resource(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };
    }

    private HttpHeaders actorHeaders(String id, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Authenticated-User-Id", id);
        headers.set("X-Authenticated-User-Role", role);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
