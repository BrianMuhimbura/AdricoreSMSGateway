package com.servicecops.project.repositories;

import com.servicecops.project.models.database.PartnerPaymentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerPaymentRequestRepository extends JpaRepository<PartnerPaymentRequest, UUID> {
    Optional<PartnerPaymentRequest> findFirstByInvoice_IdAndStatusInOrderByRequestedAtDesc(
            UUID invoiceId,
            Collection<String> statuses
    );

    List<PartnerPaymentRequest> findByStatusInOrderByRequestedAtAsc(Collection<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartnerPaymentRequest p where p.id = :id")
    Optional<PartnerPaymentRequest> lockById(@Param("id") UUID id);

    Optional<PartnerPaymentRequest> findFirstByPartner_IdAndAmountAndStatusInAndCreditedAtIsNullOrderByRequestedAtDesc(
            UUID partnerId,
            java.math.BigDecimal amount,
            Collection<String> statuses
    );

    Optional<PartnerPaymentRequest> findFirstByPartner_IdAndAmountAndStatusInOrderByRequestedAtDesc(
            UUID partnerId,
            java.math.BigDecimal amount,
            Collection<String> statuses
    );

    Optional<PartnerPaymentRequest> findByIdAndPartner_Id(UUID id, UUID partnerId);

    Optional<PartnerPaymentRequest> findByExternalTransactionId(String externalTransactionId);

    Optional<PartnerPaymentRequest> findFirstByMmTransactionIdOrderByRequestedAtDesc(String mmTransactionId);

    boolean existsByInvoice_IdAndStatus(UUID invoiceId, String status);
}
