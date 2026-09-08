package com.servicecops.project.models.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_transactions")
@Getter
@Setter
public class CreditTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "credit_tx_type")
    private CreditTxType type;

    @Column(nullable = false)
    private java.math.BigDecimal amount;

    @Column(name = "balance_after", nullable = false)
    private java.math.BigDecimal balanceAfter;

    private String reference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Object> meta;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Getters and setters

    public enum CreditTxType {
        TOPUP, ALLOCATE, CONSUMPTION, REFUND, REVERSAL
    }
}

