package br.unicamp.ft.si600.eventos.fee;

import br.unicamp.ft.si600.eventos.entity.*;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US-06: Cliente informa e altera a quantidade das taxas variáveis. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectFeeQuantityIntegrationTest {
    private static final String CLIENT = "client-us06";

    @Autowired TestRestTemplate restTemplate;
    @Autowired ProjectRepository projects;
    @Autowired FeeRepository fees;
    @Autowired ProjectFeeRepository projectFees;
    @Autowired JdbcTemplate jdbc;

    Project project;
    ProjectFee energy;
    ProjectFee rent;

    @BeforeEach
    void setUp() {
        cleanUp();
        project = approvedProject(CLIENT);
        Fee energyFee = fees.save(new Fee("Energia", null, FeeType.VARIAVEL, null, null, null, null, null, "kVA"));
        Fee rentFee = fees.save(new Fee("Aluguel", null, FeeType.FIXA, null,
                new BigDecimal("100.00"), null, null, null, null));
        energy = projectFees.save(new ProjectFee(project, energyFee));
        rent = projectFees.save(new ProjectFee(project, rentFee));
    }

    @AfterEach
    void cleanUp() {
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
    }

    // ---- Casos bons ----

    @Test
    void deveGravarQuantidadeEMoverTaxaParaAguardandoCotacao() {
        assertThat(energy.getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_QUANTIDADE);

        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(), "{\"quantity\":12.5}", CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("AGUARDANDO_COTACAO");
        assertThat(response.getBody().get("quantity").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(response.getBody().get("measurementUnit").asText()).isEqualTo("kVA");
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuantity()).isEqualByComparingTo("12.50");
        assertThat(saved.getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_COTACAO);
        assertThat(saved.getQuantityUpdatedAt()).isNotNull();
    }

    @Test
    void devePermitirAlterarQuantidadeEnquantoAguardaCotacao() {
        putQuantity(project.getId(), energy.getId(), "{\"quantity\":10}", CLIENT, "CLIENT");

        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(), "{\"quantity\":20}", CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuantity()).isEqualByComparingTo("20");
        assertThat(saved.getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_COTACAO);
    }

    @Test
    void alteracaoAposCotacaoDeveVoltarTaxaParaAguardandoCotacao() {
        putQuantity(project.getId(), energy.getId(), "{\"quantity\":10}", CLIENT, "CLIENT");
        jdbc.update("update project_fees set status = 'COTADA' where id = ?", energy.getId());

        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(), "{\"quantity\":15}", CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status").asText()).isEqualTo("AGUARDANDO_COTACAO");
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("15");
    }

    // ---- Casos ruins ----

    @Test
    void deveBloquearAlteracaoComPagamentoGerado() {
        putQuantity(project.getId(), energy.getId(), "{\"quantity\":10}", CLIENT, "CLIENT");
        ProjectFee locked = projectFees.findById(energy.getId()).orElseThrow();
        jdbc.update("update project_fees set status = 'PAGAMENTO_GERADO' where id = ?", locked.getId());

        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(), "{\"quantity\":99}", CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.CONFLICT);
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuantity()).isEqualByComparingTo("10");
        assertThat(saved.getStatus()).isEqualTo(ProjectFeeStatus.PAGAMENTO_GERADO);
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = {"AGUARDANDO_PDF", "PDF_EM_ANALISE", "REPROVADO", "CONCLUIDO"})
    void deveRejeitarQuantidadeQuandoPdfNaoEstaAprovado(ProjectStatus status) {
        jdbc.update("update projects set status = ? where id = ?", status.name(), project.getId());

        assertProblem(putQuantity(project.getId(), energy.getId(), "{\"quantity\":5}", CLIENT, "CLIENT"),
                HttpStatus.UNPROCESSABLE_ENTITY);
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuantity()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_QUANTIDADE);
    }

    @Test
    void deveRejeitarQuantidadeEmTaxaNaoVariavel() {
        ResponseEntity<JsonNode> response = putQuantity(project.getId(), rent.getId(), "{\"quantity\":3}", CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getQuantity()).isNull();
    }

    @Test
    void naoDevePermitirAcessoAProjetoDeOutroCliente() {
        assertProblem(putQuantity(project.getId(), energy.getId(), "{\"quantity\":5}", "outro-cliente", "CLIENT"),
                HttpStatus.NOT_FOUND);
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuantity()).isNull();
    }

    @Test
    void naoDeveAlterarTaxaQuePertenceAOutroProjeto() {
        Project other = approvedProject("outro-cliente");
        ProjectFee otherFee = projectFees.save(new ProjectFee(other, energy.getFee()));

        assertProblem(putQuantity(project.getId(), otherFee.getId(), "{\"quantity\":5}", CLIENT, "CLIENT"),
                HttpStatus.NOT_FOUND);
        assertThat(projectFees.findById(otherFee.getId()).orElseThrow().getQuantity()).isNull();
    }

    @Test
    void deveRetornar404ParaIdsInexistentes() {
        assertProblem(putQuantity(UUID.randomUUID(), energy.getId(), "{\"quantity\":5}", CLIENT, "CLIENT"),
                HttpStatus.NOT_FOUND);
        assertProblem(putQuantity(project.getId(), UUID.randomUUID(), "{\"quantity\":5}", CLIENT, "CLIENT"),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void deveExigirPerfilClienteEAutenticacao() {
        assertProblem(putQuantity(project.getId(), energy.getId(), "{\"quantity\":5}", "admin-1", "ADMIN"),
                HttpStatus.FORBIDDEN);
        assertProblem(putQuantity(project.getId(), energy.getId(), "{\"quantity\":5}", null, null),
                HttpStatus.UNAUTHORIZED);
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuantity()).isNull();
    }

    // ---- Casos incompletos ----

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"quantity\":null}", "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":-0.01}",
            "{\"quantity\":\"abc\"}", "{\"quantity\":\"\"}", "{\"quantity\":{}}", "{\"quantity\":[]}",
            "{\"quantity\":true}", "{\"quantity\":0.001}", "{\"quantity\":1.234}",
            "{\"quantity\":12345678901}", "{\"quantidade\":5}", "null", "{", ""
    })
    void deveRejeitarPayloadsInvalidosComBadRequestSemGravar(String body) {
        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(), body, CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.BAD_REQUEST);
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuantity()).isNull();
        assertThat(saved.getStatus()).isEqualTo(ProjectFeeStatus.AGUARDANDO_QUANTIDADE);
    }

    @Test
    void deveInformarCampoInvalidoNaMatrizDeErros() {
        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(), "{\"quantity\":0}", CLIENT, "CLIENT");

        assertProblem(response, HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("invalidFields").has("quantity")).isTrue();
    }

    @Test
    void deveAceitarLimiteSuperiorDeQuantidade() {
        ResponseEntity<JsonNode> response = putQuantity(project.getId(), energy.getId(),
                "{\"quantity\":9999999999.99}", CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void deveRejeitarIdMalformado() {
        assertProblem(restTemplate.exchange("/projects/" + project.getId() + "/fees/invalido/quantity", HttpMethod.PUT,
                new HttpEntity<>("{\"quantity\":5}", headers(CLIENT, "CLIENT")), JsonNode.class), HttpStatus.BAD_REQUEST);
    }

    // ---- Auxiliares ----

    private Project approvedProject(String ownerId) {
        Project p = new Project(ownerId, "Empresa " + ownerId, "54.018.395/0001-89", "Representante",
                "B2C", OffsetDateTime.now().plusDays(10));
        p.approve("admin-1", OffsetDateTime.now());
        return projects.save(p);
    }

    private ResponseEntity<JsonNode> putQuantity(UUID projectId, UUID projectFeeId, String body, String user, String role) {
        return restTemplate.exchange("/projects/" + projectId + "/fees/" + projectFeeId + "/quantity",
                HttpMethod.PUT, new HttpEntity<>(body, headers(user, role)), JsonNode.class);
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
