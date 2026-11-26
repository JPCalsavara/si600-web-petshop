package br.unicamp.ft.si600.eventos.gateway;

import java.time.Duration;

/** Não carrega corpo ou causa externa, que podem conter credenciais e dados pessoais. */
public class MercadoPagoException extends RuntimeException {
    public enum Kind { INVALID_REQUEST, AUTHENTICATION, NOT_FOUND, CONFLICT, PROCESSING_FAILED, LOCKED, RATE_LIMIT,
        UNAVAILABLE, INVALID_RESPONSE, REJECTED }

    private final Kind kind;
    private final int providerStatus;
    private final Duration retryAfter;
    private final boolean creationMayHaveSucceeded;

    public MercadoPagoException(Kind kind, int providerStatus, Duration retryAfter,
                               boolean creationMayHaveSucceeded) {
        super("Falha na integração Mercado Pago: " + kind + " (HTTP " + providerStatus + ").");
        this.kind = kind;
        this.providerStatus = providerStatus;
        this.retryAfter = retryAfter;
        this.creationMayHaveSucceeded = creationMayHaveSucceeded;
    }

    public Kind kind() { return kind; }
    /** Zero indica falha sem resposta HTTP. */
    public int providerStatus() { return providerStatus; }
    public Duration retryAfter() { return retryAfter; }
    public boolean creationMayHaveSucceeded() { return creationMayHaveSucceeded; }
}
