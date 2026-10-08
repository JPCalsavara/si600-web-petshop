package br.unicamp.ft.si600.eventos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que o profile de produção não herde silenciosamente os defaults de desenvolvimento.
 * O mesmo conjunto de fluxos E2E pode então ser executado apontando o Cypress para a instância prod.
 */
@ActiveProfiles("prod")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "storage.b2.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "app.cors.allowed-origins=http://localhost:4173"
        })
class ProductionProfileIntegrationTest {

    @LocalServerPort int port;
    @org.springframework.beans.factory.annotation.Autowired TestRestTemplate restTemplate;

    @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto;
    @Value("${storage.b2.enabled}") boolean b2Enabled;
    @Value("${app.cors.allowed-origins}") List<String> allowedOrigins;

    @Test
    void deveCarregarConfiguracaoDeProducaoSemDefaultsDeDesenvolvimento() {
        assertThat(ddlAuto).isEqualTo("create-drop"); // sobrescrito apenas para o banco isolado de teste
        assertThat(b2Enabled).isFalse(); // storage real é desativado somente neste teste
        assertThat(allowedOrigins).containsExactly("http://localhost:4173");
    }

    @Test
    void devePermitirPreflightDaOrigemDeProducaoConfigurada() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:4173");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(
                List.of(
                        "Content-Type",
                        "X-Authenticated-User-Id",
                        "X-Authenticated-User-Role"));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/health"), HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:4173");
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
