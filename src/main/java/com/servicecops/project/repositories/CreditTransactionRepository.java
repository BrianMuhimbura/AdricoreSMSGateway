package com.servicecops.project.repositories;

import com.servicecops.project.models.database.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, UUID> {
}

