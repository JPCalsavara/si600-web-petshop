package br.unicamp.ft.si600.eventos.service;

import br.unicamp.ft.si600.eventos.dto.ProjectHistoryEventResponse;
import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectHistoryEvent;
import br.unicamp.ft.si600.eventos.entity.ProjectHistoryEventType;
import br.unicamp.ft.si600.eventos.exception.ApiException;
import br.unicamp.ft.si600.eventos.repository.ProjectHistoryEventRepository;
import br.unicamp.ft.si600.eventos.repository.ProjectRepository;
import br.unicamp.ft.si600.eventos.security.Actor;
import br.unicamp.ft.si600.eventos.security.ActorRole;
import br.unicamp.ft.si600.eventos.util.TimeHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProjectHistoryService {

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final AtomicLong SEQUENCE = new AtomicLong(1L);

    private final ProjectHistoryEventRepository historyRepository;
    private final ProjectRepository projectRepository;
    private final TimeHelper timeHelper;

    public ProjectHistoryService(ProjectHistoryEventRepository historyRepository,
                                 ProjectRepository projectRepository,
                                 TimeHelper timeHelper) {
        this.historyRepository = historyRepository;
        this.projectRepository = projectRepository;
        this.timeHelper = timeHelper;
    }

    @Transactional
    public ProjectHistoryEvent recordEvent(UUID projectId,
                                           ProjectHistoryEventType eventType,
                                           Actor actor,
                                           String description) {
        String safeDescription = sanitizeDescription(description);
        ProjectHistoryEvent event = new ProjectHistoryEvent(
                projectId,
                eventType,
                actor.id(),
                actor.role(),
                safeDescription,
                timeHelper.now(),
                SEQUENCE.getAndIncrement()
        );
        return historyRepository.save(event);
    }

    @Transactional
    public ProjectHistoryEvent recordPaymentGenerated(UUID projectId, String feeName, BigDecimal amount, Actor actor) {
        return recordEvent(projectId, ProjectHistoryEventType.PAGAMENTO_GERADO, actor,
                "Pagamento gerado para a taxa '" + feeName + "' no valor de R$ " + amount.setScale(2) + ".");
    }

    @Transactional
    public ProjectHistoryEvent recordPaymentConfirmed(UUID projectId, String feeName, BigDecimal amount, Actor actor) {
        return recordEvent(projectId, ProjectHistoryEventType.PAGAMENTO_CONFIRMADO, actor,
                "Pagamento confirmado para a taxa '" + feeName + "' no valor de R$ " + amount.setScale(2) + ".");
    }

    @Transactional
    public ProjectHistoryEvent recordPaymentCancelled(UUID projectId, String feeName, Actor actor) {
        return recordEvent(projectId, ProjectHistoryEventType.PAGAMENTO_CANCELADO, actor,
                "Pagamento cancelado e taxa '" + feeName + "' reaberta pelo administrador.");
    }

    @Transactional(readOnly = true)
    public List<ProjectHistoryEventResponse> listHistory(UUID projectId, Actor actor) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Projeto não encontrado."));

        if (actor.role() == ActorRole.CLIENT && !project.getOwnerId().equals(actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Você não possui permissão para acessar o histórico deste projeto.");
        }

        return historyRepository.findAllByProjectIdOrderByCreatedAtAscSequenceOrderAsc(projectId)
                .stream()
                .map(ProjectHistoryEventResponse::from)
                .toList();
    }

    private String sanitizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "-";
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            return trimmed.substring(0, MAX_DESCRIPTION_LENGTH - 3) + "...";
        }
        return trimmed;
    }
}
