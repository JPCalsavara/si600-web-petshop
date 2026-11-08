package br.unicamp.ft.si600.eventos.controller;

import br.unicamp.ft.si600.eventos.dto.payment.*;
import br.unicamp.ft.si600.eventos.entity.PaymentStatus;
import br.unicamp.ft.si600.eventos.payment.http.PaymentAccessPolicy;
import br.unicamp.ft.si600.eventos.payment.http.PaymentBoundaryFilter;
import br.unicamp.ft.si600.eventos.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import br.unicamp.ft.si600.eventos.exception.PaymentCreationRejected;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/internal/payments")
public class PaymentInternalController {
    private final PaymentService service;
    private final PaymentAccessPolicy policy;
    private final br.unicamp.ft.si600.eventos.payment.http.MercadoPagoWebhookSignature webhook;
    public PaymentInternalController(PaymentService service, PaymentAccessPolicy policy,
                                     br.unicamp.ft.si600.eventos.payment.http.MercadoPagoWebhookSignature webhook) {
        this.service = service; this.policy = policy; this.webhook = webhook;
    }
    public record WebhookConfiguration(boolean enabled, boolean configured) {}
    @GetMapping("/webhook-status")
    public WebhookConfiguration webhookStatus() {
        return new WebhookConfiguration(webhook.enabled(), webhook.configured());
    }
    @PostMapping("/boleto")
    public ResponseEntity<PaymentResponse> generate(@RequestAttribute(PaymentBoundaryFilter.PRINCIPAL) PaymentBoundaryFilter.Principal principal,
                                                   @Valid @RequestBody InternalBoletoRequest request, HttpServletRequest servletRequest) {
        policy.authorize(principal.integrationId(), request.chargeReference());
        boolean development = policy.isDevelopment(principal.integrationId());
        var command = request.command(principal.integrationId());
        if (development) {
            if (!"test_user_br@testuser.com".equals(request.payer().email())) {
                throw new br.unicamp.ft.si600.eventos.exception.ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "No ambiente de teste de boleto, use o e-mail test_user_br@testuser.com e a credencial de teste do Mercado Pago.");
            }
            command = new GenerateBoletoCommand(principal.integrationId(), "dev-" + request.requestId(), request.requestId(),
                    request.amount(), "BRL", request.description(), request.expirationDays(), request.payer());
        }
        var result = service.generateBoleto(command);
        if (result.status() == PaymentStatus.FALHOU && !development) throw new PaymentCreationRejected(result.id());
        int status = result.status() == PaymentStatus.CRIANDO || result.status() == PaymentStatus.INCERTO ? 202
                : 200;
        return ResponseEntity.status(status).location(URI.create(servletRequest.getContextPath() + "/internal/payments/" + result.id()))
                .header("Cache-Control", "no-store").header("X-Payment-Next-Action", "QUERY_EXISTING_PAYMENT").body(result);
    }
    public record PaymentHistory(java.util.List<PaymentResponse> payments, int page, boolean hasNext) {}

    @GetMapping("/history/{page}")
    public PaymentHistory history(@RequestAttribute(PaymentBoundaryFilter.PRINCIPAL) PaymentBoundaryFilter.Principal principal,
                                  @PathVariable int page) {
        if (!policy.isDevelopment(principal.integrationId())) {
            throw new br.unicamp.ft.si600.eventos.exception.ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "Histórico de desenvolvimento indisponível.");
        }
        if (page < 0) throw new br.unicamp.ft.si600.eventos.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Página inválida.");
        var history = service.getPaymentHistory(principal.integrationId(), page);
        return new PaymentHistory(history.getContent(), page, history.hasNext());
    }
    @GetMapping("/{id}")
    public PaymentResponse get(@RequestAttribute(PaymentBoundaryFilter.PRINCIPAL) PaymentBoundaryFilter.Principal principal, @PathVariable UUID id) {
        var result = service.getPayment(principal.integrationId(), id);
        policy.authorize(principal.integrationId(), result.obligationReference());
        return result;
    }
    @GetMapping("/{id}/boleto")
    public BoletoDetailsResponse boleto(@RequestAttribute(PaymentBoundaryFilter.PRINCIPAL) PaymentBoundaryFilter.Principal principal, @PathVariable UUID id) {
        get(principal, id); // Authorize before the service performs an external GET.
        return service.getBoleto(principal.integrationId(), id);
    }
    public record SynchronizeRequest(String orderId) {}

    @PostMapping("/{id}/synchronize")
    public PaymentResponse synchronize(@RequestAttribute(PaymentBoundaryFilter.PRINCIPAL) PaymentBoundaryFilter.Principal principal,
                                       @PathVariable UUID id, @RequestBody(required = false) SynchronizeRequest request) {
        var payment = get(principal, id);
        if ((request == null || request.orderId() == null) && payment.orderId() == null) {
            throw new br.unicamp.ft.si600.eventos.exception.PaymentException(
                    br.unicamp.ft.si600.eventos.exception.PaymentException.Kind.RECOVERY_REQUIRED, id);
        }
        return request == null || request.orderId() == null
                ? service.synchronizeOrder(principal.integrationId(), id)
                : service.synchronizeOrder(principal.integrationId(), id, request.orderId());
    }
}
