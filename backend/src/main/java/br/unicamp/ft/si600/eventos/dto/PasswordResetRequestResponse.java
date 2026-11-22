package br.unicamp.ft.si600.eventos.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.unicamp.ft.si600.eventos.entity.PasswordResetRequest;

/** US-14: linha da lista de pedidos pendentes exibida ao Admin. */
public record PasswordResetRequestResponse(UUID id, String email, OffsetDateTime requestedAt) {
    public static PasswordResetRequestResponse from(PasswordResetRequest request) {
        return new PasswordResetRequestResponse(request.getId(), request.getEmail(), request.getRequestedAt());
    }
}
