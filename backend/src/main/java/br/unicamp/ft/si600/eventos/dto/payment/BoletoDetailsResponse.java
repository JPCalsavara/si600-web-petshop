package br.unicamp.ft.si600.eventos.dto.payment;

/** Dados transitórios: nunca persistir nem logar conteúdo do boleto. */
public record BoletoDetailsResponse(PaymentResponse payment, String ticketUrl,
                                    String digitableLine, String barcodeContent) {
    @Override public String toString() { return "BoletoDetailsResponse[redacted]"; }
}
