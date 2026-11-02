package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import java.math.BigDecimal;
import java.util.UUID;

public record ProjectFeeConfigurationResponse(UUID projectFeeId, UUID feeId, String name,
        String description, String type, String measurementUnit, BigDecimal amount,
        BigDecimal amountPerM2, BigDecimal areaPerUnitM2, BigDecimal unitAmount,
        String areaPricingMode, String status, BigDecimal quantity, BigDecimal quotedAmount,
        String quotedBy, String quotedAt) {
    public static ProjectFeeConfigurationResponse from(ProjectFee pf) {
        var fee = pf.getFee();
        return new ProjectFeeConfigurationResponse(pf.getId(), fee.getId(), fee.getName(), fee.getDescription(),
                fee.getType().name(), fee.getMeasurementUnit(), fee.getAmount(), fee.getAmountPerM2(),
                fee.getAreaPerUnitM2(), fee.getUnitAmount(), fee.getAreaPricingMode() == null ? null : fee.getAreaPricingMode().name(),
                pf.effectiveStatus().name(), pf.getQuantity(), pf.getQuotedAmount(), pf.getQuotedBy(),
                pf.getQuotedAt() == null ? null : pf.getQuotedAt().toString());
    }
}
