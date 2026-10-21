package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.FeeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Linha da lista de taxas do Cliente (US-05), com o valor já calculado ou cotado. */
public record CalculatedFeeResponse(
        /** Id do vínculo taxa-projeto: é o id usado nas rotas de quantidade e cotação. */
        UUID projectFeeId,
        /** Id da taxa no catálogo. */
        UUID feeId,
        String name,
        String description,
        FeeType type,
        /** Fixa/por metragem: valor calculado. Variável: valor cotado pelo Admin (null até a cotação). */
        BigDecimal calculatedValue,
        /** Variável: quantidade informada pelo Cliente. Por metragem: m² ou unidades cobradas. */
        BigDecimal quantity,
        String measurementUnit,
        String status,
        /** Verdadeiro enquanto o Cliente pode informar ou alterar a quantidade (US-06). */
        boolean quantityEditable,
        OffsetDateTime paymentDeadline
) {}
