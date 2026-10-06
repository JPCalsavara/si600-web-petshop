package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.FeeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CalculatedFeeResponse(
        UUID feeId,
        String name,
        FeeType type,
        BigDecimal calculatedValue,
        String measurementUnit,
        String status,
        OffsetDateTime paymentDeadline
) {}
