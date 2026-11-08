package br.unicamp.ft.si600.eventos.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import static br.unicamp.ft.si600.eventos.gateway.MercadoPagoException.Kind;

/** Sem polling, retries implícitos, persistência ou conhecimento de Projeto/Taxa. */
public final class MercadoPagoGateway {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MercadoPagoGateway.class);
    private final RestClient client;
    private final ObjectMapper mapper;
    private final Validator validator;

    public MercadoPagoGateway(MercadoPagoProperties properties, ObjectMapper mapper, Validator validator) {
        validateConfiguration(properties);
        this.mapper = mapper;
        this.validator = validator;
        HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    public OrderResult createBoleto(BoletoRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new IllegalArgumentException("Dados de boleto inválidos ou incompletos.");
        }
        Payer payer = request.payer();
        int expectedDigits = "CPF".equals(payer.identificationType()) ? 11 : 14;
        if (payer.identificationNumber().length() != expectedDigits
                || request.idempotencyKey().version() != 4 || request.idempotencyKey().variant() != 2) {
            throw new IllegalArgumentException("Documento ou chave de idempotência inválidos.");
        }
        String amount = request.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        Address address = payer.address();
        Map<String, Object> body = Map.of(
                "type", "online", "processing_mode", "automatic",
                "external_reference", request.externalReference().toString(),
                "total_amount", amount, "description", request.description(),
                "payer", Map.of("email", payer.email(), "first_name", payer.firstName(),
                        "last_name", payer.lastName(), "identification", Map.of(
                                "type", payer.identificationType(), "number", payer.identificationNumber()),
                        "address", Map.of("zip_code", address.zipCode(), "street_name", address.streetName(),
                                "street_number", address.streetNumber(), "neighborhood", address.neighborhood(),
                                "city", address.city(), "state", address.state())),
                "transactions", Map.of("payments", List.of(Map.of("amount", amount,
                        "payment_method", Map.of("id", "boleto", "type", "ticket"),
                        "expiration_time", "P" + request.expirationDays() + "D"))));
        OrderResult result = execute(client.post().uri("/v1/orders")
                .header("X-Idempotency-Key", request.idempotencyKey().toString())
                .contentType(MediaType.APPLICATION_JSON).body(body), true, request.externalReference().toString());
        if (!request.externalReference().toString().equals(result.externalReference())
                || request.amount().compareTo(result.totalAmount()) != 0) {
            throw new MercadoPagoException(Kind.INVALID_RESPONSE, 201, null, true);
        }
        return result;
    }

    public OrderResult getOrder(String orderId) {
        if (orderId == null || !orderId.matches("ORD[A-Z0-9]{1,100}")) {
            throw new IllegalArgumentException("Identificador de order inválido.");
        }
        OrderResult result = execute(client.get().uri("/v1/orders/{id}", orderId), false, orderId);
        if (!orderId.equals(result.orderId())) {
            throw new MercadoPagoException(Kind.INVALID_RESPONSE, 200, null, false);
        }
        return result;
    }

    private OrderResult execute(RestClient.RequestHeadersSpec<?> request, boolean creating, String reference) {
        try {
            return request.exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                log.info("MP_ORDERS operation={} reference={} http={}", creating ? "CREATE" : "GET", reference, status);
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new MercadoPagoException(classify(status), status,
                            retryAfter(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)),
                            creating && (status >= 500 || status == 402 || status == 408 || status == 409 || status == 423));
                }
                try {
                    return parse(mapper.readTree(response.getBody()), status);
                } catch (IOException | IllegalArgumentException ex) {
                    throw new MercadoPagoException(Kind.INVALID_RESPONSE, status, null, creating);
                }
            });
        } catch (ResourceAccessException ex) {
            log.info("MP_ORDERS operation={} reference={} http=0", creating ? "CREATE" : "GET", reference);
            throw new MercadoPagoException(Kind.UNAVAILABLE, 0, null, creating);
        } catch (RestClientException ex) {
            throw new MercadoPagoException(Kind.INVALID_RESPONSE, 0, null, creating);
        }
    }

    private OrderResult parse(JsonNode json, int httpStatus) {
        String id = required(json, "id");
        if (!id.matches("ORD[A-Z0-9]{1,100}")) throw new IllegalArgumentException();
        String status = required(json, "status");
        String reference = required(json, "external_reference");
        BigDecimal amount = new BigDecimal(required(json, "total_amount"));
        if (amount.signum() <= 0) throw new IllegalArgumentException();
        JsonNode payments = json.path("transactions").path("payments");
        JsonNode payment = mapper.createObjectNode();
        if (payments.isArray() && payments.size() == 1) {
            payment = payments.get(0);
            if (!required(payment, "id").matches("PAY[A-Z0-9]{1,100}")) throw new IllegalArgumentException();
            required(payment, "status");
            JsonNode method = payment.path("payment_method");
            if (!method.isMissingNode()) {
                String methodId = required(method, "id");
                String methodType = required(method, "type");

                if (!("boleto".equals(methodId) || "bolbradesco".equals(methodId))
                        || !"ticket".equals(methodType)) {
                  throw new IllegalArgumentException(
                          "Método de pagamento incompatível com boleto."
                  );
                }
        }
        } else if ((!payments.isMissingNode() && (!payments.isArray() || !payments.isEmpty()))
                || !("created".equals(status) || "processing".equals(status)
                || "failed".equals(status) || "canceled".equals(status))) {
            throw new IllegalArgumentException();
        }
        JsonNode method = payment.path("payment_method");
        String ticketUrl = optional(method, "ticket_url");
        if (ticketUrl != null) {
            URI ticket = URI.create(ticketUrl);
            if (!"https".equals(ticket.getScheme()) || ticket.getHost() == null || ticket.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        }
        return new OrderResult(id, reference, amount, status, optional(json, "status_detail"),
                optional(payment, "id"), optional(payment, "status"), optional(payment, "status_detail"),
                ticketUrl, optional(method, "digitable_line"), optional(method, "barcode_content"),
                expirationDate(payment), httpStatus);
    }

    private static OffsetDateTime expirationDate(JsonNode payment) {
        String date = optional(payment, "date_of_expiration");
        if (date == null) return null;
        try {
            return OffsetDateTime.parse(date);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Vencimento retornado pelo provedor inválido.");
        }
    }

    private static String required(JsonNode json, String field) {
        String value = optional(json, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException();
        return value;
    }

    private static String optional(JsonNode json, String field) {
        if (json == null || json.isNull()) throw new IllegalArgumentException();
        JsonNode value = json.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException();
        return value.textValue();
    }

    private static Kind classify(int status) {
        return switch (status) {
            case 400, 422 -> Kind.INVALID_REQUEST;
            case 401, 403 -> Kind.AUTHENTICATION;
            case 402 -> Kind.PROCESSING_FAILED;
            case 404 -> Kind.NOT_FOUND;
            case 409 -> Kind.CONFLICT;
            case 423 -> Kind.LOCKED;
            case 429 -> Kind.RATE_LIMIT;
            default -> status >= 500 ? Kind.UNAVAILABLE : Kind.REJECTED;
        };
    }

    private static Duration retryAfter(String value) {
        if (value == null) return null;
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ex) {
            try {
                Duration remaining = Duration.between(Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return remaining.isNegative() ? Duration.ZERO : remaining;
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private static void validateConfiguration(MercadoPagoProperties properties) {
        if (properties == null || properties.accessToken() == null || properties.accessToken().isBlank()
                || properties.accessToken().chars().anyMatch(Character::isWhitespace)
                || properties.baseUrl() == null || properties.connectTimeout() == null || properties.readTimeout() == null
                || properties.connectTimeout().isNegative() || properties.connectTimeout().isZero()
                || properties.readTimeout().isNegative() || properties.readTimeout().isZero()) {
            throw new IllegalArgumentException("Configuração Mercado Pago incompleta ou inválida.");
        }
        URI base;
        try {
            base = URI.create(properties.baseUrl());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("MP_BASE_URL inválida.");
        }
        boolean localStub = "http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        if ((!"https".equals(base.getScheme()) && !localStub) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("MP_BASE_URL exige HTTPS ou stub HTTP em 127.0.0.1.");
        }
    }
    /** Mesma tentativa exige a mesma chave UUID v4 e exatamente os mesmos dados. */
    public record BoletoRequest(
            @NotNull UUID externalReference,
            @NotNull UUID idempotencyKey,
            @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 255) String description,
            @Min(3) @Max(30) int expirationDays,
            @NotNull @Valid Payer payer) {
        @Override public String toString() { return "BoletoRequest[redacted]"; }
    }

    public record Payer(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String firstName,
            @NotBlank @Size(max = 200) String lastName,
            @NotBlank @Pattern(regexp = "CPF|CNPJ") String identificationType,
            @NotBlank @Pattern(regexp = "[0-9]{11}|[0-9]{14}") String identificationNumber,
            @NotNull @Valid Address address) {
        @Override public String toString() { return "Payer[redacted]"; }
    }

    public record Address(
            @NotBlank @Pattern(regexp = "[0-9]{8}") String zipCode,
            @NotBlank @Size(max = 200) String streetName,
            @NotBlank @Size(max = 30) String streetNumber,
            @NotBlank @Size(max = 100) String neighborhood,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO") String state) {
        @Override public String toString() { return "Address[redacted]"; }
    }

    /** Estados externos preservados; boleto é transitório e não deve ser logado ou persistido. */
    public record OrderResult(String orderId, String externalReference, BigDecimal totalAmount,
                       String status, String statusDetail, String paymentId,
                       String paymentStatus, String paymentStatusDetail,
                       String ticketUrl, String digitableLine, String barcodeContent,
                       OffsetDateTime paymentExpiresAt, int providerHttpStatus) {
        @Override public String toString() { return "OrderResult[redacted]"; }
    }
}
