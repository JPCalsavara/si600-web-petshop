package br.unicamp.ft.si600.eventos.payment.http;

import br.unicamp.ft.si600.eventos.config.PaymentInternalProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class PaymentBoundaryFilter extends OncePerRequestFilter {
    public static final String PRINCIPAL = "payment.internal.principal";
    public record Principal(String integrationId) {}
    private final PaymentInternalProperties internal;
    private final Environment environment;
    private final ObjectMapper mapper;
    public PaymentBoundaryFilter(PaymentInternalProperties internal, ObjectMapper mapper, Environment environment) {
        this.internal = internal; this.environment = environment; this.mapper = mapper;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PaymentHttpPaths.internal(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        boolean development = environment.acceptsProfiles(Profiles.of("dev"))
                && environment.getProperty("payment.development.enabled", Boolean.class, false);
        String origin = request.getHeader("Origin");
        String allowedOrigin = environment.getProperty("payment.development.origin", "http://localhost:5173");
        if (origin != null && (!development || !origin.equals(allowedOrigin))) {
            reject(response, 403, "Origem de navegador não permitida."); return;
        }
        if (request.getQueryString() != null) { reject(response, 400, "Parâmetros de query não permitidos."); return; }
        var headers = Collections.list(request.getHeaders("Authorization"));
        String authorization = headers.size() == 1 ? headers.getFirst() : "";
        if (authorization.startsWith("Basic ") && development) {
            String user = environment.getProperty("payment.development.username", "");
            String password = environment.getProperty("payment.development.password", "");
            if (user.isBlank() || password.length() < 12) { reject(response, 503, "Configure o acesso de desenvolvimento."); return; }
            String expected = "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(digest(expected), digest(authorization))) { reject(response, 401, "Usuário ou senha inválidos."); return; }
            request.setAttribute(PRINCIPAL, new Principal("development"));
        } else {
            if (origin != null) { reject(response, 403, "Acesso técnico indisponível no navegador."); return; }
            if (!internal.enabled()) { reject(response, development ? 401 : 404, "Recurso não disponível ou autenticação necessária."); return; }
            if (!internal.configured()) { reject(response, 503, "Configuração de pagamento indisponível."); return; }
            if (!authorization.startsWith("Bearer ") || authorization.length() > 519
                    || !MessageDigest.isEqual(digest(internal.token()), digest(authorization.substring(7)))) {
                reject(response, 401, "Credencial técnica obrigatória ou inválida."); return;
            }
            request.setAttribute(PRINCIPAL, new Principal(internal.integrationId()));
        }
        byte[] body = request.getInputStream().readNBytes(65_537);
        if (body.length > 65_536) { reject(response, 413, "Corpo de pagamento excede o limite permitido."); return; }
        chain.doFilter(new PaymentRequestBody(request, body), response);
    }
    private byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 indisponível."); }
    }
    private void reject(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setTitle("Acesso ao módulo de pagamentos não autorizado ou indisponível");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
