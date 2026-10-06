package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectFeeResponse(
        UUID id,
        UUID feeId,
        String name,
        String description,
        FeeType type,
        String measurementUnit,
        BigDecimal quantity,
        ProjectFeeStatus status,
        OffsetDateTime quantityUpdatedAt,
        /** Verdadeiro enquanto o Cliente ainda pode informar ou alterar a quantidade. */
        boolean quantityEditable
) {
    public static ProjectFeeResponse from(ProjectFee pf) {
        boolean editable = pf.getFee().getType() == FeeType.VARIAVEL && !pf.isPaymentGenerated();
        return new ProjectFeeResponse(pf.getId(), pf.getFee().getId(), pf.getFee().getName(),
                pf.getFee().getDescription(), pf.getFee().getType(), pf.getFee().getMeasurementUnit(),
                pf.getQuantity(), pf.getStatus(), pf.getQuantityUpdatedAt(), editable);
    }
}
