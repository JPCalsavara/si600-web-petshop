package br.unicamp.ft.si600.eventos.dto.payment;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.Payer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record GenerateBoletoCommand(
        @NotBlank @Size(max = 100) String integrationId,
        @NotBlank @Size(max = 160) String obligationReference,
        @NotNull UUID requestId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "BRL") String currency,
        @NotBlank @Size(max = 255) String description,
        @Min(3) @Max(30) int expirationDays,
        @NotNull @Valid Payer payer) {
    @Override public String toString() { return "GenerateBoletoCommand[redacted]"; }
}
