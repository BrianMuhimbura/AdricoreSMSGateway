package com.servicecops.project.services.external;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SystemUserModel;
import com.servicecops.project.service.PartnerService;
import com.servicecops.project.service.SmsOutboundService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
@Service
public class ExternalSMSGateway extends BaseWebActionsService {
    private static final String API_USER = "API_USER";

    private final PartnerService partnerService;
    private final SmsOutboundService smsOutboundService;

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        return switch (action) {
            case "sendSMS", "send" -> sendSMS(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private OperationReturnObject sendSMS(JSONObject request) {
        OperationReturnObject denied = authorizeApiUser();
        if (denied != null) {
            return denied;
        }
        Partner partner = loadPartner();
        return smsOutboundService.send(
                partner,
                request == null ? null : request.get("to"),
                requiredText(request, "text"),
                optionalText(request, "from"),
                "partner_api"
        );
    }

    private OperationReturnObject authorizeApiUser() {
        if (!Boolean.TRUE.equals(isAuthenticated())) {
            return fail(401, "AUTHENTICATION REQUIRED");
        }
        SystemUserModel user = authenticatedUser();
        if (!Boolean.TRUE.equals(user.getIsActive()) || !API_USER.equalsIgnoreCase(user.getRoleCode())) {
            return fail(403, "UNAUTHORIZED USER");
        }
        if (user.getPartnerId() == null) {
            return fail(403, "UNAUTHORIZED USER");
        }
        try {
            can(user.getUsername(), "SEND_SMS");
        } catch (IllegalStateException deniedPrivilege) {
            return fail(403, "UNAUTHORIZED USER");
        }
        return null;
    }

    private Partner loadPartner() {
        Partner partner = partnerService.findById(authenticatedUser().getPartnerId())
                .orElseThrow(() -> new IllegalArgumentException("Partner not found"));
        if (!"active".equalsIgnoreCase(partner.getStatus())) {
            throw new IllegalArgumentException("Partner is inactive");
        }
        return partner;
    }

    private String requiredText(JSONObject request, String field) {
        String value = request == null ? null : request.getString(field);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return value.trim();
    }

    private String optionalText(JSONObject request, String field) {
        if (request == null || !request.containsKey(field) || !StringUtils.hasText(request.getString(field))) {
            return null;
        }
        return request.getString(field).trim();
    }

    private OperationReturnObject fail(int code, String message) {
        OperationReturnObject res = new OperationReturnObject();
        res.setReturnCodeAndReturnMessage(code, message);
        return res;
    }
}
