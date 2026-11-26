package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.dto.payment.BoletoDetailsResponse;
import br.unicamp.ft.si600.eventos.dto.payment.PaymentResponse;
import br.unicamp.ft.si600.eventos.entity.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pagamento de uma taxa do projeto (US-11). Os dados do boleto (link, linha digitável e código de barras)
 * só vêm na consulta GET .../payment: o Mercado Pago é a fonte deles e o sistema não os grava.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeePaymentResponse(
        UUID projectFeeId,
        BigDecimal amount,
        PaymentStatus status,
        OffsetDateTime expiresAt,
        String ticketUrl,
        String digitableLine,
        String barcodeContent
) {
    public static FeePaymentResponse of(UUID projectFeeId, PaymentResponse payment) {
        return new FeePaymentResponse(projectFeeId, payment.amount(), payment.status(), payment.expiresAt(),
                null, null, null);
    }

    public static FeePaymentResponse of(UUID projectFeeId, BoletoDetailsResponse boleto) {
        PaymentResponse payment = boleto.payment();
        return new FeePaymentResponse(projectFeeId, payment.amount(), payment.status(), payment.expiresAt(),
                boleto.ticketUrl(), boleto.digitableLine(), boleto.barcodeContent());
    }

    /** Evita expor o conteúdo do boleto em logs acidentais. */
    @Override
    public String toString() {
        return "FeePaymentResponse[projectFeeId=" + projectFeeId + ", status=" + status + "]";
    }
}
