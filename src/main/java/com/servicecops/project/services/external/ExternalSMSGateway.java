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
import com.servicecops.project.service.CreditTransactionService;
import com.servicecops.project.models.database.CreditTransaction;
import java.math.BigDecimal;
import java.time.Instant;

import java.util.Optional;

/**
 * Created by brian on 22/11/2025.
 */
public class ExternalSMSGateway extends BaseWebActionsService {
    PartnerService partnerService;
    CreditAccountService creditAccountService;
    SmsPricingService smsPricingService;
    AfricasTalkingSmsService africasTalkingSmsService;
    CreditTransactionService creditTransactionService;

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        if (action.equals("sendSMS")) {
            return this.sendSMS(request);
        }
        return null;
    }


    private OperationReturnObject sendSMS(JSONObject request) {
        Partner partner = authenticatePartner(request);
        // Call Africa's Talking service to send the SMS
        String phoneNumber = request.getString("to");
        String message = request.getString("text");
        String countryCode = request.getString("countryCode");
        double cost = smsPricingService.calculateSmsCost(partner, countryCode);
        boolean sent = africasTalkingSmsService.sendSms(phoneNumber, message);
        if (!sent) {
            throw new RuntimeException("Failed to send SMS via Africa's Talking");
        }
        // Reduce credit and record transaction
        Optional<CreditAccount> creditAccountOpt = creditAccountService.findByPartner(partner);
        if (creditAccountOpt.isEmpty()) {
            throw new RuntimeException("Credit account not found for partner");
        }
        CreditAccount creditAccount = creditAccountOpt.get();
        BigDecimal costBD = BigDecimal.valueOf(cost);
        BigDecimal newBalance = creditAccount.getBalance().subtract(costBD);
        creditAccount.setBalance(newBalance);
        creditAccount.setUpdatedAt(Instant.now());
        creditAccountService.save(creditAccount);
        CreditTransaction tx = new CreditTransaction();
        tx.setPartner(partner);
        tx.setType(CreditTransaction.CreditTxType.CONSUMPTION);
        tx.setAmount(costBD);
        tx.setBalanceAfter(newBalance);
        tx.setReference("SMS sent to " + phoneNumber);
        tx.setCreatedAt(Instant.now());
        creditTransactionService.save(tx);
        return new OperationReturnObject().success("SMS sent successfully");
    }

    private Partner authenticatePartner(JSONObject request) {
        String partnerCode = request.getString("partnerCode");
        Optional<Partner> byPartnerCode = partnerService.findByPartnerCode(partnerCode);
        if (byPartnerCode.isEmpty()) {
            throw new RuntimeException("Partner Details Not Found");
        }
        Partner partner = byPartnerCode.get();
        String countryCode = request.getString("countryCode");
        double cost = smsPricingService.calculateSmsCost(partner, countryCode);
        Optional<CreditAccount> creditAccountOpt = creditAccountService.findByPartner(partner);
        double credit = creditAccountOpt.map(acc -> acc.getBalance().doubleValue()).orElse(0.0);
        if (credit < cost) {
            throw new RuntimeException("Insufficient credit on partner account");
        }
        return partner;
    }
}
