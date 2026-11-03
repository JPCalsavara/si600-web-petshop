package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PendingApprovalResponse(
        UUID projectId,
        String companyName,
        String pdfOriginalFilename,
        OffsetDateTime pdfUploadedAt,
        BigDecimal boothAreaM2,
        ProjectStatus status
) {}
