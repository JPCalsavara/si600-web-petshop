package br.unicamp.ft.si600.eventos.dto.payment;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoGateway.Payer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Backend-only input. Integration and currency are assigned by the trusted server context. */
public record InternalBoletoRequest(@NotNull UUID requestId,
        @NotBlank @Size(max=160) String chargeReference,
        @NotNull @DecimalMin("0.01") @Digits(integer=14, fraction=2) BigDecimal amount,
        @NotBlank @Size(max=255) String description, @Min(3) @Max(30) int expirationDays,
        @NotNull @Valid Payer payer) {
    public GenerateBoletoCommand command(String integrationId) {
        return new GenerateBoletoCommand(integrationId, chargeReference, requestId, amount, "BRL", description, expirationDays, payer);
    }
    @Override public String toString() { return "InternalBoletoRequest[redacted]"; }
}
