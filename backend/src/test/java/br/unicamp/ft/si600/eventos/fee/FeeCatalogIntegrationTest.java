package br.unicamp.ft.si600.eventos.fee;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.*;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FeeCatalogIntegrationTest {
    @Autowired TestRestTemplate restTemplate;
    @Autowired FeeRepository repository;

    @BeforeEach
    void limparCatalogo() { repository.deleteAll(); }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"name\":\"Limpeza\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\",\"amountPerM2\":5.25}",
        "{\"name\":\"Extintor\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":25,\"unitAmount\":30}",
        "{\"name\":\"Energia\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"kVA\"}"
    })
    void deveCriarConfiguracoesValidasPersistidas(String body) {
        ResponseEntity<JsonNode> response = request(HttpMethod.POST, "/fees", body, "ADMIN");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(repository.count()).isEqualTo(1);
        String id = response.getBody().get("id").asString();
        JsonNode fetched = request(HttpMethod.GET, "/fees/" + id, null, "ADMIN").getBody();
        assertThat(fetched.get("id").asString()).isEqualTo(id);
        assertThat(fetched.get("name").asString()).isEqualTo(response.getBody().get("name").asString());
        assertThat(fetched.get("type").asString()).isEqualTo(response.getBody().get("type").asString());
        if (fetched.get("areaPricingMode").asString().equals("UNIDADES_POR_INTERVALO")) {
            assertThat(fetched.get("areaPerUnitM2").decimalValue()).isEqualByComparingTo("25");
            assertThat(fetched.get("unitAmount").decimalValue()).isEqualByComparingTo("30");
        } else if (fetched.get("type").asString().equals("VARIAVEL")) {
            assertThat(fetched.get("measurementUnit").asString()).isEqualTo("kVA");
            assertThat(fetched.get("amount").isNull()).isTrue();
        } else {
            assertThat(fetched.get("amountPerM2").decimalValue()).isEqualByComparingTo("5.25");
        }
    }

    @Test
    void deveListarEditarEDesativarSemExcluirOuReativar() {
        String id = request(HttpMethod.POST, "/fees", fixed(), "ADMIN").getBody().get("id").asString();
        assertThat(request(HttpMethod.GET, "/fees", null, "ADMIN").getBody().size()).isEqualTo(1);
        String update = "{\"name\":\"Energia\",\"description\":\"Consumo\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"kVA\"}";
        ResponseEntity<JsonNode> edited = request(HttpMethod.PUT, "/fees/" + id, update, "ADMIN");
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("amount").isNull()).isTrue();
        assertThat(edited.getBody().get("description").asString()).isEqualTo("Consumo");
        ResponseEntity<JsonNode> disabled = request(HttpMethod.POST, "/fees/" + id + "/deactivate", null, "ADMIN");
        assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(disabled.getBody().get("active").asBoolean()).isFalse();
        assertThat(request(HttpMethod.POST, "/fees/" + id + "/deactivate", null, "ADMIN").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.PUT, "/fees/" + id, update, "ADMIN").getBody().get("active").asBoolean()).isFalse();
        JsonNode listed = request(HttpMethod.GET, "/fees", null, "ADMIN").getBody();
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.get(0).get("active").asBoolean()).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"name\":null,\"type\":\"FIXA\",\"amount\":10}",
        "{\"name\":\"\",\"type\":\"FIXA\",\"amount\":10}",
        "{\"name\":{},\"type\":\"FIXA\",\"amount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":{}}",
        "{\"name\":\"Taxa\",\"type\":\"VARIAVEL\",\"measurementUnit\":{}}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\"}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\",\"amountPerM2\":-5}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"unitAmount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":-25,\"unitAmount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":25,\"unitAmount\":0}",
        "{\"name\":\"Taxa\",\"type\":0,\"amount\":10}",
        "{}", "null", "{",
        "{\"name\":\" \",\"type\":\"FIXA\",\"amount\":10}",
        "{\"name\":\"Taxa\",\"amount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\"}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":0}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":-1}",
        "{\"name\":\"Taxa\",\"type\":\"OUTRO\"}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":\"barato\"}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":10.001}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":1000000000000}",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":10,\"measurementUnit\":\"kVA\"}",
        "{\"name\":\"Taxa\",\"type\":\"VARIAVEL\"}",
        "{\"name\":\"Taxa\",\"type\":\"VARIAVEL\",\"measurementUnit\":\" \"}",
        "{\"name\":\"Taxa\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"kVA\",\"amount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\"}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"OUTRO\"}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\",\"amountPerM2\":0}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\",\"amountPerM2\":5,\"unitAmount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":0,\"unitAmount\":10}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":25}",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":25,\"unitAmount\":-10}"
    })
    void deveRejeitarCriacaoEEdicaoInvalidasSemGravar(String body) {
        assertProblem(request(HttpMethod.POST, "/fees", body, "ADMIN"), HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
        String id = request(HttpMethod.POST, "/fees", fixed(), "ADMIN").getBody().get("id").asString();
        assertProblem(request(HttpMethod.PUT, "/fees/" + id, body, "ADMIN"), HttpStatus.BAD_REQUEST);
        JsonNode preserved = request(HttpMethod.GET, "/fees/" + id, null, "ADMIN").getBody();
        assertThat(preserved.get("name").asString()).isEqualTo("Aluguel");
        assertThat(preserved.get("amount").decimalValue()).isEqualByComparingTo("100.50");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deveRejeitarIdsInexistentesEMalformados() {
        String missing = "/fees/" + UUID.randomUUID();
        assertProblem(request(HttpMethod.GET, missing, null, "ADMIN"), HttpStatus.NOT_FOUND);
        assertProblem(request(HttpMethod.PUT, missing, fixed(), "ADMIN"), HttpStatus.NOT_FOUND);
        assertProblem(request(HttpMethod.POST, missing + "/deactivate", null, "ADMIN"), HttpStatus.NOT_FOUND);
        assertProblem(request(HttpMethod.GET, "/fees/invalido", null, "ADMIN"), HttpStatus.BAD_REQUEST);
        assertProblem(request(HttpMethod.PUT, "/fees/invalido", fixed(), "ADMIN"), HttpStatus.BAD_REQUEST);
        assertProblem(request(HttpMethod.POST, "/fees/invalido/deactivate", null, "ADMIN"), HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deveExigirAdminEmTodasAsOperacoesSemAlterarCatalogo() {
        String id = request(HttpMethod.POST, "/fees", fixed(), "ADMIN").getBody().get("id").asString();
        for (String role : new String[]{null, "CLIENT"}) {
            HttpStatus status = role == null ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
            assertProblem(request(HttpMethod.GET, "/fees", null, role), status);
            assertProblem(request(HttpMethod.GET, "/fees/" + id, null, role), status);
            assertProblem(request(HttpMethod.POST, "/fees", fixed(), role), status);
            assertProblem(request(HttpMethod.PUT, "/fees/" + id, fixed(), role), status);
            assertProblem(request(HttpMethod.POST, "/fees/" + id + "/deactivate", null, role), status);
        }
        assertThat(repository.count()).isEqualTo(1);
        assertThat(request(HttpMethod.GET, "/fees/" + id, null, "ADMIN").getBody().get("active").asBoolean()).isTrue();
    }

    @Test
    void deveListarCatalogoVazio() {
        ResponseEntity<JsonNode> response = request(HttpMethod.GET, "/fees", null, "ADMIN");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isArray()).isTrue();
        assertThat(response.getBody().size()).isZero();
    }

    @Test
    void deveRetornarErrosDeCamposELimitesSemEscritaParcial() {
        ResponseEntity<JsonNode> missing = request(HttpMethod.POST, "/fees", "{}", "ADMIN");
        assertProblem(missing, HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().get("invalidFields").has("name")).isTrue();
        assertThat(missing.getBody().get("invalidFields").has("type")).isTrue();
        String tooLong = fixed().replace("Aluguel", "a".repeat(201));
        assertProblem(request(HttpMethod.POST, "/fees", tooLong, "ADMIN"), HttpStatus.BAD_REQUEST);
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u2003", "\\u2003 \\t", "\\u0000", "\\u00a0", "\\u2003\\u0000\\u2003"})
    void deveRejeitarNomeEfetivamenteVazioAposNormalizacao(String name) {
        assertInvalidFieldsOnCreateAndUpdate(fixed().replace("Aluguel", name), "name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u0000", "\\u001f", "\\u2003 \\u0000", "\\u00a0", "\\u2003\\u0000\\u2003"})
    void deveRejeitarUnidadeEfetivamenteVaziaAposNormalizacao(String unit) {
        String body = "{\"name\":\"Energia\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"" + unit + "\"}";
        assertInvalidFieldsOnCreateAndUpdate(body, "measurementUnit");
    }

    @ParameterizedTest
    @CsvSource(value = {
        "{\"name\":\"Taxa\",\"type\":\"FIXA\"}|amount",
        "{\"name\":\"Taxa\",\"type\":\"FIXA\",\"amount\":10,\"areaPricingMode\":\"VALOR_POR_M2\",\"amountPerM2\":2}|areaPricingMode,amountPerM2",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\"}|areaPricingMode",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\"}|amountPerM2",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"VALOR_POR_M2\",\"amountPerM2\":2,\"unitAmount\":10}|unitAmount",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"unitAmount\":10}|areaPerUnitM2",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\",\"areaPerUnitM2\":25}|unitAmount",
        "{\"name\":\"Taxa\",\"type\":\"POR_METRAGEM\",\"areaPricingMode\":\"UNIDADES_POR_INTERVALO\"}|areaPerUnitM2,unitAmount",
        "{\"name\":\"Taxa\",\"type\":\"VARIAVEL\"}|measurementUnit",
        "{\"name\":\"Taxa\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"kVA\",\"amount\":10,\"unitAmount\":5}|amount,unitAmount"
    }, delimiter = '|')
    void deveIdentificarTodosOsCamposCondicionaisInvalidos(String body, String fields) {
        assertInvalidFieldsOnCreateAndUpdate(body, fields.split(","));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "name|\\u0000Taxa", "name|Ta\\u0000xa", "name|Taxa\\u0000",
        "description|\\u0000Consumo", "description|Con\\u0000sumo", "description|Consumo\\u0000",
        "measurementUnit|\\u0000kVA", "measurementUnit|k\\u0000VA", "measurementUnit|kVA\\u0000"
    }, delimiter = '|')
    void deveRejeitarNulEmCamposTextuaisSemPersistir(String field, String value) {
        String body = "{\"name\":\"Energia\",\"description\":\"Consumo\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"kVA\"}";
        String original = switch (field) {
            case "name" -> "Energia";
            case "description" -> "Consumo";
            default -> "kVA";
        };
        assertInvalidFieldsOnCreateAndUpdate(body.replace(original, value), field);
    }

    @Test
    void deveIdentificarNulEmTodosOsCamposTextuaisNaMesmaRequisicao() {
        String body = "{\"name\":\"En\\u0000ergia\",\"description\":\"Con\\u0000sumo\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"k\\u0000VA\"}";
        assertInvalidFieldsOnCreateAndUpdate(body, "name", "description", "measurementUnit");
    }

    @Test
    void devePersistirNomeEUnidadeNormalizadosNaCriacaoEEdicao() {
        String body = "{\"name\":\"\\u2003 Energia \\u2003\",\"type\":\"VARIAVEL\",\"measurementUnit\":\"\\u2003 kVA \\u2003\"}";
        ResponseEntity<JsonNode> created = request(HttpMethod.POST, "/fees", body, "ADMIN");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = created.getBody().get("id").asString();
        for (JsonNode fee : new JsonNode[]{created.getBody(),
                request(HttpMethod.PUT, "/fees/" + id, body, "ADMIN").getBody(),
                request(HttpMethod.GET, "/fees/" + id, null, "ADMIN").getBody()}) {
            assertThat(fee.get("name").asString()).isEqualTo("Energia");
            assertThat(fee.get("measurementUnit").asString()).isEqualTo("kVA");
        }
    }

    private void assertInvalidFieldsOnCreateAndUpdate(String body, String... fields) {
        ResponseEntity<JsonNode> created = request(HttpMethod.POST, "/fees", body, "ADMIN");
        assertFields(created, fields);
        assertThat(repository.count()).isZero();
        String id = request(HttpMethod.POST, "/fees", fixed(), "ADMIN").getBody().get("id").asString();
        assertFields(request(HttpMethod.PUT, "/fees/" + id, body, "ADMIN"), fields);
        JsonNode preserved = request(HttpMethod.GET, "/fees/" + id, null, "ADMIN").getBody();
        assertThat(preserved.get("name").asString()).isEqualTo("Aluguel");
        assertThat(preserved.get("type").asString()).isEqualTo("FIXA");
        assertThat(preserved.get("description").isNull()).isTrue();
        assertThat(preserved.get("measurementUnit").isNull()).isTrue();
        assertThat(preserved.get("amount").decimalValue()).isEqualByComparingTo("100.50");
        assertThat(repository.count()).isEqualTo(1);
    }

    private void assertFields(ResponseEntity<JsonNode> response, String... fields) {
        assertProblem(response, HttpStatus.BAD_REQUEST);
        JsonNode errors = response.getBody().get("invalidFields");
        assertThat(errors).isNotNull();
        assertThat(errors.size()).isEqualTo(fields.length);
        for (String field : fields) {
            assertThat(errors.has(field)).as("campo %s", field).isTrue();
        }
    }

    private String fixed() { return "{\"name\":\"Aluguel\",\"type\":\"FIXA\",\"amount\":100.50}"; }

    private void assertProblem(ResponseEntity<JsonNode> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)).isTrue();
        assertThat(response.getBody().get("status").asInt()).isEqualTo(status.value());
        assertThat(response.getBody().hasNonNull("title")).isTrue();
    }

    @Test
    void deveCriarTaxaFixaPersistidaEConsultarPelaApi() {
        ResponseEntity<JsonNode> created = request(HttpMethod.POST, "/fees",
                "{\"name\":\"Aluguel\",\"type\":\"FIXA\",\"amount\":100.50}", "ADMIN");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode fee = created.getBody();
        assertThat(fee).isNotNull();
        assertThat(fee.get("active").asBoolean()).isTrue();
        ResponseEntity<JsonNode> fetched = request(HttpMethod.GET, "/fees/" + fee.get("id").asString(), null, "ADMIN");
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("amount").decimalValue()).isEqualByComparingTo("100.50");
    }

    private ResponseEntity<JsonNode> request(HttpMethod method, String path, String body, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (role != null) {
            headers.set("X-Authenticated-User-Id", "fee-test-user");
            headers.set("X-Authenticated-User-Role", role);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
