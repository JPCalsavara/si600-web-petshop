package br.unicamp.ft.si600.eventos.project;

import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectHistoryEventRepository;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
@Import(TestStorageConfig.class)
class ProjectCsvExportIntegrationTest {

    private static final String ADMIN_ID = "admin-us17";
    private static final String CLIENT_EMAIL = "expositor-us17@exemplo.com";

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
    ProjectHistoryEventRepository historyEventRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        historyEventRepository.deleteAll();
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

    private UUID createProjectViaHttp(String company, String document, String email, String address, String representative) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", company,
                "address", address,
                "document", document,
                "email", email,
                "area", "B2C",
                "pdfDeadline", "2026-12-10",
                "paymentDeadline", "2026-12-20",
                "representativeName", representative
        ));
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

        HttpHeaders textHeaders = new HttpHeaders();
        textHeaders.setContentType(MediaType.TEXT_PLAIN);
        multipart.add("areaM2", new HttpEntity<>(areaM2, textHeaders));

        HttpHeaders reqHeaders = new HttpHeaders();
        reqHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        reqHeaders.set("X-Authenticated-User-Id", clientEmail);
        reqHeaders.set("X-Authenticated-User-Role", "CLIENT");

        return restTemplate.postForEntity(
                url("/projects/" + projectId + "/pdf"),
                new HttpEntity<>(multipart, reqHeaders),
                String.class
        );
    }

    @Nested
    @DisplayName("Casos Bons (Happy Path)")
    class HappyPath {

        @Test
        @DisplayName("Admin exporta CSV completo com UTF-8 BOM, separador ';' e 3 secoes (Dados do Projeto, Taxas e Historico)")
        void adminExportsFullProjectCsvWithUtf8BomSemicolonAndThreeSections() throws Exception {
            Fee fixedFee = feeRepository.save(new Fee(
                    "Taxa de Limpeza Inicial",
                    "Limpeza padrao do pavilhao",
                    FeeType.FIXA,
                    null,
                    new BigDecimal("250.00"),
                    null, null, null, null
            ));
            Fee areaFee = feeRepository.save(new Fee(
                    "Aluguel de Piso",
                    "Cobranca por metro quadrado",
                    FeeType.POR_METRAGEM,
                    AreaPricingMode.VALOR_POR_M2,
                    null,
                    new BigDecimal("100.00"),
                    null, null, null
            ));
            Fee variableFee = feeRepository.save(new Fee(
                    "Energia Trifasica",
                    "Pontos extras de energia",
                    FeeType.VARIAVEL,
                    null, null, null, null, null,
                    "kVA"
            ));

            UUID projectId = createProjectViaHttp(
                    "Expositora Alfa Ltda",
                    "12.345.678/0001-99",
                    CLIENT_EMAIL,
                    "Av. das Feiras, 500",
                    "Maria Oliveira"
            );

            // Cliente envia PDF
            byte[] pdfBytes = "%PDF-1.4 planta estande".getBytes(StandardCharsets.UTF_8);
            assertThat(uploadPdf(projectId, CLIENT_EMAIL, "planta-alfa.pdf", pdfBytes, "30.00").getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            // Admin aprova PDF com 35.00 m2
            ResponseEntity<String> approveRes = restTemplate.postForEntity(
                    url("/projects/admin/" + projectId + "/approve"),
                    new HttpEntity<>("{\"approvedAreaM2\": 35.00}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(approveRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Admin configura as 3 taxas no projeto
            String configBody = """
                    { "feeIds": ["%s", "%s", "%s"] }
                    """.formatted(fixedFee.getId(), areaFee.getId(), variableFee.getId());
            ResponseEntity<String> configRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/fees/configuration"),
                    HttpMethod.PUT,
                    new HttpEntity<>(configBody, headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(configRes.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode configuredFees = objectMapper.readTree(configRes.getBody());
            UUID variableProjectFeeId = null;
            for (JsonNode node : configuredFees) {
                if ("VARIAVEL".equals(node.get("type").asText())) {
                    variableProjectFeeId = UUID.fromString(node.get("projectFeeId").asText());
                }
            }
            assertThat(variableProjectFeeId).isNotNull();

            // Cliente informa quantidade e Admin cota a taxa variavel
            assertThat(restTemplate.exchange(
                    url("/projects/" + projectId + "/fees/" + variableProjectFeeId + "/quantity"),
                    HttpMethod.PUT,
                    new HttpEntity<>("{\"quantity\": 12.50}", headers(CLIENT_EMAIL, "CLIENT")),
                    String.class
            ).getStatusCode()).isEqualTo(HttpStatus.OK);

            assertThat(restTemplate.exchange(
                    url("/projects/" + projectId + "/fees/" + variableProjectFeeId + "/quote"),
                    HttpMethod.PUT,
                    new HttpEntity<>("{\"amount\": 890.00}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            ).getStatusCode()).isEqualTo(HttpStatus.OK);

            // Admin exporta CSV
            ResponseEntity<byte[]> csvResponse = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    byte[].class
            );

            assertThat(csvResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(csvResponse.getHeaders().getContentType()).isNotNull();
            assertThat(csvResponse.getHeaders().getContentType().toString()).containsIgnoringCase("text/csv");
            assertThat(csvResponse.getHeaders().getContentType().toString()).containsIgnoringCase("UTF-8");

            String contentDisposition = csvResponse.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(contentDisposition)
                    .isNotNull()
                    .contains("attachment")
                    .contains(".csv");

            byte[] rawBytes = csvResponse.getBody();
            assertThat(rawBytes).isNotNull();
            String csvText = new String(rawBytes, StandardCharsets.UTF_8);

            // Verifica presenca de UTF-8 BOM (\uFEFF) para abertura direta no Excel em portugues
            assertThat(csvText).startsWith("\uFEFF");

            // Verifica cabecalhos em portugues com separador ponto e virgula (;)
            assertThat(csvText).contains("ID do Projeto;Empresa;CPF/CNPJ;Representante;E-mail de Contato;Endereço;Categoria do Estande;Status do Projeto;Área do Estande (m²);Prazo de Envio do PDF;Prazo de Pagamento;Arquivo PDF;Decisão por;Data da Decisão;Justificativa de Reprovação");
            assertThat(csvText).contains("ID do Vínculo;Nome da Taxa;Descrição;Tipo de Taxa;Status da Taxa;Quantidade;Unidade;Valor Calculado / Cotado (R$);Cotado por;Data da Cotação");
            assertThat(csvText).contains("Data/Hora;Tipo de Evento;Autor;Perfil do Autor;Descrição");

            // Verifica dados cadastrais do projeto
            assertThat(csvText).contains("Expositora Alfa Ltda");
            assertThat(csvText).contains("12.345.678/0001-99");
            assertThat(csvText).contains("Maria Oliveira");
            assertThat(csvText).contains("planta-alfa.pdf");
            assertThat(csvText).contains("35.00");

            // Verifica detalhamento das taxas (Fixa 250.00, Por Metragem 35 * 100 = 3500.00, Variavel Cotada 890.00)
            assertThat(csvText).contains("Taxa de Limpeza Inicial");
            assertThat(csvText).contains("250.00");
            assertThat(csvText).contains("Aluguel de Piso");
            assertThat(csvText).contains("3500.00");
            assertThat(csvText).contains("Energia Trifasica");
            assertThat(csvText).contains("890.00");

            // Verifica eventos do historico (US-16)
            assertThat(csvText).contains("Projeto criado");
            assertThat(csvText).contains("PDF enviado");
            assertThat(csvText).contains("PDF aprovado");
            assertThat(csvText).contains("Taxas configuradas");
            assertThat(csvText).contains("Quantidade informada");
            assertThat(csvText).contains("Taxa cotada");
        }

        @Test
        @DisplayName("Admin exporta CSV de projeto recem-criado em AGUARDANDO_PDF sem erro 422")
        void adminExportsProjectInInitialStatusWithoutError() throws Exception {
            UUID projectId = createProjectViaHttp(
                    "Estande Inicial S.A.",
                    "98.765.432/0001-10",
                    "inicial-us17@exemplo.com",
                    "Rua Principal, 10",
                    "Carlos Souza"
            );

            ResponseEntity<byte[]> csvResponse = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    byte[].class
            );

            assertThat(csvResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            String csvText = new String(csvResponse.getBody(), StandardCharsets.UTF_8);
            assertThat(csvText).contains("Estande Inicial S.A.");
            assertThat(csvText).contains("Aguardando PDF");
            assertThat(csvText).contains("Projeto criado");
        }
    }

    @Nested
    @DisplayName("Casos Ruins (Sad Path / Regras de Negocio)")
    class SadPath {

        @Test
        @DisplayName("Cliente nao pode exportar CSV do projeto (403 Forbidden)")
        void clientCannotExportProjectCsv() throws Exception {
            UUID projectId = createProjectViaHttp(
                    "Empresa Restrita Ltda",
                    "11.222.333/0001-44",
                    CLIENT_EMAIL,
                    "Av. Brasil, 200",
                    "Joao Silva"
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(CLIENT_EMAIL, "CLIENT")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            JsonNode problem = objectMapper.readTree(response.getBody());
            assertThat(problem.get("status").asInt()).isEqualTo(403);
            assertThat(problem.get("detail").asText()).contains("permissão");
        }

        @Test
        @DisplayName("Exportar CSV de projeto inexistente retorna 404 Not Found")
        void nonExistentProjectReturns404() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/" + UUID.randomUUID() + "/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            JsonNode problem = objectMapper.readTree(response.getBody());
            assertThat(problem.get("status").asInt()).isEqualTo(404);
        }

        @Test
        @DisplayName("Requisicao com role invalida retorna 401 Unauthorized")
        void invalidRoleReturns401() throws Exception {
            UUID projectId = createProjectViaHttp(
                    "Empresa Auth Ltda",
                    "55.666.777/0001-88",
                    "auth-us17@exemplo.com",
                    "Av. Central, 300",
                    "Ana Costa"
            );

            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers("anon", "ROLE_INVALIDA")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Casos Incompletos / Limites de Borda (Boundary & Malformed Payloads)")
    class BoundaryAndMalformed {

        @Test
        @DisplayName("UUID malformado na rota de exportacao retorna 400 Bad Request com RFC 7807")
        void malformedUuidReturns400() throws Exception {
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/projects/id-nao-uuid/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            JsonNode problem = objectMapper.readTree(response.getBody());
            assertThat(problem.get("status").asInt()).isEqualTo(400);
        }

        @Test
        @DisplayName("Metodos de mutacao (POST/PUT/DELETE) no endpoint de exportacao retornam 405 Method Not Allowed")
        void mutationMethodsReturn405() throws Exception {
            UUID projectId = createProjectViaHttp(
                    "Empresa Imutavel CSV",
                    "33.444.555/0001-22",
                    "imutavel-us17@exemplo.com",
                    "Rua Cinco, 50",
                    "Pedro Lima"
            );

            ResponseEntity<String> postRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.POST,
                    new HttpEntity<>("{}", headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(postRes.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

            ResponseEntity<String> deleteRes = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    String.class
            );
            assertThat(deleteRes.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }

        @Test
        @DisplayName("Campos contendo ponto e virgula (;) ou aspas duplas (\") sao escapados corretamente no CSV")
        void specialCharactersWithSemicolonsAndQuotesAreEscapedProperly() throws Exception {
            UUID projectId = createProjectViaHttp(
                    "Empresa Ponto;Virgula \"Especial\" Ltda",
                    "77.888.999/0001-66",
                    "especial-us17@exemplo.com",
                    "Av. A; Bloco \"B\", Sala 10",
                    "Roberto; \"Beto\" Dias"
            );

            ResponseEntity<byte[]> csvResponse = restTemplate.exchange(
                    url("/projects/" + projectId + "/export/csv"),
                    HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN_ID, "ADMIN")),
                    byte[].class
            );

            assertThat(csvResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            String csvText = new String(csvResponse.getBody(), StandardCharsets.UTF_8);

            // Campos com ';' e '"' devem vir entre aspas duplas e com aspas internas duplicadas ("")
            assertThat(csvText).contains("\"Empresa Ponto;Virgula \"\"Especial\"\" Ltda\"");
            assertThat(csvText).contains("\"Av. A; Bloco \"\"B\"\", Sala 10\"");
            assertThat(csvText).contains("\"Roberto; \"\"Beto\"\" Dias\"");
        }
    }
}
