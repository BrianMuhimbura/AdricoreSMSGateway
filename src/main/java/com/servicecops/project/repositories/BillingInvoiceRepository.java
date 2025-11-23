package com.servicecops.project.repositories;

import com.servicecops.project.models.database.BillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {
}

