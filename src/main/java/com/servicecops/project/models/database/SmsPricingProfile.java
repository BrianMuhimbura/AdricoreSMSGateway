package com.servicecops.project.models.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "sms_pricing_profile")
public class SmsPricingProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_code", nullable = false, unique = true, length = 50)
    private String profileCode;

    @Column(name = "profile_name", nullable = false, length = 150)
    private String profileName;

    private String description;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "UGX";

    @Column(name = "provider_cost_per_sms", nullable = false, precision = 19, scale = 4)
    private BigDecimal providerCostPerSms;

    @Column(name = "topup_fee_per_sms", nullable = false, precision = 19, scale = 4)
    private BigDecimal topupFeePerSms;

    @Column(name = "selling_price_per_sms", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellingPricePerSms;

    @Column(name = "minimum_sms_volume", nullable = false)
    private Long minimumSmsVolume = 0L;

    @Column(name = "maximum_sms_volume")
    private Long maximumSmsVolume;

    @Column(name = "billing_type", nullable = false, length = 30)
    private String billingType = "PER_SMS";

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    @PreUpdate
    public void refreshSellingPrice() {
        BigDecimal cost = providerCostPerSms == null ? BigDecimal.ZERO : providerCostPerSms;
        BigDecimal fee = topupFeePerSms == null ? BigDecimal.ZERO : topupFeePerSms;
        this.sellingPricePerSms = cost.add(fee).setScale(4, RoundingMode.HALF_UP);
    }
}
