package br.unicamp.ft.si600.eventos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import br.unicamp.ft.si600.eventos.EventosApplication;

/**
 * US-01: os cabeçalhos X-Authenticated-User-* permitem forjar o perfil ADMIN, então a aplicação
 * não pode subir com o profile "prod" e app.security.legacy-headers=true.
 */
class LegacyHeadersProductionGuardIntegrationTest {

    @Test
    void perfilProdNaoSobeComCabecalhosLegadosLigados() {
        // Argumentos de linha de comando têm precedência sobre o application-prod.yml (que liga o B2).
        String[] args = {
                "--server.port=0",
                "--storage.b2.enabled=false",
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--app.cors.allowed-origins=http://localhost:4173",
                "--app.security.legacy-headers=true"
        };
        SpringApplicationBuilder app = new SpringApplicationBuilder(EventosApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("prod");

        assertThatThrownBy(() -> app.run(args))
                .satisfies(error -> assertThat(rootMessages(error)).contains("app.security.legacy-headers=true"));
    }

    private static String rootMessages(Throwable error) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = error; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append('\n');
        }
        return all.toString();
    }
}
