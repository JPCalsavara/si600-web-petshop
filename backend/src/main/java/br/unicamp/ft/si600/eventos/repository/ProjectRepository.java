package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.Project;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByIdAndOwnerId(UUID id, String ownerId);
    boolean existsByDocumentDigits(String documentDigits);
    boolean existsByContactEmail(String contactEmail);
    List<Project> findAllByStatusOrderByPdfUploadedAtAsc(ProjectStatus status);
}
