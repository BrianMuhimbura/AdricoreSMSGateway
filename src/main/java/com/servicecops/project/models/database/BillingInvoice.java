package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "billing_invoices")
public class BillingInvoice {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "due_date")
    private java.time.LocalDate dueDate;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "jsonb")
    private String meta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Getters and setters
}

