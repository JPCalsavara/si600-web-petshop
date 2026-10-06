package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProjectFeeRepository extends JpaRepository<ProjectFee, UUID> {

    /** Bloqueia a linha para que a alteração de quantidade não corra com a geração de pagamento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pf from ProjectFee pf join fetch pf.fee f where pf.id = :id and pf.project.id = :projectId")
    Optional<ProjectFee> findForUpdate(@Param("id") UUID id, @Param("projectId") UUID projectId);
}
