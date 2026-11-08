package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.exception.PaymentException;
import br.unicamp.ft.si600.eventos.gateway.MercadoPagoException;
import br.unicamp.ft.si600.eventos.payment.http.MercadoPagoWebhookSignature;
import br.unicamp.ft.si600.eventos.service.PaymentService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;

@RestController
@RequestMapping("/webhooks/mercadopago")
public class MercadoPagoWebhookController {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);
    private final MercadoPagoWebhookSignature signature;
    private final PaymentService payments;
    private final ObjectMapper mapper;

    public MercadoPagoWebhookController(MercadoPagoWebhookSignature signature, PaymentService payments, ObjectMapper mapper) {
        this.signature = signature;
        this.payments = payments;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        String orderId;
        try {
            orderId = signature.verify(request);
        } catch (ResponseStatusException ex) {
            log.info("MP_WEBHOOK validation=rejected http={}", ex.getStatusCode().value());
            throw ex;
        }
        String event = readEvent(request, orderId);
        log.info("MP_WEBHOOK order={} event={} validation=accepted", orderId, event);
        try {
            var payment = payments.synchronizeNotifiedOrder(orderId);
            log.info("MP_WEBHOOK order={} event={} synchronization=completed status={}", orderId, event, payment.status());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).build();
        } catch (PaymentException ex) {
            String reason = ex.kind() == PaymentException.Kind.NOT_FOUND ? "payment_not_found_local" : ex.kind().name();
            log.warn("MP_WEBHOOK order={} event={} synchronization={}", orderId, event, reason);
            int status = switch (ex.kind()) {
                case NOT_FOUND -> 404;
                case CONFIGURATION, STORAGE_FAILURE, STALE_SNAPSHOT -> 503;
                default -> 409;
            };
            throw new ResponseStatusException(HttpStatus.valueOf(status), "Sincronização não concluída: " + reason);
        } catch (MercadoPagoException ex) {
            String reason = switch (ex.kind()) {
                case NOT_FOUND -> "provider_order_not_found";
                case UNAVAILABLE, RATE_LIMIT, LOCKED -> "provider_temporary_failure";
                default -> "provider_request_failed";
            };
            log.warn("MP_WEBHOOK order={} event={} synchronization={}", orderId, event, reason);
            if ("provider_order_not_found".equals(reason)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sincronização não concluída: " + reason);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta ou persistência indisponível; reentregar notificação.");
        } catch (DataAccessException ex) {
            log.warn("MP_WEBHOOK order={} event={} synchronization=persistence_failure", orderId, event);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta ou persistência indisponível; reentregar notificação.");
        }
    }

    private String readEvent(HttpServletRequest request, String orderId) {
        try {
            byte[] body = request.getInputStream().readNBytes(65_537);
            if (body.length > 65_536) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Notificação muito grande.");
            var json = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(body);
            if (json == null || !json.isObject() || !json.path("data").path("id").isTextual()
                    || !orderId.equalsIgnoreCase(json.path("data").path("id").textValue())
                    || (json.has("type") && !"order".equals(json.path("type").asText()))
                    || (json.has("action") && (!json.path("action").isTextual()
                    || !json.path("action").asText().matches("order\\.[a-z_]{1,40}")))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notificação Orders inválida ou ID divergente.");
            }
            return json.path("action").asText("order");
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON de notificação inválido.");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> problem(ResponseStatusException ex) {
        var problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problem.setTitle("Notificação Mercado Pago não processada");
        return ResponseEntity.status(ex.getStatusCode()).cacheControl(CacheControl.noStore()).body(problem);
    }
}
