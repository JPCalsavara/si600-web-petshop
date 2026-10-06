package br.unicamp.ft.si600.eventos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ActorFilter extends OncePerRequestFilter {
    public static final String ACTOR_ATTRIBUTE = Actor.class.getName();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/health".equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
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
