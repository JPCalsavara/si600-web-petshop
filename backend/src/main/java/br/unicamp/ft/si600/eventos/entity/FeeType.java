package br.unicamp.ft.si600.eventos.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

public enum FeeType {
    FIXA, POR_METRAGEM, VARIAVEL;

    @JsonCreator
    public static FeeType fromJson(JsonNode value) {
        if (!value.isTextual()) {
            throw new IllegalArgumentException("O tipo ou modalidade deve ser informado como texto.");
        }
        return valueOf(value.textValue());
    }
}
