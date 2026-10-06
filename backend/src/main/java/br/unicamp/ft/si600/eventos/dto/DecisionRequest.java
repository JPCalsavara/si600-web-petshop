package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.NotBlank;

public record DecisionRequest(
        @NotBlank(message = "A justificativa é obrigatória") String justification
) {}
