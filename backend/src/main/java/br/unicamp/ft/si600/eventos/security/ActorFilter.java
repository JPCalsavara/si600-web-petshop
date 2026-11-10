package br.unicamp.ft.si600.eventos.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.unicamp.ft.si600.eventos.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * US-01: identifica quem está chamando a API.
 * Fluxo normal: cabeçalho "Authorization: Bearer <token>" emitido por POST /auth/login.
 * Os cabeçalhos X-Authenticated-User-* só valem com app.security.legacy-headers=true
 * (usado apenas nos testes antigos); por padrão são ignorados, pois qualquer um poderia forjá-los.
 * Por isso a aplicação se recusa a subir com o profile "prod" e essa flag ligada.
 */
@Component
public class ActorFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = Actor.class.getName();
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;
    private final boolean legacyHeadersEnabled;

    public ActorFilter(AuthService authService, Environment environment,
                       @Value("${app.security.legacy-headers:false}") boolean legacyHeadersEnabled) {
        if (legacyHeadersEnabled && environment.acceptsProfiles(Profiles.of("prod"))) {
            // Com a flag ligada qualquer cliente forja "X-Authenticated-User-Role: ADMIN": falha na subida.
            throw new IllegalStateException(
                    "app.security.legacy-headers=true não é permitido no profile prod: "
                            + "os cabeçalhos X-Authenticated-User-* podem ser forjados por qualquer cliente.");
        }
        this.authService = authService;
        this.legacyHeadersEnabled = legacyHeadersEnabled;
    }

    /** Extrai o token do cabeçalho Authorization, ou null se não houver. */
    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/health".equals(path) || "/auth/login".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = bearerToken(request);
        if (token != null) {
            Optional<Actor> actor = authService.authenticate(token);
            if (actor.isEmpty()) {
                writeUnauthorized(response, "Sessão inválida", "Sessão expirada ou inválida. Faça login novamente.");
                return;
            }
            request.setAttribute(ACTOR_ATTRIBUTE, actor.get());
            filterChain.doFilter(request, response);
            return;
        }

        if (!legacyHeadersEnabled) {
            writeUnauthorized(response, "Autenticação necessária", "Faça login para continuar.");
            return;
        }

        String id = request.getHeader("X-Authenticated-User-Id");
        String role = request.getHeader("X-Authenticated-User-Role");
        if (id == null || id.isBlank() || role == null || role.isBlank()) {
            writeUnauthorized(response, "Autenticação necessária",
                    "A identidade autenticada deve ser fornecida pelo provedor de autenticação.");
            return;
        }

        ActorRole actorRole;
        try {
            actorRole = ActorRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            writeUnauthorized(response, "Identidade inválida", "O papel informado não é suportado.");
            return;
        }

        // A cadeia roda fora do try: erros downstream não podem virar 401 de "papel inválido".
        request.setAttribute(ACTOR_ATTRIBUTE, new Actor(id.trim(), actorRole));
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String title, String detail) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"" + title + "\",\"status\":401,\"detail\":\"" + detail + "\"}");
    }
}
