package br.unicamp.ft.si600.eventos.exception;

import java.util.UUID;

/** A conclusive failure still retains a canonical local payment reservation. */
public class PaymentCreationRejected extends RuntimeException {
    private final UUID paymentId;
    public PaymentCreationRejected(UUID paymentId) { super("Criação rejeitada; reserva preservada."); this.paymentId = paymentId; }
    public UUID paymentId() { return paymentId; }
}
