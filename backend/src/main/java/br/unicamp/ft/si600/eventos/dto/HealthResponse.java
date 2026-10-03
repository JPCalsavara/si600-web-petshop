package br.unicamp.ft.si600.eventos.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        String message,
        Instant timestamp
) {}
