package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Lista completa das taxas de catálogo que devem ficar vinculadas ao projeto. */
public record ProjectFeeConfigurationRequest(@NotNull List<UUID> feeIds) {}
