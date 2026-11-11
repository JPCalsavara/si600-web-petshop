package br.unicamp.ft.si600.eventos.repository;

import br.unicamp.ft.si600.eventos.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByIntegrationIdAndRequestId(String integrationId, UUID requestId);
    Optional<Payment> findByIntegrationIdAndObligationReference(String integrationId, String obligationReference);
    Optional<Payment> findByIdAndIntegrationId(UUID id, String integrationId);
    Optional<Payment> findByOrderId(String orderId);
    org.springframework.data.domain.Page<Payment> findByIntegrationId(String integrationId, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.integrationId = :integration")
    Optional<Payment> findForUpdate(@Param("id") UUID id, @Param("integration") String integrationId);
}
