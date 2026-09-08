package com.servicecops.project.repositories;

import com.servicecops.project.models.database.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID>, JpaSpecificationExecutor<CreditTransaction> {
    boolean existsByTypeAndReference(CreditTransaction.CreditTxType type, String reference);
}
