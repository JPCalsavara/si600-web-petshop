package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.*;
import java.math.BigDecimal;
import java.util.UUID;

public record FeeResponse(UUID id, String name, String description, FeeType type, boolean active,
                          AreaPricingMode areaPricingMode, BigDecimal amount, BigDecimal amountPerM2,
                          BigDecimal areaPerUnitM2, BigDecimal unitAmount, String measurementUnit) {
    public static FeeResponse from(Fee fee) {
        return new FeeResponse(fee.getId(), fee.getName(), fee.getDescription(), fee.getType(), fee.isActive(),
                fee.getAreaPricingMode(), fee.getAmount(), fee.getAmountPerM2(), fee.getAreaPerUnitM2(),
                fee.getUnitAmount(), fee.getMeasurementUnit());
    }
}
