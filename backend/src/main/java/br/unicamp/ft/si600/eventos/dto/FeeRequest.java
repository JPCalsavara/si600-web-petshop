package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.AreaPricingMode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Definição completa usada na criação e na edição; atividade é alterada separadamente. */
public record FeeRequest(
        @NotBlank(message = "O nome é obrigatório") @Size(max = 200) String name,
        @Size(max = 2000) String description,
        @NotNull(message = "O tipo é obrigatório") FeeType type,
        AreaPricingMode areaPricingMode,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal amountPerM2,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal areaPerUnitM2,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal unitAmount,
        @Size(max = 100) String measurementUnit
) {}
