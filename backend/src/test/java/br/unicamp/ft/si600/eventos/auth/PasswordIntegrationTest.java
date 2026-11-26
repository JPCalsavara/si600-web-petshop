package br.unicamp.ft.si600.eventos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import br.unicamp.ft.si600.eventos.entity.PasswordResetRequest;
import br.unicamp.ft.si600.eventos.entity.PasswordResetStatus;
import br.unicamp.ft.si600.eventos.entity.UserAccount;
import br.unicamp.ft.si600.eventos.project.TestEmailConfig;
import br.unicamp.ft.si600.eventos.project.TestStorageConfig;
import br.unicamp.ft.si600.eventos.repository.AuthSessionRepository;
import br.unicamp.ft.si600.eventos.repository.PasswordResetRequestRepository;
import br.unicamp.ft.si600.eventos.repository.UserAccountRepository;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.security.PasswordHasher;
import tools.jackson.databind.ObjectMapper;

/**
 * US-14: troca de senha (autenticado) e recuperação de acesso ("Esqueci a senha").
 * Roda com os cabeçalhos antigos DESLIGADOS (app.security.legacy-headers=false), como em produção.
 *
 * Usa um banco H2 só dele: o LegacyHeadersProductionGuardIntegrationTest sobe (e derruba de propósito) a
 * aplicação com create-drop no banco padrão "testdb", apagando as tabelas dele. Sem banco próprio, esta classe
 * herdaria o contexto em cache do AuthIntegrationTest apontando para um "testdb" vazio.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "storage.b2.enabled=false",
                "app.security.legacy-headers=false",
                "spring.datasource.url=jdbc:h2:mem:passwords;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
        })
@Import({TestStorageConfig.class, TestEmailConfig.class})
class PasswordIntegrationTest {

    private static final String PASSWORD = "senha-segura-123";
    private static final String NEW_PASSWORD = "nova-senha-456";
    private static final String ADMIN_EMAIL = "admin@exemplo.com";
    private static final String CLIENT_EMAIL = "cliente@exemplo.com";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired PasswordResetRequestRepository resets;
    @Autowired PasswordHasher hasher;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        resets.deleteAll();
        users.deleteAll();
        users.save(new UserAccount(ADMIN_EMAIL, hasher.hash(PASSWORD), ActorRole.ADMIN));
        users.save(new UserAccount(CLIENT_EMAIL, hasher.hash(PASSWORD), ActorRole.CLIENT));
    }

    // ---------- Casos bons: troca de senha ----------

    @Test
    void trocaDeSenhaComSenhaAtualCorretaPermiteEntrarComANovaEInvalidaAAntiga() throws Exception {
        String token = tokenOf(CLIENT_EMAIL, PASSWORD);

        ResponseEntity<String> response = changePassword(token, PASSWORD, NEW_PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(CLIENT_EMAIL, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void trocaDeSenhaEncerraAsOutrasSessoesEMantemAAtual() throws Exception {
        String atual = tokenOf(CLIENT_EMAIL, PASSWORD);
        String outroDispositivo = tokenOf(CLIENT_EMAIL, PASSWORD);

        assertThat(changePassword(atual, PASSWORD, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/auth/me", atual).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/auth/me", outroDispositivo).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- Casos bons: recuperação ----------

    @Test
    void esqueciASenhaSemLoginRegistraPedidoPendenteEResponde202() throws Exception {
        ResponseEntity<String> response = forgotPassword(CLIENT_EMAIL);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(mapper.readTree(response.getBody()).get("message").asText()).isNotBlank();
        assertThat(resets.findAllByStatusOrderByRequestedAtAsc(PasswordResetStatus.PENDENTE))
                .singleElement().satisfies(p -> assertThat(p.getEmail()).isEqualTo(CLIENT_EMAIL));
    }

    @Test
    void adminVeOPedidoGeraSenhaTemporariaEOUsuarioPassaAEntrarComEla() throws Exception {
        String clienteAntigo = tokenOf(CLIENT_EMAIL, PASSWORD);
        forgotPassword(CLIENT_EMAIL);
        String adminToken = tokenOf(ADMIN_EMAIL, PASSWORD);

        ResponseEntity<String> list = get("/admin/password-resets", adminToken);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        var items = mapper.readTree(list.getBody());
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("email").asText()).isEqualTo(CLIENT_EMAIL);
        String id = items.get(0).get("id").asText();

        ResponseEntity<String> resolved = post("/admin/password-resets/" + id + "/resolve", adminToken, null);

        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        String temporaria = mapper.readTree(resolved.getBody()).get("temporaryPassword").asText();
        assertThat(temporaria).hasSizeGreaterThanOrEqualTo(8);
        assertThat(login(CLIENT_EMAIL, temporaria).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/auth/me", clienteAntigo).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resets.findAllByStatusOrderByRequestedAtAsc(PasswordResetStatus.PENDENTE)).isEmpty();
        assertThat(resets.findAll()).singleElement().satisfies(p -> {
            assertThat(p.getStatus()).isEqualTo(PasswordResetStatus.ATENDIDO);
            assertThat(p.getResolvedBy()).isEqualTo(ADMIN_EMAIL);
            assertThat(p.getResolvedAt()).isNotNull();
        });
    }

    // ---------- Casos ruins: troca de senha ----------

    @Test
    void senhaAtualIncorretaRetorna422ENaoAlteraASenha() throws Exception {
        String token = tokenOf(CLIENT_EMAIL, PASSWORD);

        ResponseEntity<String> response = changePassword(token, "senha-errada-999", NEW_PASSWORD);

        // 422 e não 401: o frontend trata 401 como sessão expirada e deslogaria o usuário.
        // Compara o número: no Spring Framework 7 o 422 se chama UNPROCESSABLE_CONTENT (UNPROCESSABLE_ENTITY é outro item do enum).
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login(CLIENT_EMAIL, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void novaSenhaFracaOuIgualAAtualRetorna400IndicandoOCampo() throws Exception {
        String token = tokenOf(CLIENT_EMAIL, PASSWORD);

        for (String fraca : new String[] {"curta1", "somenteletras", "123456789", PASSWORD}) {
            ResponseEntity<String> response = changePassword(token, PASSWORD, fraca);

            assertProblemDetail400(response);
            assertThat(mapper.readTree(response.getBody()).get("invalidFields").has("newPassword")).isTrue();
        }
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trocarSenhaSemTokenOuComTokenInventadoRetorna401() {
        assertThat(changePassword(null, PASSWORD, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(changePassword("token-inventado", PASSWORD, NEW_PASSWORD).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---------- Casos ruins: recuperação ----------

    @Test
    void emailNaoCadastradoRecebeAMesmaRespostaENaoCriaPedido() throws Exception {
        ResponseEntity<String> existente = forgotPassword(CLIENT_EMAIL);
        resets.deleteAll();

        ResponseEntity<String> inexistente = forgotPassword("ninguem@exemplo.com");

        assertThat(inexistente.getStatusCode()).isEqualTo(existente.getStatusCode());
        assertThat(inexistente.getBody()).isEqualTo(existente.getBody());
        assertThat(resets.count()).isZero();
    }

    @Test
    void pedidoRepetidoNaoCriaSegundoPedidoPendente() {
        forgotPassword(CLIENT_EMAIL);
        forgotPassword("  CLIENTE@Exemplo.com ");

        assertThat(resets.findAllByStatusOrderByRequestedAtAsc(PasswordResetStatus.PENDENTE)).hasSize(1);
    }

    @Test
    void clienteNaoListaNemAtendePedidosDeRedefinicao() throws Exception {
        forgotPassword(CLIENT_EMAIL);
        String clienteToken = tokenOf(CLIENT_EMAIL, PASSWORD);
        UUID id = resets.findAll().get(0).getId();

        assertThat(get("/admin/password-resets", clienteToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/admin/password-resets/" + id + "/resolve", clienteToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resets.findAll().get(0).getStatus()).isEqualTo(PasswordResetStatus.PENDENTE);
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rotasDoAdminDeRedefinicaoExigemLogin() {
        assertThat(get("/admin/password-resets", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/admin/password-resets/" + UUID.randomUUID() + "/resolve", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void atenderPedidoInexistenteRetorna404EJaAtendidoRetorna409() throws Exception {
        forgotPassword(CLIENT_EMAIL);
        String adminToken = tokenOf(ADMIN_EMAIL, PASSWORD);
        PasswordResetRequest pedido = resets.findAll().get(0);

        assertThat(post("/admin/password-resets/" + UUID.randomUUID() + "/resolve", adminToken, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(post("/admin/password-resets/" + pedido.getId() + "/resolve", adminToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post("/admin/password-resets/" + pedido.getId() + "/resolve", adminToken, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---------- Casos incompletos ----------

    @Test
    void trocaDeSenhaComCamposAusentesOuEmBrancoRetorna400ComInvalidFields() throws Exception {
        String token = tokenOf(CLIENT_EMAIL, PASSWORD);

        for (String payload : new String[] {
                "{}",
                "{\"currentPassword\":null,\"newPassword\":null}",
                "{\"currentPassword\":\"   \",\"newPassword\":\"   \"}"}) {
            ResponseEntity<String> response = post("/auth/change-password", token, payload);

            assertProblemDetail400(response);
            var fields = mapper.readTree(response.getBody()).get("invalidFields");
            assertThat(fields.has("currentPassword")).isTrue();
            assertThat(fields.has("newPassword")).isTrue();
        }
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void trocaDeSenhaComCorpoMalformadoOuSenhaGiganteRetorna400() throws Exception {
        String token = tokenOf(CLIENT_EMAIL, PASSWORD);

        for (String payload : new String[] {"", "null", "{nao e json", "[1,2]"}) {
            assertProblemDetail400(post("/auth/change-password", token, payload));
        }
        ResponseEntity<String> longa = changePassword(token, PASSWORD, "a1".repeat(101));
        assertProblemDetail400(longa);
        assertThat(mapper.readTree(longa.getBody()).get("invalidFields").get("newPassword").asText())
                .isEqualTo("Senha muito longa.");
    }

    @Test
    void esqueciASenhaComCorpoIncompletoOuMalformadoRetorna400SemCriarPedido() throws Exception {
        for (String payload : new String[] {
                "{}", "{\"email\":null}", "{\"email\":\"   \"}", "", "null", "{nao e json"}) {
            assertProblemDetail400(post("/auth/forgot-password", null, payload));
        }
        ResponseEntity<String> longo = forgotPassword("a".repeat(250) + "@x.com");
        assertProblemDetail400(longo);
        assertThat(mapper.readTree(longo.getBody()).get("invalidFields").get("email").asText())
                .isEqualTo("E-mail muito longo.");
        assertThat(resets.count()).isZero();
    }

    @Test
    void atenderPedidoComIdQueNaoEUuidRetorna400() throws Exception {
        String adminToken = tokenOf(ADMIN_EMAIL, PASSWORD);

        assertProblemDetail400(post("/admin/password-resets/nao-e-uuid/resolve", adminToken, null));
    }

    // ---------- Helpers ----------

    private void assertProblemDetail400(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().getSubtype()).contains("json");
        var body = mapper.readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isNotBlank();
    }

    private String tokenOf(String email, String password) throws Exception {
        return mapper.readTree(login(email, password).getBody()).get("token").asText();
    }

    private ResponseEntity<String> login(String email, String password) {
        return post("/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private ResponseEntity<String> changePassword(String token, String current, String next) {
        return post("/auth/change-password", token,
                "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
    }

    private ResponseEntity<String> forgotPassword(String email) {
        return post("/auth/forgot-password", null, "{\"email\":\"" + email + "\"}");
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, String token, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
