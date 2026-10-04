package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String companyName,
        String document,
        String representativeName,
        String category,
        OffsetDateTime pdfDeadline,
        ProjectStatus status,
        BigDecimal boothAreaM2,
        String pdfOriginalFilename,
        Long pdfSizeBytes,
        OffsetDateTime pdfUploadedAt,
        String decisionBy,
        OffsetDateTime decisionAt,
        String rejectionJustification
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getCompanyName(), p.getDocument(),
                p.getRepresentativeName(), p.getCategory(), p.getPdfDeadline(),
                p.getStatus(), p.getBoothAreaM2(), p.getPdfOriginalFilename(),
                p.getPdfSizeBytes(), p.getPdfUploadedAt(), p.getDecisionBy(),
                p.getDecisionAt(), p.getRejectionJustification());
    }
}
