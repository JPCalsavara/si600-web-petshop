package br.unicamp.ft.si600.eventos.gateway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Integração técnica; o chamador backend autoriza e fornece dados de origem confiável. */
public interface MercadoPagoGateway {
    OrderResult createBoleto(BoletoRequest request);
    OrderResult getOrder(String orderId);

    /** Mesma tentativa exige a mesma chave UUID v4 e exatamente os mesmos dados. */
    record BoletoRequest(
            @NotNull UUID externalReference,
            @NotNull UUID idempotencyKey,
            @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @Min(3) @Max(30) int expirationDays,
            @NotNull @Valid Payer payer) {
        @Override public String toString() { return "BoletoRequest[redacted]"; }
    }

    record Payer(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String firstName,
            @NotBlank @Size(max = 200) String lastName,
            @NotBlank @Pattern(regexp = "CPF|CNPJ") String identificationType,
            @NotBlank @Pattern(regexp = "[0-9]{11}|[0-9]{14}") String identificationNumber,
            @NotNull @Valid Address address) {
        @Override public String toString() { return "Payer[redacted]"; }
    }

    record Address(
            @NotBlank @Pattern(regexp = "[0-9]{8}") String zipCode,
            @NotBlank @Size(max = 200) String streetName,
            @NotBlank @Size(max = 30) String streetNumber,
            @NotBlank @Size(max = 100) String neighborhood,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String state) {
        @Override public String toString() { return "Address[redacted]"; }
    }

    /** Estados externos preservados; boleto é transitório e não deve ser logado ou persistido. */
    record OrderResult(String orderId, String externalReference, BigDecimal totalAmount,
                       String status, String statusDetail, String paymentId,
                       String paymentStatus, String paymentStatusDetail,
                       String ticketUrl, String digitableLine, String barcodeContent,
                       OffsetDateTime paymentExpiresAt, int providerHttpStatus) {
        @Override public String toString() { return "OrderResult[redacted]"; }
    }
}
