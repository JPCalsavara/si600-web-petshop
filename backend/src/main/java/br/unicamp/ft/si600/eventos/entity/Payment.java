package br.unicamp.ft.si600.eventos.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Objects;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_request", columnNames = {"integration_id", "request_id"}),
        @UniqueConstraint(name = "uq_payment_obligation", columnNames = {"integration_id", "obligation_reference"}),
        @UniqueConstraint(name = "uq_payment_idempotency", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uq_payment_order", columnNames = "mp_order_id"),
        @UniqueConstraint(name = "uq_payment_external", columnNames = "mp_payment_id")
})
@Check(constraints = "amount > 0 and currency = 'BRL'")
public class Payment {
    @Id @GeneratedValue private UUID id;
    @Version private Long version;
    @Column(name = "integration_id", nullable = false, length = 100) private String integrationId;
    @Column(name = "obligation_reference", nullable = false, length = 160) private String obligationReference;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "idempotency_key", nullable = false) private UUID idempotencyKey;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false, precision = 16, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentStatus status;
    @Column(name = "mp_order_id", length = 128) private String orderId;
    @Column(name = "mp_payment_id", length = 128) private String paymentId;
    @Column(name = "mp_status", length = 128) private String providerStatus;
    @Column(name = "mp_status_detail", length = 128) private String providerStatusDetail;
    @Column(name = "mp_payment_status", length = 128) private String providerPaymentStatus;
    @Column(name = "mp_payment_status_detail", length = 128) private String providerPaymentStatusDetail;
    @Column(name = "last_error_code", length = 64) private String lastErrorCode;
    @Column(name = "last_http_status", nullable = false) private int lastHttpStatus;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @Column(name = "expires_at") private OffsetDateTime expiresAt;

    protected Payment() {}

    public Payment(String integrationId, String obligationReference, UUID requestId,
                   UUID idempotencyKey, String fingerprint, BigDecimal amount, String currency,
                   OffsetDateTime now) {
        this.integrationId = integrationId;
        this.obligationReference = obligationReference;
        this.requestId = requestId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = fingerprint;
        this.amount = amount;
        this.currency = currency;
        this.status = PaymentStatus.CRIANDO;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyProviderSnapshot(String orderId, String paymentId, String providerStatus,
                                     String providerDetail, String paymentStatus, String paymentDetail,
                                     OffsetDateTime expiresAt, int httpStatus, PaymentStatus next,
                                     OffsetDateTime now) {
        if (!status.accepts(next)) return;
        // Repeated identical GETs are reads: do not advance version/timestamp or contend on a phantom update.
        if (status == next && Objects.equals(this.orderId, orderId)
                && (paymentId == null || Objects.equals(this.paymentId, paymentId))
                && Objects.equals(this.providerStatus, providerStatus) && Objects.equals(this.providerStatusDetail, providerDetail)
                && Objects.equals(this.providerPaymentStatus, paymentStatus) && Objects.equals(this.providerPaymentStatusDetail, paymentDetail)
                && (expiresAt == null || Objects.equals(this.expiresAt, expiresAt))
                && this.lastHttpStatus == httpStatus && this.lastErrorCode == null) return;
        this.orderId = orderId;
        if (paymentId != null) this.paymentId = paymentId;
        this.providerStatus = providerStatus;
        this.providerStatusDetail = providerDetail;
        this.providerPaymentStatus = paymentStatus;
        this.providerPaymentStatusDetail = paymentDetail;
        if (expiresAt != null) this.expiresAt = expiresAt;
        this.lastHttpStatus = httpStatus;
        this.lastErrorCode = null;
        this.status = next;
        this.updatedAt = now;
    }

    public void recordCreationFailure(boolean uncertain, String errorCode, int httpStatus, OffsetDateTime now) {
        if (orderId != null || status == PaymentStatus.PAGO) return;
        status = uncertain ? PaymentStatus.INCERTO : PaymentStatus.FALHOU;
        lastErrorCode = errorCode;
        lastHttpStatus = httpStatus;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public String getIntegrationId() { return integrationId; }
    public String getObligationReference() { return obligationReference; }
    public UUID getRequestId() { return requestId; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getOrderId() { return orderId; }
    public String getPaymentId() { return paymentId; }
    public String getProviderStatus() { return providerStatus; }
    public String getProviderStatusDetail() { return providerStatusDetail; }
    public String getProviderPaymentStatus() { return providerPaymentStatus; }
    public String getProviderPaymentStatusDetail() { return providerPaymentStatusDetail; }
    public String getLastErrorCode() { return lastErrorCode; }
    public int getLastHttpStatus() { return lastHttpStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
}
