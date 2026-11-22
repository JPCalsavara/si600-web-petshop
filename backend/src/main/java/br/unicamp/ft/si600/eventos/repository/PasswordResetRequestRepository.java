package br.unicamp.ft.si600.eventos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.unicamp.ft.si600.eventos.entity.PasswordResetRequest;
import br.unicamp.ft.si600.eventos.entity.PasswordResetStatus;
import jakarta.persistence.LockModeType;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {

    boolean existsByUserIdAndStatus(UUID userId, PasswordResetStatus status);

    List<PasswordResetRequest> findAllByStatusOrderByRequestedAtAsc(PasswordResetStatus status);

    /** Trava a linha: dois Admins atendendo o mesmo pedido ao mesmo tempo não geram duas senhas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PasswordResetRequest r where r.id = :id")
    Optional<PasswordResetRequest> findByIdForUpdate(@Param("id") UUID id);
}
