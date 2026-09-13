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
    public static final String ADRICORE_CONTROL = "ADRICORE_CONTROL";
    public static final String ADRICORE_OPERATIONS = "ADRICORE_OPERATIONS";
    public static final String ADRICORE_INCOME = "ADRICORE_INCOME";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "partner_id", unique = true)
    private Partner partner;

    @Column(name = "account_code", length = 50)
    private String accountCode;

    @Column(name = "account_name", length = 150)
    private String accountName;

    @Column(nullable = false)
    private java.math.BigDecimal balance;

    @Column(nullable = false)
    private java.math.BigDecimal reserved;

    @Column(nullable = false)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isSystemAccount() {
        return partner == null;
    }

    public String label() {
        if (accountName != null && !accountName.isBlank()) {
            return accountName;
        }
        if (accountCode != null && !accountCode.isBlank()) {
            return accountCode;
        }
        if (partner != null && partner.getName() != null) {
            return partner.getName();
        }
        return "Account";
    }
}
