package com.servicecops.project.services.messages;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.Message;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.repositories.MessageRepository;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import com.servicecops.project.utils.PagedQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MessagesService extends BaseWebActionsService {
    private static final Set<String> SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "toAddr", "fromAddr", "status", "cost", "upstreamId", "text"
    );
    private static final List<Message.MessageStatus> IN_PROGRESS = List.of(
            Message.MessageStatus.QUEUED,
            Message.MessageStatus.SENDING,
            Message.MessageStatus.SENT,
            Message.MessageStatus.ACCEPTED
    );
    private static final List<Message.MessageStatus> FAILED = List.of(
            Message.MessageStatus.FAILED,
            Message.MessageStatus.REJECTED,
            Message.MessageStatus.EXPIRED
    );

    private final MessageRepository messageRepository;

    protected OperationReturnObject list(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        PagedQuery.Request pageRequest = PagedQuery.from(request, "createdAt", -1);
        String status = request == null ? null : request.getString("status");
        Specification<Message> spec = messageSpec(pageRequest.query(), status, scopedPartnerId);
        Page<Message> page = messageRepository.findAll(
                spec,
                PagedQuery.toPageable(pageRequest, SORT_FIELDS, "createdAt")
        );

        Map<String, Object> payload = PagedQuery.payload(
                page.getContent().stream().map(this::toPublicMessage).toList(),
                page,
                pageRequest
        );
        payload.put("totalCount", messageRepository.count(messageSpec(null, "all", scopedPartnerId)));
        payload.put("deliveredCount", messageRepository.count(messageSpec(null, "delivered", scopedPartnerId)));
        payload.put("failedCount", messageRepository.count(messageSpec(null, "failed", scopedPartnerId)));
        payload.put("inFlightCount", messageRepository.count(messageSpec(null, "in_progress", scopedPartnerId)));
        return success("Messages found", payload);
    }

    protected OperationReturnObject get(JSONObject request) {
        UUID scopedPartnerId = authorizeAndScope();
        requires(request, "id");
        UUID id = parseId(request.getString("id"), "id");
        Message message = messageRepository.findWithPartnerById(id)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (scopedPartnerId != null && (message.getPartner() == null || !scopedPartnerId.equals(message.getPartner().getId()))) {
            throw new IllegalStateException("NOT AUTHORISED");
        }
        return success("Message found", toPublicMessage(message));
    }

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "list" -> list(request);
            case "get" -> get(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private UUID authorizeAndScope() {
        requiresAuth();
        SystemUserModel user = authenticatedUser();
        if (user.getPartnerId() != null) {
            can(user.getUsername(), "VIEW_MESSAGES", "PARTNER_PORTAL");
            return user.getPartnerId();
        }
        can(user.getUsername(), "VIEW_MESSAGES");
        return null;
    }

    private Specification<Message> messageSpec(String query, String status, UUID partnerId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Message, Partner> partner = root.join("partner", partnerId != null ? JoinType.INNER : JoinType.LEFT);
            if (partnerId != null) {
                predicates.add(cb.equal(partner.get("id"), partnerId));
            }
            List<Message.MessageStatus> statuses = statusesFor(status);
            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("toAddr"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("fromAddr"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("text"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("upstreamId"), "")), like),
                        cb.like(cb.lower(cb.coalesce(partner.get("name"), "")), like),
                        cb.like(cb.lower(cb.coalesce(partner.get("partnerCode"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<Message.MessageStatus> statusesFor(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status.trim())) {
            return List.of();
        }
        return switch (status.trim().toLowerCase()) {
            case "in_progress", "inflight" -> IN_PROGRESS;
            case "delivered" -> List.of(Message.MessageStatus.DELIVERED);
            case "failed" -> FAILED;
            default -> {
                try {
                    yield List.of(Message.MessageStatus.valueOf(status.trim().toUpperCase()));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Unknown message status");
                }
            }
        };
    }

    private Map<String, Object> toPublicMessage(Message message) {
        Map<String, Object> data = new LinkedHashMap<>();
        Partner partner = message.getPartner();
        data.put("id", message.getId());
        data.put("partnerId", partner == null ? null : partner.getId());
        data.put("partnerCode", partner == null ? null : partner.getPartnerCode());
        data.put("partnerName", partner == null ? null : partner.getName());
        data.put("fromAddr", message.getFromAddr());
        data.put("toAddr", message.getToAddr());
        data.put("text", message.getText());
        data.put("encoding", message.getEncoding());
        data.put("segments", message.getSegments());
        data.put("cost", message.getCost());
        data.put("status", message.getStatus() == null ? null : message.getStatus().name());
        data.put("upstreamId", message.getUpstreamId());
        data.put("callbackUrl", message.getCallbackUrl());
        data.put("providerResponse", message.getProviderResponse());
        data.put("createdAt", message.getCreatedAt());
        data.put("updatedAt", message.getUpdatedAt());
        return data;
    }

    private UUID parseId(String raw, String field) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    private OperationReturnObject success(String message, Object payload) {
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, message, payload);
        return res;
    }
}
