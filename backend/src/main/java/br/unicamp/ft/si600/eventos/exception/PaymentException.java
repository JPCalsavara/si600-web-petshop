package br.unicamp.ft.si600.eventos.exception;

import java.util.UUID;

public class PaymentException extends RuntimeException {
    public enum Kind { INVALID_INPUT, CONFLICT, NOT_FOUND, CONFIGURATION, RECOVERY_REQUIRED,
        VERIFICATION_FAILED, STORAGE_FAILURE, AMBIENT_TRANSACTION, STALE_SNAPSHOT }
    private final Kind kind;
    private final UUID paymentId;

    public PaymentException(Kind kind, UUID paymentId) {
        super("Operação de pagamento não concluída: " + kind + ".");
        this.kind = kind;
        this.paymentId = paymentId;
    }
    public Kind kind() { return kind; }
    public UUID paymentId() { return paymentId; }
}
