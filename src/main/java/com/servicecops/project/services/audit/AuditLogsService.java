package com.servicecops.project.services.audit;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.AuditLog;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.AuditLogRepository;
import com.servicecops.project.repositories.SystemUserRepository;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import com.servicecops.project.utils.PagedQuery;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AuditLogsService extends BaseWebActionsService {
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "action", "actorType", "actorId", "resourceType", "resourceId", "success"
    );
    private static final Set<String> RESOURCE_FILTERS = Set.of("Auth", "Users", "Partners", "SMS", "Transactions");

    private final AuditLogRepository auditLogRepository;
    private final SystemUserRepository systemUserRepository;

    protected OperationReturnObject list(JSONObject request) {
        Scope scope = authorizeAndScope();
        PagedQuery.Request pageRequest = PagedQuery.from(request, "createdAt", -1);
        String status = request == null ? null : request.getString("status");
        Specification<AuditLog> spec = auditSpec(pageRequest.query(), status, scope);
        Page<AuditLog> page = auditLogRepository.findAll(
                spec,
                PagedQuery.toPageable(pageRequest, SORT_FIELDS, "createdAt")
        );

        Map<String, Object> payload = PagedQuery.payload(
                page.getContent().stream().map(log -> toPublicLog(log, false)).toList(),
                page,
                pageRequest
        );
        payload.put("totalCount", auditLogRepository.count(auditSpec(null, "all", scope)));
        payload.put("failedCount", auditLogRepository.count(auditSpec(null, "failed", scope)));
        payload.put("todayCount", auditLogRepository.count(auditSpec(null, "all", scope).and(
                (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startOfToday())
        )));
        return success("Audit logs found", payload);
    }

    protected OperationReturnObject get(JSONObject request) {
        Scope scope = authorizeAndScope();
        requires(request, "id");
        UUID id = parseId(request.getString("id"), "id");
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Audit log not found"));
        if (!belongsToScope(log, scope)) {
            throw new IllegalStateException("NOT AUTHORISED");
        }
        return success("Audit log found", toPublicLog(log, true));
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "list" -> list(request);
            case "get" -> get(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private Scope authorizeAndScope() {
        requiresAuth();
        SystemUserModel user = authenticatedUser();
        can(user.getUsername(), "VIEW_AUDIT_LOGS");
        if (user.getPartnerId() == null) {
            return new Scope(null, List.of());
        }
        List<String> actorIds = systemUserRepository.findIdsByPartnerId(user.getPartnerId()).stream()
                .map(String::valueOf)
                .toList();
        return new Scope(user.getPartnerId(), actorIds);
    }

    private Specification<AuditLog> auditSpec(String query, String status, Scope scope) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (scope.partnerId() != null) {
                Predicate byPartner = cb.equal(root.get("partnerId"), scope.partnerId());
                Predicate byActor = scope.actorIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("actorId").in(scope.actorIds());
                predicates.add(cb.or(byPartner, byActor));
            }
            String filter = status == null ? "all" : status.trim();
            if ("failed".equalsIgnoreCase(filter)) {
                predicates.add(cb.isFalse(root.get("success")));
            } else if (RESOURCE_FILTERS.contains(filter)) {
                predicates.add(cb.equal(root.get("resourceType"), filter));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("actorType"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("actorId"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("resourceType"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("resourceId"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private boolean belongsToScope(AuditLog log, Scope scope) {
        if (scope.partnerId() == null) {
            return true;
        }
        if (scope.partnerId().equals(log.getPartnerId())) {
            return true;
        }
        return log.getActorId() != null && scope.actorIds().contains(log.getActorId());
    }

    private record Scope(UUID partnerId, List<String> actorIds) {
    }

    private Map<String, Object> toPublicLog(AuditLog log, boolean includeDetails) {
        Map<String, Object> details = log.getDetails() == null ? Map.of() : log.getDetails();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", log.getId());
        data.put("action", log.getAction());
        data.put("actorType", log.getActorType());
        data.put("actorId", log.getActorId());
        data.put("actorUsername", details.get("actorUsername"));
        data.put("resourceType", log.getResourceType());
        data.put("resourceId", log.getResourceId());
        data.put("success", log.getSuccess());
        data.put("summary", firstText(details.get("returnMessage"), details.get("error")));
        data.put("createdAt", log.getCreatedAt());
        if (includeDetails) {
            data.put("details", details);
        }
        return data;
    }

    private Instant startOfToday() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private UUID parseId(String raw, String field) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private String firstText(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private OperationReturnObject success(String message, Object payload) {
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, message, payload);
        return res;
    }
}
