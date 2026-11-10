package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
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
        String rejectionJustification,
        String address,
        String contactEmail,
        OffsetDateTime paymentDeadline,
        AlertTag alertTag,
        /** Só vem preenchida na resposta do POST /projects (Admin), e só quando a conta foi criada agora. */
        @JsonInclude(JsonInclude.Include.NON_NULL) String provisionalPassword
) {
    public static ProjectResponse from(Project p, AlertTag alertTag) {
        return new ProjectResponse(p.getId(), p.getCompanyName(), p.getDocument(),
                p.getRepresentativeName(), p.getCategory(), p.getPdfDeadline(),
                p.getStatus(), p.getBoothAreaM2(), p.getPdfOriginalFilename(),
                p.getPdfSizeBytes(), p.getPdfUploadedAt(), p.getDecisionBy(),
                p.getDecisionAt(), p.getRejectionJustification(),
                p.getAddress(), p.getContactEmail(), p.getPaymentDeadline(),
                alertTag != null ? alertTag : AlertTag.EM_DIA, null);
    }

    public ProjectResponse withProvisionalPassword(String password) {
        return new ProjectResponse(id, companyName, document, representativeName, category, pdfDeadline,
                status, boothAreaM2, pdfOriginalFilename, pdfSizeBytes, pdfUploadedAt, decisionBy,
                decisionAt, rejectionJustification, address, contactEmail, paymentDeadline,
                alertTag, password);
    }

    public static ProjectResponse from(Project p) {
        return from(p, AlertTag.EM_DIA);
    }
}
