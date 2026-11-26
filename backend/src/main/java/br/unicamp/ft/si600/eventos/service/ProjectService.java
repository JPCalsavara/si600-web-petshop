package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.CreateProjectRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectAddressRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectResponse;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectAddress;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.repository.FeeRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectFeeRepository;
import br.unicamp.ft.si600.eventos.entity.Fee;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectHistoryEventType;
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
    /** Fuso do evento: os prazos são gravados como fim do dia neste fuso e a US-10 os avalia nele. */
    public static final ZoneId EVENT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ProjectRepository repository;
    private final FeeRepository feeRepository;
    private final ProjectFeeRepository projectFeeRepository;
    private final AlertTagCalculator alertTagCalculator;
    private final ProjectHistoryService historyService;
    private final AuthService authService;

    public ProjectService(ProjectRepository repository, FeeRepository feeRepository,
                          ProjectFeeRepository projectFeeRepository, AlertTagCalculator alertTagCalculator,
                          ProjectHistoryService historyService, AuthService authService) {
        this.repository = repository;
        this.feeRepository = feeRepository;
        this.projectFeeRepository = projectFeeRepository;
        this.alertTagCalculator = alertTagCalculator;
        this.historyService = historyService;
        this.authService = authService;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request, Actor actor) {
        if (actor.role() != ActorRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Você não possui permissão para esta operação.");
        }
        String name = request.name().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String document = request.document().trim();
        String digits = document.replaceAll("\\D", "");
        ProjectAddress structuredAddress = toStructuredAddress(request.address());
        String address = structuredAddress.toDisplayText();
        if (name.isEmpty()) {
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

        // O e-mail é o login do cliente (US-01): serve de identidade (ownerId) e de usuário da conta criada abaixo.
        Project project = Project.create(email, name, document, digits, representative,
                request.area(), address, structuredAddress, email,
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
            historyService.recordEvent(
                    saved.getId(),
                    ProjectHistoryEventType.PROJETO_CRIADO,
                    actor,
                    "Projeto cadastrado para a empresa " + saved.getCompanyName() + " (área: " + saved.getCategory() + ")."
            );
            // US-01: cria o acesso do Cliente (senha provisória por e-mail, enviada só após o commit).
            // A senha também volta na resposta (só o Admin chega aqui) para ser repassada ao Cliente na tela.
            String provisionalPassword = authService.provisionClientAccount(email).orElse(null);
            return ProjectResponse.from(saved, alertTagCalculator.calculate(saved))
                    .withProvisionalPassword(provisionalPassword);
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre duas criações simultâneas: a constraint unique do banco é a palavra final.
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
    }

    /** Grava o CEP só com dígitos, para o pagador do boleto e para a comparação com o texto livre. */
    private static ProjectAddress toStructuredAddress(ProjectAddressRequest request) {
        return new ProjectAddress(request.zipCode().replaceAll("\\D", ""), request.street(), request.number(),
                request.complement(), request.neighborhood(), request.city(), request.state());
    }

    private OffsetDateTime endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59).atZone(EVENT_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }
}
