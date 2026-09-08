package com.servicecops.project.services.sasula;

import com.alibaba.fastjson2.JSONObject;
import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.repositories.CreditTransactionRepository;
import com.servicecops.project.service.BillingInvoiceService;
import com.servicecops.project.service.CreditAccountService;
import com.servicecops.project.service.PartnerService;
import com.servicecops.project.services.base.BaseWebActionsService;
import com.servicecops.project.utils.OperationReturnObject;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class SasulaService extends BaseWebActionsService {
    private final PartnerService partnerService;
    private final BillingInvoiceService billingInvoiceService;
    private final CreditAccountService creditAccountService;
    private final CreditTransactionRepository creditTransactionRepository;

    @Override
    public OperationReturnObject switchActions(String action, JSONObject request) {
        OperationReturnObject denied = authorize();
        if (denied != null) {
            return denied;
        }
        return switch (action) {
            case "queryCustomer", "queryCustomerReferenceDetails" -> queryCustomer(request);
            case "postPayment" -> postPayment(request);
            default -> throw new IllegalArgumentException("Action " + action + " not known in this context");
        };
    }

    private OperationReturnObject authorize() {
        if (!Boolean.TRUE.equals(isAuthenticated())) {
            return fail(401, "AUTHENTICATION REQUIRED");
        }
        try {
            can(authenticatedUser().getUsername(), "SASULA_INTEGRATION");
        } catch (IllegalStateException ex) {
            return fail(403, "NOT AUTHORISED");
        }
        return null;
    }

    private OperationReturnObject queryCustomer(JSONObject request) {
        String customerReference = firstText(request, "customerReference", "CustomerReference", "partnerCode");
        if (!StringUtils.hasText(customerReference)) {
            return fail(1, "Customer reference cannot be empty");
        }
        Optional<Partner> found = partnerService.findByPartnerCode(customerReference);
        if (found.isEmpty()) {
            return fail(1, "Unknown customer reference");
        }
        Partner partner = found.get();
        if (!"active".equalsIgnoreCase(partner.getStatus())) {
            return fail(1, "Customer reference is inactive");
        }

        BigDecimal outstanding = billingInvoiceService.sumPendingFor(partner.getId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerReference", partner.getPartnerCode());
        payload.put("customerName", partner.getName());
        payload.put("amountOutstanding", outstanding);
        payload.put("fixedAmount", outstanding.compareTo(BigDecimal.ZERO) > 0);
        payload.put("businessReference", partner.getPartnerCode());
        payload.put("partnerId", partner.getId());
        payload.put("partnerCode", partner.getPartnerCode());
        payload.put("email", partner.getEmail());
        payload.put("phone", partner.getPhone());
        payload.put("status", partner.getStatus());
        payload.put("currency", partner.getCurrency());
        return success("Customer found", payload);
    }

    @Transactional
    protected OperationReturnObject postPayment(JSONObject request) {
        String customerReference = firstText(request, "customerReference", "CustomerReference", "partnerCode");
        if (!StringUtils.hasText(customerReference)) {
            return fail(1, "Customer reference cannot be empty");
        }
        Optional<Partner> found = partnerService.findByPartnerCode(customerReference);
        if (found.isEmpty()) {
            return fail(1, "Unknown customer reference");
        }
        Partner partner = found.get();
        if (!"active".equalsIgnoreCase(partner.getStatus())) {
            return fail(1, "Customer reference is inactive");
        }

        BigDecimal amountPaid;
        try {
            amountPaid = decimalOf(request, "amountPaid", "AmountPaid", "amount");
        } catch (IllegalArgumentException ex) {
            return fail(1, ex.getMessage());
        }
        if (amountPaid == null) {
            return fail(1, "Amount paid cannot be empty");
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            return fail(1, "Amount paid must be greater than zero");
        }

        String sasulaReceipt = firstText(
                request,
                "sasulaReceiptNumber",
                "SasulaReceiptNumber",
                "sasulaReceipt"
        );
        if (!StringUtils.hasText(sasulaReceipt)) {
            return fail(1, "Sasula receipt number cannot be empty");
        }
        if (creditTransactionRepository.existsByTypeAndReference(CreditTransaction.CreditTxType.TOPUP, sasulaReceipt)) {
            return fail(1, "Duplicate Sasula receipt number");
        }

        JSONObject meta = new JSONObject();
        meta.put("paymentChannel", firstText(request, "paymentChannel", "PaymentChannel"));
        meta.put("thirdPartyTransactionReference", firstText(
                request,
                "thirdPartyTransactionReference",
                "thirdpartyTransactionReference",
                "ThirdpartyTransactionReference"
        ));
        meta.put("channelTransactionId", firstText(request, "channelTransactionId", "ChannelTransactionId"));
        meta.put("sasulaReceiptNumber", sasulaReceipt);
        meta.put("datePaid", firstText(request, "datePaid", "DatePaid"));
        meta.put("transactionDescription", firstText(request, "transactionDescription", "TransactionDescription"));
        meta.put("payerNumber", firstText(request, "payerNumber", "PayerNumber"));
        meta.put("payerName", firstText(request, "payerName", "PayerName"));

        CreditTransaction tx;
        try {
            tx = creditAccountService.topup(partner, amountPaid, sasulaReceipt, meta);
        } catch (DataIntegrityViolationException duplicate) {
            return fail(1, "Duplicate Sasula receipt number");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerReference", partner.getPartnerCode());
        payload.put("customerName", partner.getName());
        payload.put("amountPaid", tx.getAmount());
        payload.put("balanceAfter", tx.getBalanceAfter());
        payload.put("transactionId", tx.getId());
        payload.put("sasulaReceiptNumber", sasulaReceipt);
        payload.put("reference", tx.getReference());
        payload.put("currency", partner.getCurrency());
        return success("Payment posted", payload);
    }

    private String firstText(JSONObject request, String... keys) {
        if (request == null) {
            return null;
        }
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private BigDecimal decimalOf(JSONObject request, String... keys) {
        String raw = firstText(request, keys);
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Amount paid must be a number");
        }
    }

    private OperationReturnObject success(String message, Object payload) {
        OperationReturnObject res = new OperationReturnObject();
        res.setCodeAndMessageAndReturnObject(0, message, payload);
        return res;
    }

    private OperationReturnObject fail(int code, String message) {
        OperationReturnObject res = new OperationReturnObject();
        res.setReturnCodeAndReturnMessage(code, message);
        return res;
    }
}
