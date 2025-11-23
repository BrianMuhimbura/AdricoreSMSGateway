package com.servicecops.project.models.database;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "partners")
public class Partner {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "parent_partner_id")
    private Partner parentPartner;

    @Column(name = "partner_code", unique = true, nullable = false)
    private String partnerCode;

    @Column(nullable = false)
    private String name;

    private String email;
    private String phone;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String currency;

    @Column(columnDefinition = "jsonb")
    private String priceProfile;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne
    @JoinColumn(name = "sms_pricing_profile_id")
    private SmsPricingProfile smsPricingProfile;

    // Getters and setters
}
