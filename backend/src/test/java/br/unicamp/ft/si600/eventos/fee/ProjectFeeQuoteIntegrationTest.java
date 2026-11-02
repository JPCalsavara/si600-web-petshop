package br.unicamp.ft.si600.eventos.fee;

import br.unicamp.ft.si600.eventos.entity.*;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US-07: cotação das taxas pelo Admin e visualização do valor pelo Cliente (US-05). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectFeeQuoteIntegrationTest {
    private static final String CLIENT = "client-us07";
    private static final String ADMIN = "admin-us07";

    @Autowired TestRestTemplate restTemplate;
    @Autowired ProjectRepository projects;
    @Autowired FeeRepository fees;
    @Autowired ProjectFeeRepository projectFees;
    @Autowired JdbcTemplate jdbc;

    Project project;
    ProjectFee energy;
    ProjectFee rent;
    ProjectFee extinguisher;
    ProjectFee tfe;

    @BeforeEach
    void setUp() {
        cleanUp();
        project = approvedProject(CLIENT);
        energy = projectFees.save(new ProjectFee(project, fees.save(
                new Fee("Energia", null, FeeType.VARIAVEL, null, null, null, null, null, "kVA"))));
        rent = projectFees.save(new ProjectFee(project, fees.save(new Fee("Aluguel", null, FeeType.POR_METRAGEM,
                AreaPricingMode.VALOR_POR_M2, null, new BigDecimal("10.00"), null, null, null))));
        extinguisher = projectFees.save(new ProjectFee(project, fees.save(new Fee("Extintor", null,
                FeeType.POR_METRAGEM, AreaPricingMode.UNIDADES_POR_INTERVALO, null, null,
                new BigDecimal("25.00"), new BigDecimal("80.00"), null))));
        tfe = projectFees.save(new ProjectFee(project, fees.save(new Fee("TFE", null, FeeType.FIXA, null,
                new BigDecimal("100.00"), null, null, null, null))));
    }

    @AfterEach
    void cleanUp() {
        projectFees.deleteAll();
        projects.deleteAll();
        fees.deleteAll();
    }

    // ---- Casos bons ----

    @Test
    void listaCotacoesPendentesComClienteTaxaEQuantidade() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":12.5}", CLIENT, "CLIENT");

        ResponseEntity<JsonNode> response = send(HttpMethod.GET, "/admin/quotes", null, ADMIN, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isEqualTo(1);
        JsonNode item = response.getBody().get(0);
        assertThat(item.get("projectFeeId").asText()).isEqualTo(energy.getId().toString());
        assertThat(item.get("projectName").asText()).isEqualTo(project.getCompanyName());
        assertThat(item.get("clientEmail").asText()).isEqualTo(project.getContactEmail());
        assertThat(item.get("feeName").asText()).isEqualTo("Energia");
        assertThat(item.get("quantity").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(item.get("measurementUnit").asText()).isEqualTo("kVA");
        assertThat(item.get("status").asText()).isEqualTo("AGUARDANDO_COTACAO");
    }

    @Test
    void naoListaTaxaVariavelSemQuantidadeNemTaxasComValorCalculado() {
        ResponseEntity<JsonNode> response = send(HttpMethod.GET, "/admin/quotes", null, ADMIN, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isZero();
    }

    @Test
    void adminCotaTaxaRegistraAutorEDataEClienteVeOValor() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");

        ResponseEntity<JsonNode> quote = send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":250.50}", ADMIN, "ADMIN");

        assertThat(quote.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(quote.getBody().get("status").asText()).isEqualTo("COTADA");
        assertThat(quote.getBody().get("quotedAmount").decimalValue()).isEqualByComparingTo("250.50");
        assertThat(quote.getBody().get("quotedBy").asText()).isEqualTo(ADMIN);
        assertThat(quote.getBody().get("quotedAt").asText()).isNotBlank();

        JsonNode line = feeLine(energy.getId());
        assertThat(line.get("status").asText()).isEqualTo("COTADA");
        assertThat(line.get("calculatedValue").decimalValue()).isEqualByComparingTo("250.50");
        assertThat(line.get("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(line.get("quantityEditable").asBoolean()).isTrue();
    }

    @Test
    void adminPodeRecotarEnquantoNaoHaPagamento() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");
        send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":250}", ADMIN, "ADMIN");

        ResponseEntity<JsonNode> second = send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":300}", "admin-2", "ADMIN");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuotedAmount()).isEqualByComparingTo("300.00");
        assertThat(saved.getQuotedBy()).isEqualTo("admin-2");
        // Taxa cotada continua na lista para permitir nova recotação.
        assertThat(send(HttpMethod.GET, "/admin/quotes", null, ADMIN, "ADMIN").getBody().size()).isEqualTo(1);
    }

    @Test
    void alterarQuantidadeDepoisDaCotacaoEscondeValorAntigoDoCliente() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");
        send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":250}", ADMIN, "ADMIN");

        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":15}", CLIENT, "CLIENT");

        JsonNode line = feeLine(energy.getId());
        assertThat(line.get("status").asText()).isEqualTo("AGUARDANDO_COTACAO");
        assertThat(line.get("calculatedValue").isNull()).isTrue();
        assertThat(line.get("quantity").decimalValue()).isEqualByComparingTo("15");
        ProjectFee saved = projectFees.findById(energy.getId()).orElseThrow();
        assertThat(saved.getQuotedAmount()).isNull();
        assertThat(saved.getQuotedBy()).isNull();
    }

    @Test
    void clienteVeValorCalculadoDasTaxasFixasEPorMetragem() {
        ResponseEntity<JsonNode> response = send(HttpMethod.GET, "/projects/" + project.getId() + "/fees", null, CLIENT, "CLIENT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().size()).isEqualTo(4);
        JsonNode rentLine = feeLine(rent.getId());
        assertThat(rentLine.get("projectFeeId").asText()).isEqualTo(rent.getId().toString());
        assertThat(rentLine.get("feeId").asText()).isEqualTo(rent.getFee().getId().toString());
        assertThat(rentLine.get("status").asText()).isEqualTo("DISPONIVEL");
        assertThat(rentLine.get("calculatedValue").decimalValue()).isEqualByComparingTo("500.00"); // 50 m² x R$ 10
        assertThat(rentLine.get("quantityEditable").asBoolean()).isFalse();
        // Extintor: 1 unidade a cada 25 m² em estande de 50 m² = 2 unidades.
        JsonNode extLine = feeLine(extinguisher.getId());
        assertThat(extLine.get("quantity").decimalValue()).isEqualByComparingTo("2");
        assertThat(extLine.get("calculatedValue").decimalValue()).isEqualByComparingTo("160.00");
        assertThat(feeLine(tfe.getId()).get("calculatedValue").decimalValue()).isEqualByComparingTo("100.00");
        // Variável ainda sem quantidade: campo editável e sem valor.
        JsonNode energyLine = feeLine(energy.getId());
        assertThat(energyLine.get("status").asText()).isEqualTo("AGUARDANDO_QUANTIDADE");
        assertThat(energyLine.get("calculatedValue").isNull()).isTrue();
        assertThat(energyLine.get("quantityEditable").asBoolean()).isTrue();
        assertThat(energyLine.get("measurementUnit").asText()).isEqualTo("kVA");
        assertThat(energyLine.get("paymentDeadline").isNull()).isFalse();
    }

    @Test
    void linhaGravadaComoAguardandoCotacaoParaTaxaFixaAparecoComoDisponivel() {
        jdbc.update("update project_fees set status = 'AGUARDANDO_COTACAO' where id = ?", tfe.getId());

        assertThat(feeLine(tfe.getId()).get("status").asText()).isEqualTo("DISPONIVEL");
    }

    // ---- Casos ruins ----

    @Test
    void naoDeveCotarTaxaComValorCalculado() {
        ResponseEntity<JsonNode> response = send(HttpMethod.PUT, quoteUrl(rent), "{\"amount\":999}", ADMIN, "ADMIN");

        assertProblem(response, HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(projectFees.findById(rent.getId()).orElseThrow().getQuotedAmount()).isNull();
    }

    @Test
    void naoDeveCotarSemQuantidadeInformada() {
        assertProblem(send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":100}", ADMIN, "ADMIN"),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void naoDeveRecotarAposGeracaoDoPagamento() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");
        send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":250}", ADMIN, "ADMIN");
        jdbc.update("update project_fees set status = 'PAGAMENTO_GERADO' where id = ?", energy.getId());

        assertProblem(send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":300}", ADMIN, "ADMIN"), HttpStatus.CONFLICT);
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuotedAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void somenteAdminCotaEListaCotacoes() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");

        assertProblem(send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":250}", CLIENT, "CLIENT"), HttpStatus.FORBIDDEN);
        assertProblem(send(HttpMethod.GET, "/admin/quotes", null, CLIENT, "CLIENT"), HttpStatus.FORBIDDEN);
        assertProblem(send(HttpMethod.GET, "/admin/quotes", null, null, null), HttpStatus.UNAUTHORIZED);
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuotedAmount()).isNull();
    }

    @Test
    void naoDeveCotarComPdfNaoAprovado() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");
        jdbc.update("update projects set status = 'REPROVADO' where id = ?", project.getId());

        assertProblem(send(HttpMethod.PUT, quoteUrl(energy), "{\"amount\":250}", ADMIN, "ADMIN"),
                HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(send(HttpMethod.GET, "/admin/quotes", null, ADMIN, "ADMIN").getBody().size()).isZero();
    }

    @Test
    void clienteNaoVeTaxasComPdfNaoAprovado() {
        jdbc.update("update projects set status = 'PDF_EM_ANALISE' where id = ?", project.getId());

        assertProblem(send(HttpMethod.GET, "/projects/" + project.getId() + "/fees", null, CLIENT, "CLIENT"),
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void clienteNaoVeTaxasDeOutroProjeto() {
        assertProblem(send(HttpMethod.GET, "/projects/" + project.getId() + "/fees", null, "outro-cliente", "CLIENT"),
                HttpStatus.NOT_FOUND);
    }

    // ---- Casos incompletos ----

    @Test
    void deveRejeitarValoresDeCotacaoInvalidos() {
        send(HttpMethod.PUT, quantityUrl(energy), "{\"quantity\":10}", CLIENT, "CLIENT");

        for (String body : new String[]{"{}", "{\"amount\":null}", "{\"amount\":0}", "{\"amount\":-5}",
                "{\"amount\":\"abc\"}", "{\"amount\":10.999}", "{\"amount\":12345678901}", "{"}) {
            assertProblem(send(HttpMethod.PUT, quoteUrl(energy), body, ADMIN, "ADMIN"), HttpStatus.BAD_REQUEST);
        }
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getQuotedAmount()).isNull();
        assertThat(projectFees.findById(energy.getId()).orElseThrow().getStatus())
                .isEqualTo(ProjectFeeStatus.AGUARDANDO_COTACAO);
    }

    @Test
    void deveRetornar404ParaTaxaOuProjetoInexistente() {
        assertProblem(send(HttpMethod.PUT, "/projects/" + project.getId() + "/fees/" + UUID.randomUUID() + "/quote",
                "{\"amount\":10}", ADMIN, "ADMIN"), HttpStatus.NOT_FOUND);
        assertProblem(send(HttpMethod.PUT, "/projects/" + UUID.randomUUID() + "/fees/" + energy.getId() + "/quote",
                "{\"amount\":10}", ADMIN, "ADMIN"), HttpStatus.NOT_FOUND);
    }

    // ---- Auxiliares ----

    private Project approvedProject(String ownerId) {
        Project p = Project.create(ownerId, "Empresa " + ownerId, "54.018.395/0001-89", "54018395000189",
                "Representante", "B2C", "Rua A, 10", ownerId + "@exemplo.com",
                OffsetDateTime.now().plusDays(10), OffsetDateTime.now().plusDays(20), "admin-1", OffsetDateTime.now());
        p.approve("admin-1", OffsetDateTime.now(), new BigDecimal("50.00"));
        return projects.save(p);
    }

    private JsonNode feeLine(UUID projectFeeId) {
        ResponseEntity<JsonNode> response = send(HttpMethod.GET, "/projects/" + project.getId() + "/fees", null, CLIENT, "CLIENT");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode node : response.getBody()) {
            if (node.get("projectFeeId").asText().equals(projectFeeId.toString())) return node;
        }
        throw new AssertionError("Taxa não encontrada na lista do cliente: " + projectFeeId);
    }

    private String quantityUrl(ProjectFee pf) {
        return "/projects/" + project.getId() + "/fees/" + pf.getId() + "/quantity";
    }

    private String quoteUrl(ProjectFee pf) {
        return "/projects/" + project.getId() + "/fees/" + pf.getId() + "/quote";
    }

    private ResponseEntity<JsonNode> send(HttpMethod method, String url, String body, String user, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (role != null) {
            headers.set("X-Authenticated-User-Id", user);
            headers.set("X-Authenticated-User-Role", role);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody().get("status").asInt()).isEqualTo(status.value());
    }
}
