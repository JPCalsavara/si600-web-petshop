package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Corpo do PUT /projects/{projectId}/fees/{projectFeeId}/quantity (US-06). */
public record FeeQuantityRequest(
        @NotNull(message = "A quantidade é obrigatória")
        @DecimalMin(value = "0", inclusive = false, message = "A quantidade deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "A quantidade deve ter no máximo 10 dígitos inteiros e 2 decimais")
        BigDecimal quantity
) {}
