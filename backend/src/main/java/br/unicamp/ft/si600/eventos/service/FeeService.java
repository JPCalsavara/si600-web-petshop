package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.*;
import br.unicamp.ft.si600.eventos.entity.*;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.exception.FieldValidationException;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.security.*;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FeeService {
    private final FeeRepository repository;
    public FeeService(FeeRepository repository) { this.repository = repository; }

    @Transactional
    public FeeResponse create(FeeRequest request, Actor actor) {
        requireAdmin(actor);
        String name = normalize(request.name());
        String unit = normalize(request.measurementUnit());
        validate(request, name, unit);
        Fee fee = new Fee(name, request.description(), request.type(), request.areaPricingMode(),
                request.amount(), request.amountPerM2(), request.areaPerUnitM2(), request.unitAmount(),
                unit);
        return FeeResponse.from(repository.saveAndFlush(fee));
    }

    @Transactional(readOnly = true)
    public List<FeeResponse> list(Actor actor) {
        requireAdmin(actor);
        return repository.findAll(Sort.by("name", "id")).stream().map(FeeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FeeResponse get(UUID id, Actor actor) {
        requireAdmin(actor);
        return FeeResponse.from(load(id));
    }

    @Transactional
    public FeeResponse update(UUID id, FeeRequest request, Actor actor) {
        requireAdmin(actor);
        Fee fee = load(id);
        String name = normalize(request.name());
        String unit = normalize(request.measurementUnit());
        validate(request, name, unit);
        fee.update(name, request.description(), request.type(), request.areaPricingMode(),
                request.amount(), request.amountPerM2(), request.areaPerUnitM2(), request.unitAmount(),
                unit);
        return FeeResponse.from(repository.saveAndFlush(fee));
    }

    @Transactional
    public FeeResponse deactivate(UUID id, Actor actor) {
        requireAdmin(actor);
        Fee fee = load(id);
        fee.deactivate();
        return FeeResponse.from(repository.saveAndFlush(fee));
    }

    private Fee load(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "Taxa não encontrada."));
    }

    private void requireAdmin(Actor actor) {
        if (actor.role() != ActorRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        int start = 0;
        int end = value.length();
        while (start < end && isEdgeWhitespace(value.charAt(start))) start++;
        while (end > start && isEdgeWhitespace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private boolean isEdgeWhitespace(char character) {
        return character <= ' ' || Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    private void validate(FeeRequest request, String name, String unit) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (name == null || name.isBlank()) {
            errors.put("name", "O nome é obrigatório");
        }
        switch (request.type()) {
            case FIXA -> {
                requireField(errors, "amount", request.amount());
                forbidField(errors, "areaPricingMode", request.areaPricingMode());
                forbidField(errors, "amountPerM2", request.amountPerM2());
                forbidField(errors, "areaPerUnitM2", request.areaPerUnitM2());
                forbidField(errors, "unitAmount", request.unitAmount());
                forbidField(errors, "measurementUnit", request.measurementUnit());
            }
            case POR_METRAGEM -> {
                forbidField(errors, "amount", request.amount());
                forbidField(errors, "measurementUnit", request.measurementUnit());
                requireField(errors, "areaPricingMode", request.areaPricingMode());
                if (request.areaPricingMode() == AreaPricingMode.VALOR_POR_M2) {
                    requireField(errors, "amountPerM2", request.amountPerM2());
                    forbidField(errors, "areaPerUnitM2", request.areaPerUnitM2());
                    forbidField(errors, "unitAmount", request.unitAmount());
                } else if (request.areaPricingMode() == AreaPricingMode.UNIDADES_POR_INTERVALO) {
                    requireField(errors, "areaPerUnitM2", request.areaPerUnitM2());
                    requireField(errors, "unitAmount", request.unitAmount());
                    forbidField(errors, "amountPerM2", request.amountPerM2());
                }
            }
            case VARIAVEL -> {
                if (unit == null || unit.isBlank()) {
                    errors.put("measurementUnit", "A unidade de medida é obrigatória");
                }
                forbidField(errors, "amount", request.amount());
                forbidField(errors, "areaPricingMode", request.areaPricingMode());
                forbidField(errors, "amountPerM2", request.amountPerM2());
                forbidField(errors, "areaPerUnitM2", request.areaPerUnitM2());
                forbidField(errors, "unitAmount", request.unitAmount());
            }
        }
        rejectNul(errors, "name", request.name());
        rejectNul(errors, "description", request.description());
        rejectNul(errors, "measurementUnit", request.measurementUnit());
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private void rejectNul(Map<String, String> errors, String field, String value) {
        if (value != null && value.indexOf(0) >= 0) {
            errors.put(field, "O campo não pode conter o caractere NUL");
        }
    }

    private void requireField(Map<String, String> errors, String field, Object value) {
        if (value == null) errors.put(field, "O campo é obrigatório para o tipo ou modalidade da taxa");
    }

    private void forbidField(Map<String, String> errors, String field, Object value) {
        if (value != null) errors.put(field, "O campo não é permitido para o tipo ou modalidade da taxa");
    }
}
