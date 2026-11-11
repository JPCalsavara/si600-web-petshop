package br.unicamp.ft.si600.eventos.dto.payment;

import br.unicamp.ft.si600.eventos.entity.Payment;
import br.unicamp.ft.si600.eventos.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(UUID id, String integrationId, String obligationReference, UUID requestId,
                              BigDecimal amount, String currency, PaymentStatus status, String orderId,
                              String paymentId, String providerStatus, String providerStatusDetail,
                              String providerPaymentStatus, String providerPaymentStatusDetail,
                              String lastErrorCode, int lastHttpStatus,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime expiresAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getIntegrationId(), p.getObligationReference(), p.getRequestId(),
                p.getAmount(), p.getCurrency(), p.getStatus(), p.getOrderId(), p.getPaymentId(),
                p.getProviderStatus(), p.getProviderStatusDetail(), p.getProviderPaymentStatus(),
                p.getProviderPaymentStatusDetail(), p.getLastErrorCode(), p.getLastHttpStatus(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getExpiresAt());
    }
}
