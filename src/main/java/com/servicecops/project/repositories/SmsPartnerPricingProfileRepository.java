package com.servicecops.project.repositories;

import com.servicecops.project.models.database.SmsPartnerPricingProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SmsPartnerPricingProfileRepository extends JpaRepository<SmsPartnerPricingProfile, Long> {
    List<SmsPartnerPricingProfile> findByPartner_IdAndIsActiveTrueAndEffectiveToIsNull(UUID partnerId);

    Optional<SmsPartnerPricingProfile> findFirstByPartner_IdAndIsActiveTrueAndEffectiveToIsNullOrderByEffectiveFromDesc(UUID partnerId);

    @Query("""
            select a from SmsPartnerPricingProfile a
            join fetch a.pricingProfile
            join fetch a.partner
            where a.partner.id in :partnerIds
              and a.isActive = true
              and a.effectiveTo is null
            """)
    List<SmsPartnerPricingProfile> findActiveByPartnerIds(@Param("partnerIds") Collection<UUID> partnerIds);
}
