package com.servicecops.project.repositories;

import com.servicecops.project.models.database.BillingInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface BillingInvoiceRepository extends JpaRepository<BillingInvoice, UUID> {
    @Query("""
            select coalesce(sum(invoice.amount), 0)
            from BillingInvoice invoice
            where invoice.partner.id = :partnerId
              and lower(invoice.status) = 'pending'
            """)
    BigDecimal sumPendingAmountByPartnerId(@Param("partnerId") UUID partnerId);

    List<BillingInvoice> findByPartner_IdAndStatusIgnoreCaseOrderByCreatedAtAsc(UUID partnerId, String status);
}
