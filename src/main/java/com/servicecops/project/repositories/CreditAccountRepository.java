package com.servicecops.project.repositories;

import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, UUID> {
    Optional<CreditAccount> findByPartner(Partner partner);
}
