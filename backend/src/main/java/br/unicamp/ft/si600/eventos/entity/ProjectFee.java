package br.unicamp.ft.si600.eventos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Taxa do catálogo vinculada a um projeto. A US-06 usa a quantidade das taxas variáveis. */
@Entity
@Table(name = "project_fees", indexes = {
        @Index(name = "idx_project_fees_project_id", columnList = "project_id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_project_fees_project_fee", columnNames = {"project_id", "fee_id"}))
public class ProjectFee {
    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_id", nullable = false)
    private Fee fee;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private ProjectFeeStatus status;

    @Column(precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "quantity_updated_at")
    private OffsetDateTime quantityUpdatedAt;

    protected ProjectFee() {}

    public ProjectFee(Project project, Fee fee) {
        this.project = project;
        this.fee = fee;
        this.status = fee.getType() == FeeType.VARIAVEL
                ? ProjectFeeStatus.AGUARDANDO_QUANTIDADE : ProjectFeeStatus.COTADA;
    }

    /** US-06: grava a quantidade e devolve a taxa para a fila de cotação. */
    public void submitQuantity(BigDecimal quantity, OffsetDateTime at) {
        this.quantity = quantity;
        this.quantityUpdatedAt = at;
        this.status = ProjectFeeStatus.AGUARDANDO_COTACAO;
    }

    public boolean isPaymentGenerated() { return status == ProjectFeeStatus.PAGAMENTO_GERADO; }

    public void markPaymentGenerated() { this.status = ProjectFeeStatus.PAGAMENTO_GERADO; }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public Fee getFee() { return fee; }
    public ProjectFeeStatus getStatus() { return status; }
    public BigDecimal getQuantity() { return quantity; }
    public OffsetDateTime getQuantityUpdatedAt() { return quantityUpdatedAt; }
}
