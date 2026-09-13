package com.servicecops.project.repositories;

import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.Partner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, UUID> {
    Optional<CreditAccount> findByPartner(Partner partner);
    Optional<CreditAccount> findByPartner_Id(UUID partnerId);
    Optional<CreditAccount> findByAccountCode(String accountCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CreditAccount account where account.partner.id = :partnerId")
    Optional<CreditAccount> findByPartnerIdForUpdate(@Param("partnerId") UUID partnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CreditAccount account where account.id = :id")
    Optional<CreditAccount> findByIdForUpdate(@Param("id") UUID id);
}
