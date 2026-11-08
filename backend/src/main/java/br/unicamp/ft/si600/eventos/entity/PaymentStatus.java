package br.unicamp.ft.si600.eventos.entity;

public enum PaymentStatus {
    CRIANDO, GERADO, INCERTO, FALHOU, PAGO, CANCELADO, EXPIRADO,
    REEMBOLSADO_PARCIAL, REEMBOLSADO, CONTESTADO;

    /** Boleto has no reopening operation. Late pending snapshots cannot undo a financial outcome. */
    public boolean accepts(PaymentStatus next) {
        if (this == REEMBOLSADO) return next == REEMBOLSADO;
        if (this == REEMBOLSADO_PARCIAL) return next == REEMBOLSADO_PARCIAL || next == REEMBOLSADO || next == CONTESTADO;
        if (this == PAGO || this == CONTESTADO) {
            return next == this || next == REEMBOLSADO_PARCIAL || next == REEMBOLSADO || next == CONTESTADO;
        }
        if (this == CANCELADO || this == EXPIRADO || this == FALHOU) {
            return next == this || next == PAGO || next == REEMBOLSADO_PARCIAL || next == REEMBOLSADO || next == CONTESTADO;
        }
        return true;
    }
}
