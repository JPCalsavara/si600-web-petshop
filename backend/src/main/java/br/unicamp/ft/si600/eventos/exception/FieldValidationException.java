package br.unicamp.ft.si600.eventos.exception;

import org.springframework.http.HttpStatus;
import java.util.Map;

/** Violações condicionais com o mesmo contrato de campos da Bean Validation. */
public class FieldValidationException extends ApiException {
    private final Map<String, String> invalidFields;

    public FieldValidationException(Map<String, String> invalidFields) {
        super(HttpStatus.BAD_REQUEST, "A requisição possui campos ausentes ou incompatíveis.");
        this.invalidFields = Map.copyOf(invalidFields);
    }

    public Map<String, String> getInvalidFields() { return invalidFields; }
}
