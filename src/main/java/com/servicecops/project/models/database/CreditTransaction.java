package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_transactions")
public class CreditTransaction {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CreditTxType type;

    @Column(nullable = false)
    private java.math.BigDecimal amount;

    @Column(name = "balance_after", nullable = false)
    private java.math.BigDecimal balanceAfter;

    private String reference;

    @Column(columnDefinition = "jsonb")
    private String meta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Getters and setters

    public enum CreditTxType {
        TOPUP, ALLOCATE, CONSUMPTION, REFUND, REVERSAL
    }
}

