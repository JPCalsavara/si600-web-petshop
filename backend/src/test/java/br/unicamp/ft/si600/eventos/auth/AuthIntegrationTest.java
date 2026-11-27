package br.unicamp.ft.si600.eventos.auth;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import tools.jackson.databind.ObjectMapper;

import br.unicamp.ft.si600.eventos.entity.AuthSession;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.UserAccount;
import br.unicamp.ft.si600.eventos.project.CapturingEmailSender;
import br.unicamp.ft.si600.eventos.project.TestEmailConfig;
import br.unicamp.ft.si600.eventos.project.TestStorageConfig;
import br.unicamp.ft.si600.eventos.repository.AuthSessionRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.repository.UserAccountRepository;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.security.PasswordHasher;
import br.unicamp.ft.si600.eventos.service.AuthService;

/**
 * US-01: login, perfis de acesso e logout. Roda com os cabeçalhos antigos DESLIGADOS
 * (app.security.legacy-headers=false), como em produção.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"storage.b2.enabled=false", "app.security.legacy-headers=false"})
@Import({TestStorageConfig.class, TestEmailConfig.class})
class AuthIntegrationTest {
    private static final String ADDRESS_JSON = "\"address\":{\"zipCode\":\"13083-852\",\"street\":\"Rua X\","
            + "\"number\":\"100\",\"complement\":null,\"neighborhood\":\"Centro\",\"city\":\"Campinas\",\"state\":\"SP\"}";

    private static final String PASSWORD = "senha-segura-123";
    private static final String ADMIN_EMAIL = "admin@exemplo.com";
    private static final String CLIENT_EMAIL = "cliente@exemplo.com";
    private static final String OTHER_EMAIL = "outro@exemplo.com";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired ProjectRepository projects;
    @Autowired ProjectFeeRepository projectFees;
    @Autowired PasswordHasher hasher;
    @Autowired CapturingEmailSender emails;
    @Autowired AuthService authService;

    private final ObjectMapper mapper = new ObjectMapper();
    private Project clientProject;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        sessions.deleteAll();
        projectFees.deleteAll();
        projects.deleteAll();
        users.deleteAll();
        emails.clear();

        users.save(new UserAccount(ADMIN_EMAIL, hasher.hash(PASSWORD), ActorRole.ADMIN));
        users.save(new UserAccount(CLIENT_EMAIL, hasher.hash(PASSWORD), ActorRole.CLIENT));
        users.save(new UserAccount(OTHER_EMAIL, hasher.hash(PASSWORD), ActorRole.CLIENT));
        clientProject = seedProject(CLIENT_EMAIL, "54018395000189");
        otherProject = seedProject(OTHER_EMAIL, "11222333000181");
    }

    // ---------- Casos bons ----------

    @Test
    void adminLogaEViraPerfilAdminSemProjeto() throws Exception {
        ResponseEntity<String> response = login(ADMIN_EMAIL, PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(response.getBody()).get("role").asText()).isEqualTo("ADMIN");
        assertThat(mapper.readTree(response.getBody()).get("token").asText()).isNotBlank();
        assertThat(mapper.readTree(response.getBody()).get("projectId").isNull()).isTrue();
        assertThat(sessions.count()).isEqualTo(1);
    }

    @Test
    void clienteLogaERecebeOIdDoProprioProjeto() throws Exception {
        ResponseEntity<String> response = login("  CLIENTE@Exemplo.com ", PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(response.getBody()).get("role").asText()).isEqualTo("CLIENT");
        assertThat(mapper.readTree(response.getBody()).get("projectId").asText())
                .isEqualTo(clientProject.getId().toString());
    }

    @Test
    void primeiroLoginDoClienteRegistraOPrimeiroAcessoSoDoProprioProjeto() {
        assertThat(projects.findById(clientProject.getId()).orElseThrow().getLastAccessAt()).isNull();

        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(projects.findById(clientProject.getId()).orElseThrow().getLastAccessAt()).isNotNull();
        assertThat(projects.findById(otherProject.getId()).orElseThrow().getLastAccessAt()).isNull();
    }

    @Test
    void novoLoginNaoSobrescreveOPrimeiroAcesso() {
        Project seeded = projects.findById(clientProject.getId()).orElseThrow();
        seeded.recordAccess(OffsetDateTime.now().minusDays(3).truncatedTo(ChronoUnit.SECONDS));
        OffsetDateTime firstAccess = projects.saveAndFlush(seeded).getLastAccessAt();

        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);

        OffsetDateTime afterLogin = projects.findById(clientProject.getId()).orElseThrow().getLastAccessAt();
        assertThat(afterLogin.toInstant()).isEqualTo(firstAccess.toInstant());
    }

    @Test
    void adminComTokenAcessaTelaDeAdmin() throws Exception {
        String token = tokenOf(ADMIN_EMAIL);

        assertThat(get("/projects/admin", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/admin/quotes", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void clienteComTokenVeOProprioProjeto() throws Exception {
        String token = tokenOf(CLIENT_EMAIL);

        assertThat(get("/projects/" + clientProject.getId(), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> me = get("/auth/me", token);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(me.getBody()).get("email").asText()).isEqualTo(CLIENT_EMAIL);
    }

    @Test
    void logoutEncerraASessaoENaoPermiteMaisUsarOToken() throws Exception {
        String token = tokenOf(CLIENT_EMAIL);

        ResponseEntity<String> logout = post("/auth/logout", token, null);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(sessions.count()).isZero();
        assertThat(get("/auth/me", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void criarProjetoGeraContaDoClienteEEnviaSenhaProvisoriaPorEmail() throws Exception {
        String adminToken = tokenOf(ADMIN_EMAIL);

        ResponseEntity<String> created = post("/projects", adminToken, newProjectJson("novo@exemplo.com", "45723174000110"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UserAccount account = users.findByEmail("novo@exemplo.com").orElseThrow();
        assertThat(account.getRole()).isEqualTo(ActorRole.CLIENT);
        assertThat(emails.sent()).hasSize(1);
        CapturingEmailSender.Sent mail = emails.sent().get(0);
        assertThat(mail.to()).isEqualTo("novo@exemplo.com");
        Matcher m = Pattern.compile("Senha provisória: (\\S+)").matcher(mail.body());
        assertThat(m.find()).isTrue();
        String provisional = m.group(1);
        assertThat(account.getPasswordHash()).doesNotContain(provisional);
        assertThat(login("novo@exemplo.com", provisional).getStatusCode()).isEqualTo(HttpStatus.OK);
        // A mesma senha também volta na resposta da criação, para o Admin repassar ao Cliente na tela.
        assertThat(mapper.readTree(created.getBody()).get("provisionalPassword").asText()).isEqualTo(provisional);
    }

    @Test
    void criarProjetoParaEmailQueJaTemContaNaoRecriaAcessoNemDevolveSenha() throws Exception {
        users.save(new UserAccount("ja-tem-conta@exemplo.com", hasher.hash(PASSWORD), ActorRole.CLIENT));
        String adminToken = tokenOf(ADMIN_EMAIL);

        ResponseEntity<String> created = post("/projects", adminToken,
                newProjectJson("ja-tem-conta@exemplo.com", "45723174000110"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(mapper.readTree(created.getBody()).has("provisionalPassword")).isFalse();
        assertThat(emails.sent()).isEmpty();
        // A senha que a pessoa já tinha continua valendo.
        assertThat(login("ja-tem-conta@exemplo.com", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sessoesExpiradasSaoLimpasPeloServicoDeLimpezaENaoPeloLogin() {
        UUID userId = users.findByEmail(CLIENT_EMAIL).orElseThrow().getId();
        sessions.save(new AuthSession("expirada-1", userId, OffsetDateTime.now().minusMinutes(5)));
        sessions.save(new AuthSession("ativa-1", userId, OffsetDateTime.now().plusHours(1)));

        // Um login não mexe nas sessões expiradas (ponto de contenção removido do login).
        assertThat(login(CLIENT_EMAIL, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessions.count()).isEqualTo(3);

        // A limpeza periódica remove só a expirada.
        assertThat(authService.purgeExpiredSessions()).isEqualTo(1);
        assertThat(sessions.findAll()).extracting(AuthSession::getTokenHash)
                .contains("ativa-1").doesNotContain("expirada-1").hasSize(2);
    }

    // ---------- Casos ruins ----------

    @Test
    void senhaErradaEEmailInexistenteRetornam401ComAMesmaMensagem() {
        ResponseEntity<String> wrongPassword = login(CLIENT_EMAIL, "errada");
        ResponseEntity<String> unknownEmail = login("ninguem@exemplo.com", PASSWORD);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).contains("E-mail ou senha inválidos.");
        assertThat(unknownEmail.getBody()).contains("E-mail ou senha inválidos.");
        assertThat(sessions.count()).isZero();
    }

    @Test
    void clienteNaoAcessaEndpointsDeAdmin() throws Exception {
        String token = tokenOf(CLIENT_EMAIL);

        assertThat(get("/projects/admin", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/admin/quotes", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/projects", token, newProjectJson("x@exemplo.com", "45723174000110")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(users.findByEmail("x@exemplo.com")).isEmpty();
    }

    @Test
    void clienteNaoEnxergaProjetoDeOutroCliente() throws Exception {
        String token = tokenOf(CLIENT_EMAIL);

        ResponseEntity<String> response = get("/projects/" + otherProject.getId(), token);

        assertThat(response.getStatusCode().value()).isIn(403, 404);
        assertThat(response.getBody()).doesNotContain(OTHER_EMAIL);
    }

    @Test
    void semTokenOuComCabecalhoForjadoRetorna401() {
        HttpHeaders forged = new HttpHeaders();
        forged.set("X-Authenticated-User-Id", "qualquer");
        forged.set("X-Authenticated-User-Role", "ADMIN");

        assertThat(get("/projects/admin", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.exchange(url("/projects/admin"), HttpMethod.GET, new HttpEntity<>(forged), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tokenInventadoOuExpiradoRetorna401() throws Exception {
        String token = tokenOf(CLIENT_EMAIL);
        AuthSession active = sessions.findAll().get(0);
        sessions.save(new AuthSession(active.getTokenHash(), active.getUserId(), OffsetDateTime.now().minusMinutes(1)));

        assertThat(get("/auth/me", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/auth/me", "token-inventado").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---------- Casos incompletos ----------

    @Test
    void loginComObjetoVazioOuCamposNulosRetorna400ComProblemDetailEInvalidFields() throws Exception {
        for (String payload : new String[] {
                "{}",
                "{\"email\":null,\"password\":null}"}) {
            ResponseEntity<String> response = postLoginRaw(payload);

            assertProblemDetail400(response);
            var fields = mapper.readTree(response.getBody()).get("invalidFields");
            assertThat(fields.get("email").asText()).isEqualTo("Informe o e-mail.");
            assertThat(fields.get("password").asText()).isEqualTo("Informe a senha.");
        }
        assertThat(sessions.count()).isZero();
    }

    @Test
    void loginComCampoEmBrancoOuSoComEspacosRetorna400IndicandoQualCampo() throws Exception {
        ResponseEntity<String> semEmail = postLoginRaw("{\"email\":\"\",\"password\":\"x\"}");
        ResponseEntity<String> semSenha = postLoginRaw("{\"email\":\"" + CLIENT_EMAIL + "\",\"password\":\"   \"}");
        ResponseEntity<String> soEspacos = postLoginRaw("{\"email\":\"   \",\"password\":\"   \"}");
        ResponseEntity<String> faltaSenha = postLoginRaw("{\"email\":\"" + CLIENT_EMAIL + "\"}");

        assertProblemDetail400(semEmail);
        assertThat(mapper.readTree(semEmail.getBody()).get("invalidFields").has("email")).isTrue();
        assertThat(mapper.readTree(semEmail.getBody()).get("invalidFields").has("password")).isFalse();
        assertProblemDetail400(semSenha);
        assertThat(mapper.readTree(semSenha.getBody()).get("invalidFields").has("password")).isTrue();
        assertProblemDetail400(soEspacos);
        assertThat(mapper.readTree(soEspacos.getBody()).get("invalidFields").size()).isEqualTo(2);
        assertProblemDetail400(faltaSenha);
        assertThat(mapper.readTree(faltaSenha.getBody()).get("invalidFields").has("password")).isTrue();
        assertThat(sessions.count()).isZero();
    }

    @Test
    void loginComCorpoVazioOuJsonMalformadoRetorna400ComProblemDetail() throws Exception {
        for (String payload : new String[] {"", "null", "{nao e json", "[1,2]"}) {
            assertProblemDetail400(postLoginRaw(payload));
        }
        assertThat(sessions.count()).isZero();
    }

    @Test
    void loginComEmailOuSenhaMaioresQueOLimiteRetorna400() throws Exception {
        String longEmail = "a".repeat(250) + "@x.com";
        String longPassword = "p".repeat(201);

        ResponseEntity<String> response = login(longEmail, longPassword);

        assertProblemDetail400(response);
        var fields = mapper.readTree(response.getBody()).get("invalidFields");
        assertThat(fields.get("email").asText()).isEqualTo("E-mail muito longo.");
        assertThat(fields.get("password").asText()).isEqualTo("Senha muito longa.");
    }

    // ---------- Helpers ----------

    private Project seedProject(String email, String digits) {
        return projects.saveAndFlush(Project.create(email, "Empresa " + email, digits, digits, "Representante",
                "B2C", "Rua X, 100", email, OffsetDateTime.now().plusDays(10), OffsetDateTime.now().plusDays(20),
                ADMIN_EMAIL, OffsetDateTime.now()));
    }

    /** Resposta 400 padronizada (ADR 0002): ProblemDetail JSON com status 400 e título. */
    private void assertProblemDetail400(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().getSubtype()).contains("json");
        var body = mapper.readTree(response.getBody());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("title").asText()).isNotBlank();
    }

    private String newProjectJson(String email, String document) {
        // Datas relativas a hoje: com datas fixas o teste quebraria quando elas ficassem no passado.
        LocalDate pdfDeadline = LocalDate.now().plusDays(10);
        LocalDate paymentDeadline = LocalDate.now().plusDays(20);
        return "{\"name\":\"Empresa Nova\",\"document\":\"" + document + "\"," + ADDRESS_JSON + ","
                + "\"area\":\"B2C\",\"email\":\"" + email + "\",\"pdfDeadline\":\"" + pdfDeadline + "\","
                + "\"paymentDeadline\":\"" + paymentDeadline + "\",\"representativeName\":null,\"boothAreaM2\":42.5}";
    }

    private String tokenOf(String email) throws Exception {
        return mapper.readTree(login(email, PASSWORD).getBody()).get("token").asText();
    }

    private ResponseEntity<String> login(String email, String password) {
        return postLoginRaw("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private ResponseEntity<String> postLoginRaw(String json) {
        return rest.postForEntity(url("/auth/login"), new HttpEntity<>(json, jsonHeaders(null)), String.class);
    }

    private ResponseEntity<String> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(String path, String token, String json) {
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(json, jsonHeaders(token)), String.class);
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
