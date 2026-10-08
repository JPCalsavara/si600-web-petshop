package br.unicamp.ft.si600.eventos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "storage.b2.enabled=false")
class CorsIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void devePermitirPreflightDoFrontendConfigurado() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(
                java.util.List.of(
                        "Content-Type",
                        "X-Authenticated-User-Id",
                        "X-Authenticated-User-Role"));

        ResponseEntity<String> response = restTemplate.exchange(
                url("/health"), HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.POST);
        assertThat(response.getHeaders().getAccessControlAllowHeaders())
                .contains("X-Authenticated-User-Id", "X-Authenticated-User-Role");
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
