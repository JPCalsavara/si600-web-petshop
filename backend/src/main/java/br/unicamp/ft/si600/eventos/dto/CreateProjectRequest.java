package br.unicamp.ft.si600.eventos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corpo do POST /projects (US-03). Datas em yyyy-mm-dd, como o frontend envia. */
public record CreateProjectRequest(
        @NotBlank(message = "O nome/razão social é obrigatório")
        @Size(max = 200, message = "O nome deve ter no máximo 200 caracteres") String name,
        @NotBlank(message = "O CPF/CNPJ é obrigatório")
        @Size(max = 30, message = "CPF/CNPJ muito longo") String document,
        @Valid @NotNull(message = "O endereço é obrigatório") ProjectAddressRequest address,
        @NotBlank(message = "A área do estande é obrigatória")
        @Pattern(regexp = "B2C|Music Hub|Music Sport|Internacional",
                message = "Área inválida. Use B2C, Music Hub, Music Sport ou Internacional") String area,
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 254, message = "E-mail muito longo") String email,
        @NotNull(message = "A data limite de envio do PDF é obrigatória") LocalDate pdfDeadline,
        @NotNull(message = "A data limite de pagamento é obrigatória") LocalDate paymentDeadline,
        @Size(max = 200, message = "O nome do representante deve ter no máximo 200 caracteres") String representativeName,
        List<UUID> feeIds,
        @NotNull(message = "A metragem do estande é obrigatória")
        @DecimalMin(value = "0.01", message = "A metragem do estande deve ser maior que zero")
        @DecimalMax(value = "9999999999.99", message = "A metragem do estande excede o limite permitido")
        BigDecimal boothAreaM2
) {}
