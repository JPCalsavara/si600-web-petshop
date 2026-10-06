package br.unicamp.ft.si600.eventos.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.ErrorResponse;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleMalformedRequest(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dados de requisição inválidos");
        problem.setDetail("A requisição está incompleta ou possui dados em formato inválido.");
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dados de requisição inválidos");
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        problem.setProperty("invalidFields", errors);

        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Violação de regra de negócio");
        problem.setDetail(ex.getMessage());
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/business-rule-violation"));
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ProblemDetail> handleFieldValidation(FieldValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dados de requisição inválidos");
        problem.setDetail(ex.getMessage());
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/bad-request"));
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("invalidFields", ex.getInvalidFields());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
        problem.setTitle("Requisição não pôde ser processada");
        problem.setDetail(ex.getMessage());
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/request"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        problem.setTitle("Arquivo muito grande");
        problem.setDetail("O PDF deve ter no máximo 10 MB.");
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/payload-too-large"));
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(problem);
    }

    private ResponseEntity<ProblemDetail> framework(ErrorResponse ex) {
        ProblemDetail problem = ex.getBody();
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(ex.getStatusCode()).headers(ex.getHeaders()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        // Erros HTTP padrão do Spring (404, 405, 415...) mantêm seu status em vez de virarem 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return framework(errorResponse);
        }
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erro interno no servidor");
        problem.setDetail("Ocorreu um erro inesperado durante o processamento da requisição");
        problem.setType(URI.create("https://api.eventos.unicamp.br/errors/internal-server-error"));
        problem.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
