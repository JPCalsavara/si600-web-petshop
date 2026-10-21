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

    @Column(name = "quoted_amount", precision = 12, scale = 2)
    private BigDecimal quotedAmount;

    @Column(name = "quoted_by", length = 200)
    private String quotedBy;

    @Column(name = "quoted_at")
    private OffsetDateTime quotedAt;

    protected ProjectFee() {}

    public ProjectFee(Project project, Fee fee) {
        this.project = project;
        this.fee = fee;
        // Só a taxa variável depende do Cliente (quantidade) e do Admin (cotação); as demais já têm valor.
        this.status = fee.getType() == FeeType.VARIAVEL
                ? ProjectFeeStatus.AGUARDANDO_QUANTIDADE : ProjectFeeStatus.DISPONIVEL;
    }

    /**
     * US-06: grava a quantidade e devolve a taxa para a fila de cotação.
     * A cotação anterior deixa de valer (a quantidade mudou), então o valor e a autoria são descartados
     * para o Cliente não ver nem pagar um valor desatualizado.
     */
    public void submitQuantity(BigDecimal quantity, OffsetDateTime at) {
        this.quantity = quantity;
        this.quantityUpdatedAt = at;
        this.quotedAmount = null;
        this.quotedBy = null;
        this.quotedAt = null;
        this.status = ProjectFeeStatus.AGUARDANDO_COTACAO;
    }

    /** US-07: registra cotação ou recotação antes da geração de pagamento. */
    public void quote(BigDecimal amount, String adminId, OffsetDateTime at) {
        if (isPaymentGenerated()) {
            throw new IllegalStateException("A taxa não pode ser cotada após a geração do pagamento.");
        }
        this.quotedAmount = amount;
        this.quotedBy = adminId;
        this.quotedAt = at;
        this.status = ProjectFeeStatus.COTADA;
    }

    public BigDecimal getQuotedAmount() { return quotedAmount; }
    public String getQuotedBy() { return quotedBy; }
    public OffsetDateTime getQuotedAt() { return quotedAt; }

    /**
     * Status exposto pela API. Linhas gravadas antes da introdução de DISPONIVEL (taxa fixa/por metragem em
     * AGUARDANDO_COTACAO) são apresentadas como DISPONIVEL. Exige a taxa carregada (chamar dentro da transação).
     */
    public ProjectFeeStatus effectiveStatus() {
        if (status == ProjectFeeStatus.AGUARDANDO_COTACAO && fee.getType() != FeeType.VARIAVEL) {
            return ProjectFeeStatus.DISPONIVEL;
        }
        return status;
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
