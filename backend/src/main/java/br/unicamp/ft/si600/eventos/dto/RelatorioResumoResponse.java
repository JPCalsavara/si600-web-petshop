package br.unicamp.ft.si600.eventos.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

public record RelatorioResumoResponse(
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "#.00")
        BigDecimal totalGeral,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "#.00")
        BigDecimal totalPagamentoGerado,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "#.00")
        BigDecimal totalDisponivel,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT, pattern = "#.00")
        BigDecimal totalCotada
) {}
