package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.CreateProjectRequest;
import br.unicamp.ft.si600.eventos.dto.ProjectResponse;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
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

/** US-03: criação de projeto pelo Admin. */
@Service
public class ProjectService {
    private static final String DUPLICATE_MESSAGE = "Já existe um projeto cadastrado com este e-mail ou CPF/CNPJ.";
    // Prazos valem até o fim do dia no fuso do evento.
    private static final ZoneId EVENT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
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
            return ProjectResponse.from(repository.saveAndFlush(project));
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre duas criações simultâneas: a constraint unique do banco é a palavra final.
            throw new ApiException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
    }

    private OffsetDateTime endOfDay(LocalDate date) {
        return date.atTime(23, 59, 59).atZone(EVENT_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }
}
