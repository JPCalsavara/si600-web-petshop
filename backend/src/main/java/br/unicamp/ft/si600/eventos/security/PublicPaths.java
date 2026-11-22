package br.unicamp.ft.si600.eventos.security;

import java.util.Set;

import br.unicamp.ft.si600.eventos.payment.http.PaymentHttpPaths;

/**
 * TEC-10: único lugar que lista o que o {@link ActorFilter} NÃO autentica.
 * Um endpoint público novo (ex.: "Esqueci a senha", US-14) entra aqui, com teste de integração.
 */
public final class PublicPaths {
    /** Rotas sem login: ainda não há token (ou é uma verificação de disponibilidade). */
    private static final Set<String> NO_LOGIN = Set.of("/health", "/auth/login", "/auth/forgot-password");

    private PublicPaths() {}

    public static boolean isPublic(String method, String servletPath) {
        return "OPTIONS".equalsIgnoreCase(method) // preflight de CORS não envia token
                || NO_LOGIN.contains(servletPath)
                // /internal/payments e /webhooks/mercadopago têm autenticação própria (assinatura ou credencial técnica)
                || PaymentHttpPaths.protectedPath(servletPath);
    }
}
