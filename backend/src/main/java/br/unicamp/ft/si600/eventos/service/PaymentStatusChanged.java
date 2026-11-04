package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.entity.PaymentStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Publicado após commit; entrega local best effort, sem dados do pagador. */
public record PaymentStatusChanged(UUID paymentId, String integrationId, PaymentStatus previousStatus,
                                   PaymentStatus status, OffsetDateTime occurredAt) {}
