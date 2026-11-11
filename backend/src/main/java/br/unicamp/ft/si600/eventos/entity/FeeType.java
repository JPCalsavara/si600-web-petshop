package br.unicamp.ft.si600.eventos.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum FeeType {
    FIXA, POR_METRAGEM, VARIAVEL;

    // Jackson 3: JsonNode mudou de pacote e de API; um delegate Object mantem a mesma regra
    // (somente texto) sem depender da API da arvore JSON.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static FeeType fromJson(Object value) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("O tipo ou modalidade deve ser informado como texto.");
        }
        return valueOf(text);
    }
}
