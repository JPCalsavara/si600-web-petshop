package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ApproveProjectRequest(
    @NotNull(message = "A área aprovada em m² é obrigatória")
    @DecimalMin(value = "0.01", message = "A metragem deve ser maior que zero")
    BigDecimal approvedAreaM2
) {}
