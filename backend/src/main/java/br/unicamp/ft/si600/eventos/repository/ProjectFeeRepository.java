package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.FeeType;
import br.unicamp.ft.si600.eventos.entity.ProjectFee;
import br.unicamp.ft.si600.eventos.entity.ProjectStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import br.unicamp.ft.si600.eventos.entity.ProjectFeeStatus;
import java.util.UUID;

public interface ProjectFeeRepository extends JpaRepository<ProjectFee, UUID> {

    List<ProjectFee> findAllByProjectIdOrderByFeeNameAsc(UUID projectId);

    /** US-07: taxas de projetos liberados, com projeto e taxa já carregados, aguardando cotação primeiro. */
    @Query("select pf from ProjectFee pf join fetch pf.project p join fetch pf.fee f "
            + "where p.status in :projectStatuses and f.type = :type and pf.status in :statuses "
            + "and pf.quantity is not null "
            + "order by pf.status asc, p.companyName asc, f.name asc, pf.id asc")
    List<ProjectFee> findQuoteable(@Param("projectStatuses") java.util.Collection<ProjectStatus> projectStatuses,
                                   @Param("type") FeeType type,
                                   @Param("statuses") java.util.Collection<ProjectFeeStatus> statuses);

    /** Bloqueia a linha para que a alteração de quantidade não corra com a geração de pagamento. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pf from ProjectFee pf join fetch pf.fee f where pf.id = :id and pf.project.id = :projectId")
    Optional<ProjectFee> findForUpdate(@Param("id") UUID id, @Param("projectId") UUID projectId);
}
