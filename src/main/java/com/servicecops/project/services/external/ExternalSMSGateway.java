package com.servicecops.project.services.external;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.config.AfricasTalkingProperties;
import com.servicecops.project.models.database.CreditAccount;
import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.models.database.Message;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.repositories.MessageRepository;
import com.servicecops.project.service.CreditAccountService;
import com.servicecops.project.service.CreditTransactionService;
import com.servicecops.project.service.PartnerService;
import com.servicecops.project.service.SmsPricingService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.services.sms.AfricasTalkingSmsService;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalSMSGateway extends BaseWebActionsService {
    private final PartnerService partnerService;
    private final CreditAccountService creditAccountService;
    private final SmsPricingService smsPricingService;
    private final AfricasTalkingSmsService africasTalkingSmsService;
    private final CreditTransactionService creditTransactionService;
    private final MessageRepository messageRepository;
    private final AfricasTalkingProperties africasTalkingProperties;

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        if ("sendSMS".equals(action)) {
            return sendSMS(request);
        }
        throw new IllegalArgumentException("Action " + action + " not known in this context");
    }

    private OperationReturnObject sendSMS(JSONObject request) {
        Partner partner = authenticatePartner(request);
        String phoneNumber = request.getString("to");
        String messageText = request.getString("text");
        String countryCode = request.getString("countryCode");
        if (!StringUtils.hasText(phoneNumber) || !StringUtils.hasText(messageText)) {
            throw new IllegalArgumentException("to and text cannot be empty");
        }

        double cost = smsPricingService.calculateSmsCost(partner, countryCode);
        AfricasTalkingSmsService.SendResult result = africasTalkingSmsService.dispatch(phoneNumber, messageText);

        Message stored = persistMessage(partner, phoneNumber, messageText, cost, result);
        if (!result.success()) {
            throw new RuntimeException("Failed to send SMS via Africa's Talking");
        }

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

        JSONObject payload = new JSONObject();
        payload.put("messageId", stored.getId());
        payload.put("upstreamId", stored.getUpstreamId());
        payload.put("status", stored.getStatus() == null ? null : stored.getStatus().name());
        return new OperationReturnObject().success("SMS sent successfully", payload);
    }

    private Message persistMessage(
            Partner partner,
            String phoneNumber,
            String messageText,
            double cost,
            AfricasTalkingSmsService.SendResult result
    ) {
        Instant now = Instant.now();
        Message message = new Message();
        message.setPartner(partner);
        message.setFromAddr(StringUtils.hasText(africasTalkingProperties.getSenderId())
                ? africasTalkingProperties.getSenderId()
                : "Adricore");
        message.setToAddr(phoneNumber);
        message.setText(messageText);
        message.setEncoding("GSM_7");
        message.setSegments(segmentCount(messageText));
        message.setCost(BigDecimal.valueOf(cost));
        message.setStatus(result.success() ? Message.MessageStatus.SENT : Message.MessageStatus.FAILED);
        message.setUpstreamId(result.upstreamId());
        message.setProviderResponse(result.rawResponse());
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        try {
            return messageRepository.save(message);
        } catch (Exception error) {
            log.error("SMS dispatched but the message row could not be stored", error);
            return message;
        }
    }

    private int segmentCount(String text) {
        int length = text == null ? 0 : text.length();
        if (length <= 160) {
            return 1;
        }
        return (length + 152) / 153;
    }

    private Partner authenticatePartner(JSONObject request) {
        String partnerCode = request.getString("partnerCode");
        Optional<Partner> byPartnerCode = partnerService.findByPartnerCode(partnerCode);
        if (byPartnerCode.isEmpty()) {
            throw new RuntimeException("Partner Details Not Found");
        }
        Partner partner = byPartnerCode.get();
        if (!"active".equalsIgnoreCase(partner.getStatus())) {
            throw new RuntimeException("Partner is inactive");
        }
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
