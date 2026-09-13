package com.servicecops.project.service;

import com.alibaba.fastjson2.JSONArray;
import com.servicecops.project.config.AfricasTalkingProperties;
import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.models.database.Message;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SmsPricingProfile;
import com.servicecops.project.repositories.MessageRepository;
import com.servicecops.project.services.sms.AfricasTalkingSmsService;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmsOutboundService {
    public static final int MAX_RECIPIENTS = 100;

    private final CreditAccountService creditAccountService;
    private final SmsPricingService smsPricingService;
    private final AfricasTalkingSmsService africasTalkingSmsService;
    private final MessageRepository messageRepository;
    private final AfricasTalkingProperties africasTalkingProperties;

    public OperationReturnObject send(Partner partner, Object rawRecipients, String messageText, String from, String source) {
        return send(partner, rawRecipients, messageText, from, source, MAX_RECIPIENTS);
    }

    public OperationReturnObject send(
            Partner partner,
            Object rawRecipients,
            String messageText,
            String from,
            String source,
            int maxRecipients
    ) {
        if (partner == null || partner.getId() == null) {
            throw new IllegalArgumentException("Partner is required");
        }
        if (!"active".equalsIgnoreCase(partner.getStatus())) {
            throw new IllegalArgumentException("Partner is inactive");
        }
        if (!StringUtils.hasText(messageText)) {
            throw new IllegalArgumentException("text cannot be empty");
        }
        List<String> recipients = parseRecipients(rawRecipients, maxRecipients);
        String sender = StringUtils.hasText(from)
                ? from.trim()
                : africasTalkingProperties.getSenderId();
        String text = messageText.trim();
        String origin = StringUtils.hasText(source) ? source.trim() : "gateway";

        SmsPricingService.SmsQuote quote = smsPricingService.quote(partner, text);
        BigDecimal totalCost = quote.cost().multiply(BigDecimal.valueOf(recipients.size()));
        BigDecimal available = creditAccountService.available(partner);
        if (available.compareTo(totalCost) < 0) {
            throw new IllegalArgumentException("Insufficient credit on partner account");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        int accepted = 0;
        BigDecimal charged = BigDecimal.ZERO;
        for (String recipient : recipients) {
            Map<String, Object> item = dispatchOne(partner, sender, recipient, text, quote, origin);
            results.add(item);
            if (Boolean.TRUE.equals(item.get("success"))) {
                accepted++;
                charged = charged.add(quote.cost());
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("partnerCode", partner.getPartnerCode());
        payload.put("from", sender);
        payload.put("text", text);
        payload.put("segments", quote.segments());
        payload.put("unitPrice", quote.unitPrice());
        payload.put("costPerSms", quote.cost());
        payload.put("currency", partner.getCurrency());
        payload.put("pricingProfile", toPublicProfile(quote.profile()));
        payload.put("requested", recipients.size());
        payload.put("accepted", accepted);
        payload.put("charged", charged);
        payload.put("balanceAfter", creditAccountService.available(partner));
        payload.put("messages", results);
        if (results.size() == 1) {
            payload.putAll(results.get(0));
        }

        OperationReturnObject response = new OperationReturnObject();
        if (accepted == 0) {
            response.setCodeAndMessageAndReturnObject(1, "Failed to send SMS", payload);
            return response;
        }
        response.setCodeAndMessageAndReturnObject(
                0,
                accepted == recipients.size() ? "SMS sent successfully" : "SMS partially sent",
                payload
        );
        return response;
    }

    public List<String> parseRecipients(Object raw, int maxRecipients) {
        List<String> recipients = new ArrayList<>();
        if (raw instanceof JSONArray array) {
            for (Object value : array) {
                addRecipient(recipients, value == null ? null : String.valueOf(value));
            }
        } else if (raw instanceof Collection<?> collection) {
            for (Object value : collection) {
                addRecipient(recipients, value == null ? null : String.valueOf(value));
            }
        } else if (raw != null && StringUtils.hasText(String.valueOf(raw))) {
            for (String part : String.valueOf(raw).split("[,;\\n]")) {
                addRecipient(recipients, part);
            }
        }
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("to cannot be empty");
        }
        if (recipients.size() > maxRecipients) {
            throw new IllegalArgumentException("A maximum of " + maxRecipients + " recipients is allowed");
        }
        return recipients;
    }

    private Map<String, Object> dispatchOne(
            Partner partner,
            String from,
            String phoneNumber,
            String messageText,
            SmsPricingService.SmsQuote quote,
            String source
    ) {
        AfricasTalkingSmsService.SendResult result = africasTalkingSmsService.dispatch(phoneNumber, messageText, from);
        Message stored = persistMessage(partner, from, phoneNumber, messageText, quote, result);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("to", phoneNumber);
        item.put("messageId", stored.getId());
        item.put("upstreamId", stored.getUpstreamId());
        item.put("status", stored.getStatus() == null ? null : stored.getStatus().name());
        item.put("segments", quote.segments());
        item.put("cost", quote.cost());
        item.put("success", result.success());

        if (!result.success()) {
            item.put("error", firstText(result.providerStatus(), "Failed to send SMS"));
            return item;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", source);
        meta.put("messageId", stored.getId() == null ? null : stored.getId().toString());
        meta.put("to", phoneNumber);
        meta.put("segments", quote.segments());
        meta.put("billedUnits", quote.billedUnits());
        meta.put("pricingProfileCode", quote.profile().getProfileCode());
        meta.put("pricingProfileName", quote.profile().getProfileName());
        meta.put("unitCost", quote.unitCost());
        meta.put("profit", quote.profit());
        try {
            CreditTransaction tx = creditAccountService.consume(
                    partner,
                    quote.cost(),
                    quote.profit(),
                    stored.getId() == null ? "SMS " + phoneNumber : stored.getId().toString(),
                    meta
            );
            item.put("transactionId", tx.getId());
            item.put("balanceAfter", tx.getBalanceAfter());
        } catch (Exception billingError) {
            log.error("SMS sent but partner credit could not be deducted for {}", phoneNumber, billingError);
            item.put("billed", false);
            item.put("error", "SMS sent but credit deduction failed");
        }
        return item;
    }

    private void addRecipient(List<String> recipients, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        String phone = normalizeMsisdn(raw);
        if (!recipients.contains(phone)) {
            recipients.add(phone);
        }
    }

    public String normalizeMsisdn(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("256") && digits.length() == 12) {
            return digits;
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            return "256" + digits.substring(1);
        }
        if (digits.length() == 9) {
            return "256" + digits;
        }
        if (digits.length() >= 9 && digits.length() <= 15) {
            return digits;
        }
        throw new IllegalArgumentException("Enter a valid mobile number");
    }

    private Message persistMessage(
            Partner partner,
            String from,
            String phoneNumber,
            String messageText,
            SmsPricingService.SmsQuote quote,
            AfricasTalkingSmsService.SendResult result
    ) {
        Instant now = Instant.now();
        Message message = new Message();
        message.setPartner(partner);
        message.setFromAddr(from);
        message.setToAddr(phoneNumber);
        message.setText(messageText);
        message.setEncoding("GSM_7");
        message.setSegments(quote.segments());
        message.setCost(quote.cost());
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

    private Map<String, Object> toPublicProfile(SmsPricingProfile profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", profile.getProfileCode());
        data.put("name", profile.getProfileName());
        data.put("billingType", profile.getBillingType());
        data.put("sellingPricePerSms", profile.getSellingPricePerSms());
        data.put("currencyCode", profile.getCurrencyCode());
        return data;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
