package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import java.math.BigDecimal;
import java.util.UUID;

public record PendingQuoteResponse(
        UUID projectId, String projectName, String clientEmail,
        UUID projectFeeId, String feeName, String feeType,
        BigDecimal quantity, String measurementUnit, String status,
        BigDecimal quotedAmount, String quotedBy, String quotedAt
) {
    public static PendingQuoteResponse from(ProjectFee pf) {
        return new PendingQuoteResponse(
                pf.getProject().getId(), pf.getProject().getCompanyName(), pf.getProject().getContactEmail(),
                pf.getId(), pf.getFee().getName(), pf.getFee().getType().name(),
                pf.getQuantity(), pf.getFee().getMeasurementUnit(), pf.effectiveStatus().name(),
                pf.getQuotedAmount(), pf.getQuotedBy(),
                pf.getQuotedAt() == null ? null : pf.getQuotedAt().toString());
    }
}
