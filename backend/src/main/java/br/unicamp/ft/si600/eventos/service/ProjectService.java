package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.CreateProjectRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectResponse;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

/** US-03: criação de projeto pelo Admin. */
@Service
public class ProjectService {
    private static final String DUPLICATE_MESSAGE = "Já existe um projeto cadastrado com este e-mail ou CPF/CNPJ.";
    // Prazos valem até o fim do dia no fuso do evento.
    private static final ZoneId EVENT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ProjectRepository repository;
    private final FeeRepository feeRepository;
    private final ProjectFeeRepository projectFeeRepository;
    private final AlertTagCalculator alertTagCalculator;

    public ProjectService(ProjectRepository repository, FeeRepository feeRepository,
                          ProjectFeeRepository projectFeeRepository, AlertTagCalculator alertTagCalculator) {
        this.repository = repository;
        this.feeRepository = feeRepository;
        this.projectFeeRepository = projectFeeRepository;
        this.alertTagCalculator = alertTagCalculator;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, Actor actor) {
        if (actor.role() != ActorRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
        String name = request.name().trim();
        String address = request.address().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String document = request.document().trim();
        String digits = document.replaceAll("\\D", "");
        if (name.isEmpty() || address.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Preencha todos os campos obrigatórios.");
        }
        if (digits.length() != 11 && digits.length() != 14) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O CPF/CNPJ deve ter 11 ou 14 dígitos.");
        }
        if (request.paymentDeadline().isBefore(request.pdfDeadline())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "A data limite de pagamento não pode ser anterior à data de envio do PDF.");
        }
        if (repository.existsByDocumentDigits(digits) || repository.existsByContactEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }

        String representative = request.representativeName() == null || request.representativeName().isBlank()
                ? name : request.representativeName().trim();

        // O e-mail é o login do cliente: serve de identidade (ownerId) enquanto não há IdP (US-01).
        Project project = Project.create(email, name, document, digits, representative,
                request.area(), address, email,
                endOfDay(request.pdfDeadline()), endOfDay(request.paymentDeadline()),
                actor.id(), OffsetDateTime.now());
        try {
            Project saved = repository.saveAndFlush(project);
            List<UUID> feeIds = request.feeIds() == null ? List.of() : request.feeIds();
            Set<UUID> distinctFeeIds = feeIds.stream().collect(Collectors.toSet());
            if (distinctFeeIds.size() != feeIds.size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A mesma taxa não pode ser selecionada mais de uma vez.");
            }
            if (!distinctFeeIds.isEmpty()) {
                List<Fee> fees = feeRepository.findAllById(distinctFeeIds);
                if (fees.size() != distinctFeeIds.size() || fees.stream().anyMatch(f -> !f.isActive())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Uma ou mais taxas selecionadas não existem ou estão inativas.");
                }
                projectFeeRepository.saveAll(fees.stream().map(f -> new ProjectFee(saved, f)).toList());
            }
            return ProjectResponse.from(saved, alertTagCalculator.calculate(saved));
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre duas criações simultâneas: a constraint unique do banco é a palavra final.
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
    }

    private OffsetDateTime endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59).atZone(EVENT_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }
}
