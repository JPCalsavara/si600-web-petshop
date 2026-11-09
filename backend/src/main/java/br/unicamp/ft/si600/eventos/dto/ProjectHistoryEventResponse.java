package br.unicamp.ft.si600.eventos.dto;

import br.unicamp.ft.si600.eventos.entity.ProjectHistoryEvent;
import br.unicamp.ft.si600.eventos.entity.ProjectHistoryEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectHistoryEventResponse(
        UUID id,
        UUID projectId,
        ProjectHistoryEventType eventType,
        String actorId,
        String actorRole,
        String description,
        OffsetDateTime createdAt
) {
    public static ProjectHistoryEventResponse from(ProjectHistoryEvent event) {
        return new ProjectHistoryEventResponse(
                event.getId(),
                event.getProjectId(),
                event.getEventType(),
                event.getActorId(),
                event.getActorRole().name(),
                event.getDescription(),
                event.getCreatedAt()
        );
    }
}
