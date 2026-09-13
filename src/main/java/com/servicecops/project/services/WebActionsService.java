package com.servicecops.project.services;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.service.AuditLogService;
import com.servicecops.project.services.audit.AuditLogsService;
import com.servicecops.project.services.auth.AuthService;
import com.servicecops.project.services.external.ExternalSMSGateway;
import com.servicecops.project.services.messages.MessagesService;
import com.servicecops.project.services.partners.PartnersService;
import com.servicecops.project.services.pricing.SmsPricingProfilesService;
import com.servicecops.project.services.sasula.SasulaService;
import com.servicecops.project.services.transactions.TransactionsService;
import com.servicecops.project.services.users.UsersService;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebActionsService {

    private final AuthService authService;
    private final UsersService usersService;
    private final PartnersService partnersService;
    private final MessagesService messagesService;
    private final ExternalSMSGateway externalSMSGateway;
    private final AuditLogsService auditLogsService;
    private final TransactionsService transactionsService;
    private final SasulaService sasulaService;
    private final SmsPricingProfilesService smsPricingProfilesService;
    private final AuditLogService auditLogService;

    public OperationReturnObject processAction(String service, String action, JSONObject payload) {
        OperationReturnObject result = null;
        Exception error = null;
        try {
            result = dispatch(service, action, payload);
            return result;
        } catch (Exception exception) {
            error = exception;
            throw exception;
        } finally {
            try {
                auditLogService.recordIfWrite(service, action, payload, result, error);
            } catch (Exception auditError) {
                // Audit must never hide or fail the original action.
            }
        }
    }

    private OperationReturnObject dispatch(String service, String action, JSONObject payload) {
        return switch (service) {
            case "Auth" -> authService.process(action, payload);
            case "Users" -> usersService.process(action, payload);
            case "Partners" -> partnersService.process(action, payload);
            case "Messages" -> messagesService.process(action, payload);
            case "SMS" -> externalSMSGateway.process(action, payload);
            case "AuditLogs" -> auditLogsService.process(action, payload);
            case "Transactions" -> transactionsService.process(action, payload);
            case "Sasula" -> sasulaService.process(action, payload);
            case "SmsPricingProfiles" -> smsPricingProfilesService.process(action, payload);
            default -> {
                OperationReturnObject res = new OperationReturnObject();
                res.setReturnCodeAndReturnMessage(404, "UNKNOWN SERVICE");
                yield res;
            }
        };
    }
}
