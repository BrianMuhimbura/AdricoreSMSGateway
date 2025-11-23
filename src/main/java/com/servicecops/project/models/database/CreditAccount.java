package com.servicecops.project.models.database;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "credit_accounts")
@Getter
@Setter
public class CreditAccount {
    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "partner_id", unique = true)
    private Partner partner;

    @Column(nullable = false)
    private java.math.BigDecimal balance;

    @Column(nullable = false)
    private java.math.BigDecimal reserved;

    @Column(nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
