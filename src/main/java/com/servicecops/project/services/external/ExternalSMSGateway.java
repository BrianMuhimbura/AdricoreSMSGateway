package com.servicecops.project.services.external;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.service.PartnerService;
import com.servicecops.project.service.CreditAccountService;
import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import com.servicecops.project.service.SmsPricingService;
import com.servicecops.project.services.sms.AfricasTalkingSmsService;

import java.util.Optional;

/**
 * Created by brian on 22/11/2025.
 */
public class ExternalSMSGateway extends BaseWebActionsService {
    PartnerService partnerService;
    CreditAccountService creditAccountService;
    SmsPricingService smsPricingService;
    AfricasTalkingSmsService africasTalkingSmsService;

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        if (action.equals("sendSMS")) {
            return this.sendSMS(request);
        }
        return null;
    }

    private OperationReturnObject sendSMS(JSONObject request) {
        authenticatePartner(request);
        // Call Africa's Talking service to send the SMS
        String phoneNumber = request.getString("to");
        String message = request.getString("text");
        boolean sent = africasTalkingSmsService.sendSms(phoneNumber, message);
        if (!sent) {
            throw new RuntimeException("Failed to send SMS via Africa's Talking");
        }
        // You can return a more detailed OperationReturnObject as needed
        return new OperationReturnObject().success("SMS sent successfully");
    }

    private void authenticatePartner(JSONObject request) {
        String partnerCode = request.getString("partnerCode");
        Optional<Partner> byPartnerCode = partnerService.findByPartnerCode(partnerCode);
        if (byPartnerCode.isEmpty()) {
            throw new RuntimeException("Partner Details Not Found");
        }
        Partner partner = byPartnerCode.get();

        // Resolve SMS cost from the partner's SMS pricing profile
        String countryCode = request.getString("countryCode"); // expects countryCode in request
        double cost = smsPricingService.calculateSmsCost(partner, countryCode);
        Optional<CreditAccount> creditAccountOpt = creditAccountService.findByPartner(partner);
        double credit = creditAccountOpt.map(acc -> acc.getBalance().doubleValue()).orElse(0.0);
        if (credit < cost) {
            throw new RuntimeException("Insufficient credit on partner account");
        }
    }
}
