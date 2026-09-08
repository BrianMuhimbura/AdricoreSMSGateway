package com.servicecops.project.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.AuditLog;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.AuditLogRepository;
import com.servicecops.project.repositories.PartnerRepository;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AuditLogService {
    private static final Set<String> READ_ACTIONS = Set.of(
            "list", "get", "lookup", "roles", "login", "profile", "queryCustomer", "queryCustomerReferenceDetails"
    );
    private static final Set<String> SERVICES = Set.of(
            "Auth", "Users", "Partners", "Messages", "SMS", "AuditLogs", "Transactions", "Sasula"
    );
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "currentpassword", "newpassword", "confirmpassword",
            "token", "accesstoken", "refreshtoken", "authorization",
            "secret", "apikey", "api_key", "apikeysecret"
    );
    private static final int MAX_STRING = 2000;

    private final AuditLogRepository repository;
    private final PartnerRepository partnerRepository;
    private final TransactionTemplate transactionTemplate;

    public AuditLogService(
            AuditLogRepository repository,
            PartnerRepository partnerRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.partnerRepository = partnerRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<AuditLog> findAll() {
        return repository.findAll();
    }

    public Optional<AuditLog> findById(UUID id) {
        return repository.findById(id);
    }

    public AuditLog save(AuditLog logEntry) {
        return repository.save(logEntry);
    }

    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    /**
     * Persist a row for every mutating web action. Read-only actions are skipped.
     * Failures here never abort the original request.
     */
    public void recordIfWrite(
            String service,
            String action,
            JSONObject request,
            OperationReturnObject result,
            Exception error
    ) {
        if (!StringUtils.hasText(service)
                || !StringUtils.hasText(action)
                || !SERVICES.contains(service)
                || isRead(action)) {
            return;
        }
        try {
            SystemUserModel actor = currentUser();
            JSONObject payload = request == null ? new JSONObject() : request;

            AuditLog entry = new AuditLog();
            entry.setAction(service + "." + action);
            entry.setResourceType(service);
            entry.setResourceId(extractResourceId(payload, result));
            entry.setActorType(resolveActorType(actor, payload));
            entry.setActorId(resolveActorId(actor, payload));
            entry.setPartnerId(resolvePartnerId(actor, payload));
            entry.setSuccess(succeeded(result, error));
            entry.setDetails(details(actor, payload, result, error));
            entry.setCreatedAt(Instant.now());
            transactionTemplate.executeWithoutResult(status -> repository.save(entry));
        } catch (Exception auditError) {
            log.warn("Failed to write audit log for {}.{}: {}", service, action, auditError.getMessage());
        }
    }

    private boolean isRead(String action) {
        return READ_ACTIONS.contains(action.trim());
    }

    private boolean succeeded(OperationReturnObject result, Exception error) {
        return error == null && result != null && Integer.valueOf(0).equals(result.getReturnCode());
    }

    private Map<String, Object> details(
            SystemUserModel actor,
            JSONObject request,
            OperationReturnObject result,
            Exception error
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (actor != null) {
            details.put("actorUsername", actor.getUsername());
            details.put("actorEmail", actor.getEmail());
            details.put("actorRole", actor.getRoleCode());
        }
        UUID partnerId = resolvePartnerId(actor, request);
        if (partnerId != null) {
            details.put("actorPartnerId", partnerId.toString());
        }
        details.put("success", succeeded(result, error));
        if (result != null) {
            details.put("returnCode", result.getReturnCode());
            details.put("returnMessage", result.getReturnMessage());
        }
        if (error != null) {
            details.put("error", error.getMessage());
        }
        details.put("request", sanitizeToMap(request));
        return details;
    }

    private String resolveActorType(SystemUserModel actor, JSONObject request) {
        if (actor != null) {
            return actor.getPartnerId() != null ? "partner" : "admin";
        }
        if (hasText(request, "partnerCode")) {
            return "partner";
        }
        if (hasText(request, "customerReference") || hasText(request, "CustomerReference")) {
            return "partner";
        }
        return "system";
    }

    private String resolveActorId(SystemUserModel actor, JSONObject request) {
        if (actor != null) {
            return actor.getId() == null ? null : String.valueOf(actor.getId());
        }
        if (hasText(request, "partnerCode")) {
            return request.getString("partnerCode").trim();
        }
        if (hasText(request, "customerReference")) {
            return request.getString("customerReference").trim();
        }
        if (hasText(request, "CustomerReference")) {
            return request.getString("CustomerReference").trim();
        }
        return null;
    }

    private UUID resolvePartnerId(SystemUserModel actor, JSONObject request) {
        if (actor != null && actor.getPartnerId() != null) {
            return actor.getPartnerId();
        }
        if (hasText(request, "partnerCode")) {
            return partnerRepository.findByPartnerCode(request.getString("partnerCode").trim())
                    .or(() -> partnerRepository.findByPartnerCodeIgnoreCase(request.getString("partnerCode").trim()))
                    .map(Partner::getId)
                    .orElse(null);
        }
        String customerReference = firstNonBlank(
                stringify(request.get("customerReference")),
                stringify(request.get("CustomerReference"))
        );
        if (StringUtils.hasText(customerReference)) {
            return partnerRepository.findByPartnerCode(customerReference.trim())
                    .or(() -> partnerRepository.findByPartnerCodeIgnoreCase(customerReference.trim()))
                    .map(Partner::getId)
                    .orElse(null);
        }
        return null;
    }

    private String extractResourceId(JSONObject request, OperationReturnObject result) {
        String fromResult = extractFromObject(result == null ? null : result.getReturnObject());
        if (StringUtils.hasText(fromResult)) {
            return fromResult;
        }
        return firstNonBlank(
                stringify(request.get("id")),
                stringify(request.get("messageId")),
                stringify(request.get("partnerCode")),
                stringify(request.get("customerReference")),
                stringify(request.get("sasulaReceiptNumber"))
        );
    }

    private String extractFromObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject json) {
            return firstNonBlank(
                    stringify(json.get("id")),
                    stringify(json.get("messageId")),
                    stringify(json.get("partnerCode"))
            );
        }
        if (value instanceof Map<?, ?> map) {
            return firstNonBlank(
                    stringify(map.get("id")),
                    stringify(map.get("messageId")),
                    stringify(map.get("partnerCode"))
            );
        }
        return stringify(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeToMap(JSONObject source) {
        Object copy = JSON.parse(source.toJSONString());
        redact(copy);
        JSONObject json = copy instanceof JSONObject parsed ? parsed : new JSONObject();
        return json.toJavaObject(Map.class);
    }

    private void redact(Object node) {
        if (node instanceof JSONObject json) {
            for (String key : json.keySet().toArray(String[]::new)) {
                if (isSensitive(key)) {
                    json.put(key, "[redacted]");
                    continue;
                }
                if ("SERVICE".equals(key) || "ACTION".equals(key)) {
                    json.remove(key);
                    continue;
                }
                Object value = json.get(key);
                if (value instanceof String text && text.length() > MAX_STRING) {
                    json.put(key, text.substring(0, MAX_STRING) + "…");
                } else {
                    redact(value);
                }
            }
        } else if (node instanceof JSONArray array) {
            for (Object item : array) {
                redact(item);
            }
        }
    }

    private boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT));
    }

    private SystemUserModel currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof SystemUserModel user ? user : null;
    }

    private boolean hasText(JSONObject request, String field) {
        return request.containsKey(field) && StringUtils.hasText(request.getString(field));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }
}
