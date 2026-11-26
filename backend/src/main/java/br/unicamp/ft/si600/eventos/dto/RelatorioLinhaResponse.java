package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record RelatorioLinhaResponse(
        String nomeTaxa,
        ProjectFeeStatus statusPagamento,
        long quantidadeTaxas,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "#.00")
        BigDecimal totalValorCalculado
) {}
