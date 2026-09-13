package com.servicecops.project.services.partners;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.BillingInvoice;
import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.Organization;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.PartnerPaymentRequest;
import com.servicecops.project.models.database.SmsPricingProfile;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.OrganizationRepository;
import com.servicecops.project.repositories.PartnerRepository;
import com.servicecops.project.service.BillingInvoiceService;
import com.servicecops.project.service.CreditAccountService;
import com.servicecops.project.service.PartnerPricingService;
import com.servicecops.project.service.PartnerTopupService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class PartnersService extends BaseWebActionsService {
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    private static final Set<String> CURRENCIES = Set.of("UGX", "KES", "TZS", "RWF", "USD");
    private static final Set<String> SORT_FIELDS = Set.of(
            "name", "partnerCode", "email", "phone", "status", "currency", "createdAt", "updatedAt"
    );

    private final PartnerRepository partnerRepository;
    private final OrganizationRepository organizationRepository;
    private final CreditAccountService creditAccountService;
    private final PartnerPricingService partnerPricingService;
    private final BillingInvoiceService billingInvoiceService;
    private final PartnerTopupService partnerTopupService;

    @Transactional
    protected OperationReturnObject create(JSONObject request) {
        authorize();
        requires(request, "name");

        String name = requiredText(request, "name");
        Partner partner = new Partner();
        partner.setName(name);
        partner.setEmail(optionalText(request, "email"));
        partner.setPhone(optionalText(request, "phone"));
        partner.setCurrency(hasText(request, "currency") ? normalizeCurrency(requiredText(request, "currency")) : "UGX");
        partner.setStatus(STATUS_ACTIVE);
        partner.setPartnerCode(nextPartnerCode(optionalText(request, "partnerCode")));
        partner.setOrganization(resolveOrganization(optionalText(request, "organizationId")));
        partner.setParentPartner(resolveParent(optionalText(request, "parentPartnerId"), null));
        partner.setCreatedAt(Instant.now());
        partner.setUpdatedAt(Instant.now());

        Partner saved = partnerRepository.save(partner);
        creditAccountService.ensureFor(saved);
        SmsPricingProfile profile = partnerPricingService.resolveRequested(
                optionalLong(request, "pricingProfileId"),
                optionalText(request, "pricingProfileCode")
        );
        partnerPricingService.assign(saved, profile, authenticatedUser().getUsername());
        return success("Partner created", toPublicPartner(saved, profile));
    }

    @Transactional
    protected OperationReturnObject update(JSONObject request) {
        authorize();
        Partner partner = loadPartner(request);

        if (hasText(request, "name")) {
            partner.setName(requiredText(request, "name"));
        }
        if (request.containsKey("email")) {
            partner.setEmail(optionalText(request, "email"));
        }
        if (request.containsKey("phone")) {
            partner.setPhone(optionalText(request, "phone"));
        }
        if (hasText(request, "currency")) {
            partner.setCurrency(normalizeCurrency(requiredText(request, "currency")));
        }
        if (hasText(request, "status")) {
            String status = normalizeStatus(requiredText(request, "status"));
            if (STATUS_INACTIVE.equals(status)) {
                long activeChildren = partnerRepository.countByParentPartner_IdAndStatusIgnoreCase(partner.getId(), STATUS_ACTIVE);
                if (activeChildren > 0) {
                    throw new IllegalArgumentException("Deactivate child partners first");
                }
            }
            if (STATUS_ACTIVE.equals(status) && partner.getParentPartner() != null
                    && !STATUS_ACTIVE.equalsIgnoreCase(partner.getParentPartner().getStatus())) {
                throw new IllegalArgumentException("Reactivate the parent partner first");
            }
            partner.setStatus(status);
        }
        if (request.containsKey("organizationId")) {
            partner.setOrganization(resolveOrganization(optionalText(request, "organizationId")));
        }
        if (request.containsKey("parentPartnerId")) {
            partner.setParentPartner(resolveParent(optionalText(request, "parentPartnerId"), partner.getId()));
        }
        partner.setUpdatedAt(Instant.now());

        Partner saved = partnerRepository.save(partner);
        SmsPricingProfile profile = partnerPricingService.activeProfileFor(saved);
        if (request.containsKey("pricingProfileId") || hasText(request, "pricingProfileCode")) {
            profile = partnerPricingService.resolveRequested(
                    optionalLong(request, "pricingProfileId"),
                    optionalText(request, "pricingProfileCode")
            );
            partnerPricingService.assign(saved, profile, authenticatedUser().getUsername());
        }
        return success("Partner updated", toPublicPartner(saved, profile));
    }

    @Transactional
    protected OperationReturnObject deactivate(JSONObject request) {
        authorize();
        Partner partner = loadPartner(request);
        if (STATUS_INACTIVE.equalsIgnoreCase(partner.getStatus())) {
            return success("Partner already inactive", toPublicPartner(partner));
        }
        long activeChildren = partnerRepository.countByParentPartner_IdAndStatusIgnoreCase(partner.getId(), STATUS_ACTIVE);
        if (activeChildren > 0) {
            throw new IllegalArgumentException("Deactivate child partners first");
        }
        partner.setStatus(STATUS_INACTIVE);
        partner.setUpdatedAt(Instant.now());
        Partner saved = partnerRepository.save(partner);
        return success("Partner deactivated", toPublicPartner(saved));
    }

    @Transactional
    protected OperationReturnObject activate(JSONObject request) {
        authorize();
        Partner partner = loadPartner(request);
        if (STATUS_ACTIVE.equalsIgnoreCase(partner.getStatus())) {
            return success("Partner already active", toPublicPartner(partner));
        }
        if (partner.getParentPartner() != null && !STATUS_ACTIVE.equalsIgnoreCase(partner.getParentPartner().getStatus())) {
            throw new IllegalArgumentException("Reactivate the parent partner first");
        }
        partner.setStatus(STATUS_ACTIVE);
        partner.setUpdatedAt(Instant.now());
        Partner saved = partnerRepository.save(partner);
        return success("Partner activated", toPublicPartner(saved));
    }

    protected OperationReturnObject get(JSONObject request) {
        authorize();
        return success("Partner found", toPublicPartner(loadPartner(request)));
    }

    protected OperationReturnObject list(JSONObject request) {
        authorize();
        PagedQuery.Request pageRequest = PagedQuery.from(request, "createdAt", -1);
        Specification<Partner> spec = partnerSpec(pageRequest.query(), request == null ? null : request.getString("status"));
        Page<Partner> page = partnerRepository.findAll(spec, PagedQuery.toPageable(pageRequest, SORT_FIELDS, "createdAt"));
        List<Partner> partners = page.getContent();
        Map<UUID, SmsPricingProfile> profiles = partnerPricingService.activeProfilesFor(
                partners.stream().map(Partner::getId).toList()
        );
        SmsPricingProfile fallback = partners.isEmpty() ? null : partnerPricingService.defaultProfile();

        Map<String, Object> payload = PagedQuery.payload(
                partners.stream().map(partner -> {
                    SmsPricingProfile profile = profiles.get(partner.getId());
                    return toPublicPartner(partner, profile != null ? profile : fallback);
                }).toList(),
                page,
                pageRequest
        );
        payload.put("totalCount", partnerRepository.count());
        payload.put("activeCount", partnerRepository.countByStatusIgnoreCase(STATUS_ACTIVE));
        payload.put("inactiveCount", partnerRepository.countByStatusIgnoreCase(STATUS_INACTIVE));
        return success("Partners found", payload);
    }

    protected OperationReturnObject lookup(JSONObject request) {
        authorize();
        List<Map<String, Object>> partners = partnerRepository.findTop100ByStatusIgnoreCaseOrderByNameAsc(STATUS_ACTIVE)
                .stream()
                .map(this::toLookupPartner)
                .toList();
        return success("Partners found", partners);
    }

    protected OperationReturnObject profile(JSONObject request) {
        requiresAuth();
        SystemUserModel user = authenticatedUser();
        if (user.getPartnerId() == null) {
            throw new IllegalArgumentException("No partner is linked to this account");
        }
        can(user.getUsername(), "PARTNER_PORTAL", "MANAGE_PARTNERS");
        Partner partner = partnerRepository.findById(user.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Partner not found"));
        CreditAccount account = creditAccountService.ensureFor(partner);
        Map<String, Object> payload = toPublicPartner(partner);
        payload.put("credit", toPublicCredit(account, partner.getId()));
        payload.put("pricingCatalog", partnerPricingService.catalog());
        return success("Partner profile found", payload);
    }

    @Transactional
    protected OperationReturnObject requestTopup(JSONObject request) {
        Partner partner = requirePortalPartner(true);
        BigDecimal amount = parseAmount(request.get("amount"));
        BillingInvoice invoice = partnerTopupService.createInvoice(partner, amount);
        return success("Top-up request created", partnerTopupService.toTopupResult(partner, invoice, null));
    }

    @Transactional
    protected OperationReturnObject payNow(JSONObject request) {
        Partner partner = requirePortalPartner(true);
        UUID invoiceId = parseId(requiredText(request, "invoiceId"), "invoiceId");
        String customerPhone = requiredText(request, "customerPhone");
        PartnerPaymentRequest payment = partnerTopupService.payNow(partner, invoiceId, customerPhone);
        return success(
                "Mobile money payment requested",
                partnerTopupService.toTopupResult(partner, payment.getInvoice(), payment)
        );
    }

    @Transactional
    protected OperationReturnObject paymentStatus(JSONObject request) {
        Partner partner = requirePortalPartner(false);
        UUID paymentRequestId = parseId(requiredText(request, "paymentRequestId"), "paymentRequestId");
        PartnerPaymentRequest payment = partnerTopupService.refreshStatusForPartner(paymentRequestId, partner.getId());
        return success(
                "Payment status found",
                partnerTopupService.toTopupResult(partner, payment.getInvoice(), payment)
        );
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "create" -> create(request);
            case "update" -> update(request);
            case "deactivate" -> deactivate(request);
            case "activate" -> activate(request);
            case "get" -> get(request);
            case "list" -> list(request);
            case "lookup" -> lookup(request);
            case "profile" -> profile(request);
            case "requestTopup" -> requestTopup(request);
            case "payNow" -> payNow(request);
            case "paymentStatus" -> paymentStatus(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private Partner requirePortalPartner(boolean requireActive) {
        requiresAuth();
        SystemUserModel user = authenticatedUser();
        can(user.getUsername(), "PARTNER_PORTAL", "MANAGE_PARTNERS");
        if (user.getPartnerId() == null) {
            throw new IllegalArgumentException("No partner is linked to this account");
        }
        Partner partner = partnerRepository.findById(user.getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Partner not found"));
        if (requireActive && !STATUS_ACTIVE.equalsIgnoreCase(partner.getStatus())) {
            throw new IllegalArgumentException("Partner is inactive");
        }
        return partner;
    }

    private void authorize() {
        requiresAuth();
        SystemUserModel systemUserModel = authenticatedUser();
        can(systemUserModel.getUsername(), "MANAGE_PARTNERS");
    }

    private Partner loadPartner(JSONObject request) {
        requires(request, "id");
        UUID id = parseId(requiredText(request, "id"), "id");
        return partnerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Partner not found"));
    }

    private String nextPartnerCode(String requested) {
        if (StringUtils.hasText(requested)) {
            String code = requested.trim().toUpperCase();
            if (partnerRepository.existsByPartnerCode(code)) {
                throw new IllegalArgumentException("Partner code already in use");
            }
            return code;
        }
        Long sequence = partnerRepository.nextPartnerCodeValue();
        if (sequence == null) {
            throw new IllegalStateException("Unable to allocate a partner code");
        }
        return "ADRS" + sequence;
    }

    private Organization resolveOrganization(String organizationId) {
        if (!StringUtils.hasText(organizationId)) {
            return null;
        }
        return organizationRepository.findById(parseId(organizationId, "organizationId"))
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
    }

    private Partner resolveParent(String parentPartnerId, UUID currentId) {
        if (!StringUtils.hasText(parentPartnerId)) {
            return null;
        }
        UUID parentId = parseId(parentPartnerId, "parentPartnerId");
        if (parentId.equals(currentId)) {
            throw new IllegalArgumentException("A partner cannot be its own parent");
        }
        Partner parent = partnerRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent partner not found"));
        if (STATUS_INACTIVE.equalsIgnoreCase(parent.getStatus())) {
            throw new IllegalArgumentException("Parent partner is inactive");
        }
        return parent;
    }

    private String normalizeStatus(String status) {
        String value = status.trim().toLowerCase();
        if (!STATUS_ACTIVE.equals(value) && !STATUS_INACTIVE.equals(value)) {
            throw new IllegalArgumentException("Status must be active or inactive");
        }
        return value;
    }

    private String normalizeCurrency(String currency) {
        String value = currency.trim().toUpperCase();
        if (!CURRENCIES.contains(value)) {
            throw new IllegalArgumentException("Unsupported currency");
        }
        return value;
    }

    private UUID parseId(String raw, String field) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + field);
        }
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

    private Long optionalLong(JSONObject request, String field) {
        if (!request.containsKey(field) || request.get(field) == null || !StringUtils.hasText(String.valueOf(request.get(field)))) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(request.get(field)).trim());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private boolean hasText(JSONObject request, String field) {
        return request.containsKey(field) && StringUtils.hasText(request.getString(field));
    }

    private OperationReturnObject success(String message, Object payload) {
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, message, payload);
        return res;
    }

    private Specification<Partner> partnerSpec(String query, String status) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status.trim())) {
                predicates.add(cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase()));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("partnerCode")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, Object> toLookupPartner(Partner partner) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", partner.getId());
        data.put("partnerCode", partner.getPartnerCode());
        data.put("name", partner.getName());
        data.put("status", partner.getStatus());
        return data;
    }

    private Map<String, Object> toPublicPartner(Partner partner) {
        return toPublicPartner(partner, partnerPricingService.activeProfileFor(partner));
    }

    private Map<String, Object> toPublicPartner(Partner partner, SmsPricingProfile profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", partner.getId());
        data.put("partnerCode", partner.getPartnerCode());
        data.put("name", partner.getName());
        data.put("email", partner.getEmail());
        data.put("phone", partner.getPhone());
        data.put("status", partner.getStatus());
        data.put("currency", partner.getCurrency());
        data.put("organizationId", partner.getOrganization() != null ? partner.getOrganization().getId() : null);
        data.put("organizationName", partner.getOrganization() != null ? partner.getOrganization().getName() : null);
        data.put("parentPartnerId", partner.getParentPartner() != null ? partner.getParentPartner().getId() : null);
        data.put("parentPartnerCode", partner.getParentPartner() != null ? partner.getParentPartner().getPartnerCode() : null);
        data.put("parentPartnerName", partner.getParentPartner() != null ? partner.getParentPartner().getName() : null);
        data.put("createdAt", partner.getCreatedAt());
        data.put("updatedAt", partner.getUpdatedAt());
        if (profile != null) {
            data.put("pricingProfileId", profile.getId());
            data.put("pricingProfileCode", profile.getProfileCode());
            data.put("pricingProfileName", profile.getProfileName());
            Map<String, Object> pricing = new LinkedHashMap<>();
            pricing.put("id", profile.getId());
            pricing.put("profileCode", profile.getProfileCode());
            pricing.put("profileName", profile.getProfileName());
            pricing.put("description", profile.getDescription());
            pricing.put("currencyCode", profile.getCurrencyCode());
            pricing.put("sellingPricePerSms", profile.getSellingPricePerSms());
            pricing.put("billingType", profile.getBillingType());
            pricing.put("minimumSmsVolume", profile.getMinimumSmsVolume());
            pricing.put("maximumSmsVolume", profile.getMaximumSmsVolume());
            pricing.put("isDefault", Boolean.TRUE.equals(profile.getIsDefault()));
            data.put("pricingProfile", pricing);
        }
        return data;
    }

    private Map<String, Object> toPublicCredit(CreditAccount account, UUID partnerId) {
        BigDecimal balance = account.getBalance() == null ? BigDecimal.ZERO : account.getBalance();
        BigDecimal reserved = account.getReserved() == null ? BigDecimal.ZERO : account.getReserved();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", account.getId());
        data.put("balance", balance);
        data.put("reserved", reserved);
        data.put("available", balance);
        data.put("total", balance.add(reserved));
        data.put("pendingTopup", billingInvoiceService.sumPendingFor(partnerId));
        data.put("currency", account.getCurrency());
        data.put("updatedAt", account.getUpdatedAt());
        return data;
    }

    private BigDecimal parseAmount(Object raw) {
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            throw new IllegalArgumentException("Amount cannot be empty");
        }
        try {
            BigDecimal amount = new BigDecimal(String.valueOf(raw).trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            return amount;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Enter a valid amount");
        }
    }
}
