package br.unicamp.ft.si600.eventos.exception;

import br.unicamp.ft.si600.eventos.gateway.MercadoPagoException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(-10)
@RestControllerAdvice(assignableTypes = {
        br.unicamp.ft.si600.eventos.controller.PaymentInternalController.class})
public class PaymentHttpExceptionHandler {
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<ProblemDetail> storage(org.springframework.dao.DataAccessException ex) {
        var problem = ProblemDetail.forStatus(503);
        problem.setTitle("Persistência de pagamento indisponível");
        problem.setDetail("A operação não foi confirmada; preservar a identificação da tentativa existente.");
        problem.setProperty("code", "STORAGE_FAILURE");
        return ResponseEntity.status(503).body(problem);
    }
    @ExceptionHandler(PaymentCreationRejected.class)
    ResponseEntity<ProblemDetail> rejected(PaymentCreationRejected ex) {
        var problem = ProblemDetail.forStatus(422);
        problem.setTitle("Criação rejeitada pelo provedor");
        problem.setDetail("A reserva foi preservada; consultar o pagamento existente e não reenviar como nova obrigação.");
        problem.setProperty("paymentId", ex.paymentId());
        problem.setProperty("code", "CREATION_REJECTED");
        return ResponseEntity.unprocessableEntity().body(problem);
    }
    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ProblemDetail> payment(PaymentException ex) {
        int status = switch (ex.kind()) {
            case INVALID_INPUT, AMBIENT_TRANSACTION -> 400;
            case NOT_FOUND -> 404;
            case CONFLICT, RECOVERY_REQUIRED, VERIFICATION_FAILED, STALE_SNAPSHOT -> 409;
            case CONFIGURATION, STORAGE_FAILURE -> 503;
        };
        var problem = ProblemDetail.forStatus(status);
        problem.setTitle("Operação de pagamento não concluída");
        problem.setDetail("Consultar a tentativa existente; não emitir nova cobrança para contornar a falha.");
        problem.setProperty("code", ex.kind().name());
        if (ex.paymentId() != null) problem.setProperty("paymentId", ex.paymentId());
        return ResponseEntity.status(status).body(problem);
    }
    @ExceptionHandler(MercadoPagoException.class)
    ResponseEntity<ProblemDetail> provider(MercadoPagoException ex) {
        var problem = ProblemDetail.forStatus(ex.kind() == MercadoPagoException.Kind.UNAVAILABLE ? 503 : 502);
        problem.setTitle("Consulta ao provedor não concluída");
        problem.setDetail("O pagamento existente foi preservado; não gerar outra cobrança.");
        problem.setProperty("code", ex.kind().name());
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
