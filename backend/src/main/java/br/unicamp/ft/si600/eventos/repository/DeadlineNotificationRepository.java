package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.DeadlineMilestone;
import br.unicamp.ft.si600.eventos.entity.DeadlineNotification;
import br.unicamp.ft.si600.eventos.entity.DeadlineType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface DeadlineNotificationRepository extends JpaRepository<DeadlineNotification, UUID> {

    boolean existsByProjectIdAndDeadlineTypeAndMilestoneAndDeadlineDateAndRecipient(
            UUID projectId, DeadlineType deadlineType, DeadlineMilestone milestone,
            LocalDate deadlineDate, String recipient);
}
