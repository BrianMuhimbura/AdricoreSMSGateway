package com.servicecops.project.service;

import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SmsPartnerPricingProfile;
import com.servicecops.project.models.database.SmsPricingProfile;
import com.servicecops.project.repositories.SmsPartnerPricingProfileRepository;
import com.servicecops.project.repositories.SmsPricingProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerPricingService {
    public static final String DEFAULT_PROFILE_CODE = "STARTER";

    private final SmsPricingProfileRepository profileRepository;
    private final SmsPartnerPricingProfileRepository assignmentRepository;

    public SmsPricingProfile defaultProfile() {
        return profileRepository.findFirstByIsDefaultTrueAndIsActiveTrue()
                .or(() -> profileRepository.findByProfileCodeIgnoreCase(DEFAULT_PROFILE_CODE))
                .or(() -> profileRepository.findFirstByIsActiveTrueOrderByMinimumSmsVolumeAsc())
                .orElseThrow(() -> new IllegalStateException("No SMS pricing profile is configured"));
    }

    public SmsPricingProfile requireActive(Long id) {
        SmsPricingProfile profile = profileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pricing profile not found"));
        if (!Boolean.TRUE.equals(profile.getIsActive())) {
            throw new IllegalArgumentException("Pricing profile is inactive");
        }
        return profile;
    }

    public SmsPricingProfile resolveRequested(Long profileId, String profileCode) {
        if (profileId != null) {
            return requireActive(profileId);
        }
        if (StringUtils.hasText(profileCode)) {
            SmsPricingProfile profile = profileRepository.findByProfileCodeIgnoreCase(profileCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Pricing profile not found"));
            if (!Boolean.TRUE.equals(profile.getIsActive())) {
                throw new IllegalArgumentException("Pricing profile is inactive");
            }
            return profile;
        }
        return defaultProfile();
    }

    public List<Map<String, Object>> catalog() {
        return profileRepository.findAllByIsActiveTrueOrderByMinimumSmsVolumeAscProfileNameAsc()
                .stream()
                .map(this::toCatalogItem)
                .toList();
    }

    public SmsPricingProfile activeProfileFor(Partner partner) {
        if (partner == null || partner.getId() == null) {
            return defaultProfile();
        }
        return assignmentRepository
                .findFirstByPartner_IdAndIsActiveTrueAndEffectiveToIsNullOrderByEffectiveFromDesc(partner.getId())
                .map(SmsPartnerPricingProfile::getPricingProfile)
                .orElseGet(this::defaultProfile);
    }

    public Map<UUID, SmsPricingProfile> activeProfilesFor(Collection<UUID> partnerIds) {
        if (partnerIds == null || partnerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, SmsPricingProfile> result = new LinkedHashMap<>();
        for (SmsPartnerPricingProfile assignment : assignmentRepository.findActiveByPartnerIds(partnerIds)) {
            result.putIfAbsent(assignment.getPartner().getId(), assignment.getPricingProfile());
        }
        return result;
    }

    @Transactional
    public SmsPartnerPricingProfile assignDefault(Partner partner, String actor) {
        return assign(partner, defaultProfile(), actor);
    }

    @Transactional
    public SmsPartnerPricingProfile assign(Partner partner, SmsPricingProfile profile, String actor) {
        Instant now = Instant.now();
        List<SmsPartnerPricingProfile> open = assignmentRepository
                .findByPartner_IdAndIsActiveTrueAndEffectiveToIsNull(partner.getId());
        boolean alreadyAssigned = false;
        for (SmsPartnerPricingProfile existing : open) {
            if (existing.getPricingProfile().getId().equals(profile.getId())) {
                alreadyAssigned = true;
                continue;
            }
            existing.setIsActive(false);
            existing.setEffectiveTo(now);
        }
        if (!open.isEmpty()) {
            assignmentRepository.saveAll(open);
            assignmentRepository.flush();
        }
        if (alreadyAssigned) {
            return open.stream()
                    .filter(item -> item.getPricingProfile().getId().equals(profile.getId()))
                    .findFirst()
                    .orElseThrow();
        }

        SmsPartnerPricingProfile assignment = new SmsPartnerPricingProfile();
        assignment.setPartner(partner);
        assignment.setPricingProfile(profile);
        assignment.setEffectiveFrom(now);
        assignment.setIsActive(true);
        assignment.setCreatedAt(now);
        assignment.setCreatedBy(StringUtils.hasText(actor) ? actor : "system");
        return assignmentRepository.save(assignment);
    }

    private Map<String, Object> toCatalogItem(SmsPricingProfile profile) {
        BigDecimal unit = profile.getSellingPricePerSms() == null ? BigDecimal.ZERO : profile.getSellingPricePerSms();
        long min = profile.getMinimumSmsVolume() == null ? 0L : profile.getMinimumSmsVolume();
        Long max = profile.getMaximumSmsVolume();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", profile.getId());
        data.put("profileCode", profile.getProfileCode());
        data.put("profileName", profile.getProfileName());
        data.put("description", profile.getDescription());
        data.put("currencyCode", profile.getCurrencyCode());
        data.put("sellingPricePerSms", unit);
        data.put("billingType", profile.getBillingType());
        data.put("minimumSmsVolume", min);
        data.put("maximumSmsVolume", max);
        data.put("isDefault", Boolean.TRUE.equals(profile.getIsDefault()));
        data.put("minimumCredit", unit.multiply(BigDecimal.valueOf(min)));
        data.put("maximumCredit", max == null ? null : unit.multiply(BigDecimal.valueOf(max)));
        return data;
    }
}
