package br.unicamp.ft.si600.eventos.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

public enum AreaPricingMode {
    VALOR_POR_M2, UNIDADES_POR_INTERVALO;

    @JsonCreator
    public static AreaPricingMode fromJson(JsonNode value) {
        if (!value.isTextual()) {
            throw new IllegalArgumentException("O tipo ou modalidade deve ser informado como texto.");
        }
        return valueOf(value.textValue());
    }
}
