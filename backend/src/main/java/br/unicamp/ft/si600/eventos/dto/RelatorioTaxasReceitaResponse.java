package br.unicamp.ft.si600.eventos.dto;

import java.util.List;

public record RelatorioTaxasReceitaResponse(
        List<RelatorioLinhaResponse> linhas,
        RelatorioResumoResponse resumo
) {}
