package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.ProjectHistoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectHistoryEventRepository extends JpaRepository<ProjectHistoryEvent, UUID> {
    List<ProjectHistoryEvent> findAllByProjectIdOrderByCreatedAtAscSequenceOrderAsc(UUID projectId);
}
