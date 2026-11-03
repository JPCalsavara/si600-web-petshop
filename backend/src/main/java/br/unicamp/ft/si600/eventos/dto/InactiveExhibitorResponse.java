package br.unicamp.ft.si600.eventos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InactiveExhibitorResponse(
        UUID projectId,
        String companyName,
        String representativeName,
        String contactEmail,
        OffsetDateTime createdAt,
        OffsetDateTime lastAccessAt,
        Long daysSinceLastAccess,
        boolean neverAccessed
) {}
