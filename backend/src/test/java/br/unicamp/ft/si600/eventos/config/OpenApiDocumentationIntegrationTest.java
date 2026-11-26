package br.unicamp.ft.si600.eventos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documentação OpenAPI e Swagger UI.
 *
 * <p>O springdoc monta o caminho dos recursos da UI a partir de {@code springdoc.swagger-ui.version}. Se essa
 * propriedade não acompanhar a versão do webjar {@code org.webjars:swagger-ui} fixada no {@code pom.xml}, toda a UI
 * responde 404 e nenhum outro teste percebe. O {@code application.yml} de teste sombreia o principal, então este
 * teste importa o principal explicitamente e troca somente o banco por H2.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "storage.b2.enabled=false",
        "notification.deadlines.enabled=false",
        "spring.config.import=optional:file:src/main/resources/application.yml",
        "spring.datasource.url=jdbc:h2:mem:openapidocs;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OpenApiDocumentationIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    // ---- Casos bons ----

    @Test
    void deveServirADefinicaoOpenApiComAsRotasDaAplicacao() {
        ResponseEntity<String> response = get("/v3/api-docs", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"openapi\"", "/projects", "/fees");
    }

    @Test
    void deveServirASwaggerUiComSeusRecursosEstaticos() {
        assertThat(get("/swagger-ui/index.html", true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/swagger-ui/swagger-initializer.js", true).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> bundle = get("/swagger-ui/swagger-ui-bundle.js", true);
        assertThat(bundle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bundle.getBody()).isNotBlank();
    }

    // ---- Casos ruins ----

    @Test
    void deveExigirIdentidadeParaAcessarADocumentacao() {
        assertThat(get("/v3/api-docs", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/swagger-ui/index.html", false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Casos incompletos ----

    @Test
    void deveResponderNaoEncontradoParaRecursoInexistenteDaUiSemErroDeServidor() {
        ResponseEntity<String> response = get("/swagger-ui/recurso-que-nao-existe.js", true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> get(String path, boolean authenticated) {
        HttpHeaders headers = new HttpHeaders();
        if (authenticated) {
            headers.set("X-Authenticated-User-Id", "admin-docs");
            headers.set("X-Authenticated-User-Role", "ADMIN");
        }
        return restTemplate.exchange("http://localhost:" + port + "/api" + path,
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
