package com.servicecops.project.services.pricing;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.SmsPricingProfile;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.SmsPricingProfileRepository;
import com.servicecops.project.service.PartnerPricingService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import com.servicecops.project.utils.PagedQuery;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Service
@Transactional
public class SmsPricingProfilesService extends BaseWebActionsService {
    private static final Set<String> BILLING_TYPES = Set.of("PER_SMS", "PER_SEGMENT");
    private static final Set<String> CURRENCIES = Set.of("UGX", "KES", "TZS", "RWF", "USD");
    private static final Set<String> SORT_FIELDS = Set.of(
            "profileCode", "profileName", "sellingPricePerSms", "minimumSmsVolume",
            "isDefault", "isActive", "createdAt", "updatedAt"
    );

    private final SmsPricingProfileRepository profileRepository;

    @Transactional
    protected OperationReturnObject create(JSONObject request) {
        authorize();
        requires(request, "profileCode", "profileName");

        String code = normalizeCode(requiredText(request, "profileCode"));
        if (profileRepository.existsByProfileCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Profile code already in use");
        }

        SmsPricingProfile profile = new SmsPricingProfile();
        profile.setProfileCode(code);
        applyWritable(profile, request, true);
        Instant now = Instant.now();
        profile.setCreatedAt(now);
        profile.setCreatedBy(actor());
        profile.setUpdatedAt(now);
        profile.setUpdatedBy(actor());
        if (profile.getEffectiveFrom() == null) {
            profile.setEffectiveFrom(now);
        }

        SmsPricingProfile saved = profileRepository.saveAndFlush(profile);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            profileRepository.clearDefaultExcept(saved.getId());
        }
        return success("Pricing profile created", toPublicProfile(saved));
    }

    @Transactional
    protected OperationReturnObject update(JSONObject request) {
        authorize();
        SmsPricingProfile profile = loadProfile(request);

        if (hasText(request, "profileCode")) {
            String code = normalizeCode(requiredText(request, "profileCode"));
            if (!code.equalsIgnoreCase(profile.getProfileCode()) && profileRepository.existsByProfileCodeIgnoreCase(code)) {
                throw new IllegalArgumentException("Profile code already in use");
            }
            profile.setProfileCode(code);
        }
        applyWritable(profile, request, false);
        profile.setUpdatedAt(Instant.now());
        profile.setUpdatedBy(actor());

        SmsPricingProfile saved = profileRepository.saveAndFlush(profile);
        if (Boolean.TRUE.equals(saved.getIsDefault())) {
            profileRepository.clearDefaultExcept(saved.getId());
        }
        return success("Pricing profile updated", toPublicProfile(saved));
    }

    protected OperationReturnObject get(JSONObject request) {
        authorize();
        return success("Pricing profile found", toPublicProfile(loadProfile(request)));
    }

    protected OperationReturnObject list(JSONObject request) {
        authorize();
        PagedQuery.Request pageRequest = PagedQuery.from(request, "minimumSmsVolume", 1);
        Specification<SmsPricingProfile> spec = profileSpec(
                pageRequest.query(),
                request == null ? null : request.getString("status")
        );
        Page<SmsPricingProfile> page = profileRepository.findAll(
                spec,
                PagedQuery.toPageable(pageRequest, SORT_FIELDS, "minimumSmsVolume")
        );
        Map<String, Object> payload = PagedQuery.payload(
                page.getContent().stream().map(this::toPublicProfile).toList(),
                page,
                pageRequest
        );
        payload.put("totalCount", profileRepository.count());
        payload.put("activeCount", profileRepository.countByIsActiveTrue());
        payload.put("inactiveCount", profileRepository.countByIsActiveFalse());
        return success("Pricing profiles found", payload);
    }

    protected OperationReturnObject lookup(JSONObject request) {
        authorize();
        List<Map<String, Object>> profiles = profileRepository.findAllByIsActiveTrueOrderByMinimumSmsVolumeAscProfileNameAsc()
                .stream()
                .map(this::toLookupProfile)
                .toList();
        return success("Pricing profiles found", profiles);
    }

    @Override
    @Transactional
    public OperationReturnObject process(String action, JSONObject payload) {
        return super.process(action, payload);
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "create" -> create(request);
            case "update" -> update(request);
            case "get" -> get(request);
            case "list" -> list(request);
            case "lookup" -> lookup(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private void authorize() {
        requiresAuth();
        can(authenticatedUser().getUsername(), "MANAGE_PARTNERS");
    }

    private String actor() {
        SystemUserModel user = authenticatedUser();
        return user.getUsername();
    }

    private SmsPricingProfile loadProfile(JSONObject request) {
        requires(request, "id");
        Long id = parseLong(request.get("id"), "id");
        return profileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pricing profile not found"));
    }

    private void applyWritable(SmsPricingProfile profile, JSONObject request, boolean creating) {
        if (creating || hasText(request, "profileName")) {
            profile.setProfileName(requiredText(request, "profileName"));
        }
        if (creating || request.containsKey("description")) {
            profile.setDescription(optionalText(request, "description"));
        }
        if (creating || hasText(request, "currencyCode") || hasText(request, "currency")) {
            String currency = hasText(request, "currencyCode")
                    ? requiredText(request, "currencyCode")
                    : hasText(request, "currency") ? requiredText(request, "currency") : "UGX";
            profile.setCurrencyCode(normalizeCurrency(currency));
        } else if (creating) {
            profile.setCurrencyCode("UGX");
        }
        if (creating || request.containsKey("providerCostPerSms")) {
            profile.setProviderCostPerSms(optionalDecimal(
                    request,
                    "providerCostPerSms",
                    creating ? new BigDecimal("25.0000") : profile.getProviderCostPerSms()
            ));
        } else if (creating) {
            profile.setProviderCostPerSms(new BigDecimal("25.0000"));
        }
        if (creating || request.containsKey("topupFeePerSms")) {
            profile.setTopupFeePerSms(optionalDecimal(
                    request,
                    "topupFeePerSms",
                    creating ? new BigDecimal("5.0000") : profile.getTopupFeePerSms()
            ));
        } else if (creating) {
            profile.setTopupFeePerSms(new BigDecimal("5.0000"));
        }
        profile.refreshSellingPrice();
        if (creating || request.containsKey("minimumSmsVolume")) {
            Long min = optionalLong(request, "minimumSmsVolume");
            profile.setMinimumSmsVolume(min == null ? 0L : min);
        }
        if (creating || request.containsKey("maximumSmsVolume")) {
            profile.setMaximumSmsVolume(optionalLong(request, "maximumSmsVolume"));
        }
        if (creating || hasText(request, "billingType")) {
            profile.setBillingType(normalizeBilling(hasText(request, "billingType") ? requiredText(request, "billingType") : "PER_SMS"));
        }
        if (creating || request.containsKey("isDefault")) {
            profile.setIsDefault(optionalBoolean(request, "isDefault", creating && PartnerPricingService.DEFAULT_PROFILE_CODE.equals(profile.getProfileCode())));
        }
        if (creating || request.containsKey("isActive")) {
            profile.setIsActive(optionalBoolean(request, "isActive", true));
        }
        validateAmounts(profile);
        validateVolume(profile);
    }

    private void validateAmounts(SmsPricingProfile profile) {
        if (profile.getProviderCostPerSms() == null || profile.getProviderCostPerSms().signum() < 0) {
            throw new IllegalArgumentException("Provider cost cannot be negative");
        }
        if (profile.getTopupFeePerSms() == null || profile.getTopupFeePerSms().signum() < 0) {
            throw new IllegalArgumentException("Top-up fee cannot be negative");
        }
        profile.refreshSellingPrice();
    }

    private void validateVolume(SmsPricingProfile profile) {
        long min = profile.getMinimumSmsVolume() == null ? 0L : profile.getMinimumSmsVolume();
        if (min < 0) {
            throw new IllegalArgumentException("Minimum SMS volume cannot be negative");
        }
        if (profile.getMaximumSmsVolume() != null && profile.getMaximumSmsVolume() < min) {
            throw new IllegalArgumentException("Maximum SMS volume must be greater than or equal to the minimum");
        }
        profile.setMinimumSmsVolume(min);
    }

    private Specification<SmsPricingProfile> profileSpec(String query, String status) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
                boolean active = "active".equalsIgnoreCase(status.trim());
                predicates.add(cb.equal(root.get("isActive"), active));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("profileCode")), like),
                        cb.like(cb.lower(root.get("profileName")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, Object> toLookupProfile(SmsPricingProfile profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", profile.getId());
        data.put("profileCode", profile.getProfileCode());
        data.put("profileName", profile.getProfileName());
        data.put("currencyCode", profile.getCurrencyCode());
        data.put("sellingPricePerSms", profile.getSellingPricePerSms());
        data.put("isDefault", Boolean.TRUE.equals(profile.getIsDefault()));
        return data;
    }

    private Map<String, Object> toPublicProfile(SmsPricingProfile profile) {
        BigDecimal provider = nz(profile.getProviderCostPerSms());
        BigDecimal fee = nz(profile.getTopupFeePerSms());
        BigDecimal sell = nz(profile.getSellingPricePerSms());
        BigDecimal margin = sell.signum() > 0
                ? fee.divide(sell, 8, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", profile.getId());
        data.put("profileCode", profile.getProfileCode());
        data.put("profileName", profile.getProfileName());
        data.put("description", profile.getDescription());
        data.put("currencyCode", profile.getCurrencyCode());
        data.put("providerCostPerSms", provider);
        data.put("topupFeePerSms", fee);
        data.put("sellingPricePerSms", sell);
        data.put("effectiveCostPerSms", provider);
        data.put("grossProfitPerSms", fee);
        data.put("grossMarginPercent", margin);
        data.put("minimumSmsVolume", profile.getMinimumSmsVolume());
        data.put("maximumSmsVolume", profile.getMaximumSmsVolume());
        data.put("billingType", profile.getBillingType());
        data.put("isDefault", Boolean.TRUE.equals(profile.getIsDefault()));
        data.put("isActive", Boolean.TRUE.equals(profile.getIsActive()));
        data.put("effectiveFrom", profile.getEffectiveFrom());
        data.put("effectiveTo", profile.getEffectiveTo());
        data.put("createdAt", profile.getCreatedAt());
        data.put("updatedAt", profile.getUpdatedAt());
        return data;
    }

    private String normalizeCode(String value) {
        String code = value.trim().toUpperCase().replace(' ', '_');
        if (!code.matches("^[A-Z0-9_]{2,50}$")) {
            throw new IllegalArgumentException("Profile code must be 2-50 letters, numbers, or underscores");
        }
        return code;
    }

    private String normalizeCurrency(String currency) {
        String value = currency.trim().toUpperCase();
        if (!CURRENCIES.contains(value)) {
            throw new IllegalArgumentException("Unsupported currency");
        }
        return value;
    }

    private String normalizeBilling(String billingType) {
        String value = billingType.trim().toUpperCase();
        if (!BILLING_TYPES.contains(value)) {
            throw new IllegalArgumentException("Billing type must be PER_SMS or PER_SEGMENT");
        }
        return value;
    }

    private String requiredText(JSONObject request, String field) {
        String value = request.getString(field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field.replace("_", " ") + " cannot be empty");
        }
        return value.trim();
    }

    private String optionalText(JSONObject request, String field) {
        if (!hasText(request, field)) {
            return null;
        }
        return request.getString(field).trim();
    }

    private boolean hasText(JSONObject request, String field) {
        return request.containsKey(field) && StringUtils.hasText(request.getString(field));
    }

    private BigDecimal requiredDecimal(JSONObject request, String field) {
        BigDecimal value = parseDecimal(request.get(field), field);
        if (value == null) {
            throw new IllegalArgumentException(field.replace("_", " ") + " cannot be empty");
        }
        return value;
    }

    private BigDecimal optionalDecimal(JSONObject request, String field, BigDecimal fallback) {
        if (!request.containsKey(field) || request.get(field) == null || !StringUtils.hasText(String.valueOf(request.get(field)))) {
            return fallback;
        }
        return parseDecimal(request.get(field), field);
    }

    private Long optionalLong(JSONObject request, String field) {
        if (!request.containsKey(field) || request.get(field) == null || !StringUtils.hasText(String.valueOf(request.get(field)))) {
            return null;
        }
        return parseLong(request.get(field), field);
    }

    private boolean optionalBoolean(JSONObject request, String field, boolean fallback) {
        if (!request.containsKey(field) || request.get(field) == null) {
            return fallback;
        }
        Object raw = request.get(field);
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private BigDecimal parseDecimal(Object raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim()).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private Long parseLong(Object raw, String field) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private OperationReturnObject success(String message, Object payload) {
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, message, payload);
        return res;
    }
}
