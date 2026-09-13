package com.servicecops.project.service;

import com.servicecops.project.integrations.sasula.payments.SasulaPaymentsClient;
import com.servicecops.project.integrations.sasula.payments.SasulaPaymentsException;
import com.servicecops.project.integrations.sasula.payments.SasulaPaymentsProperties;
import com.servicecops.project.integrations.sasula.payments.dto.BusinessDebitRequest;
import com.servicecops.project.integrations.sasula.payments.dto.DebitRequest;
import com.servicecops.project.integrations.sasula.payments.dto.DebitResponse;
import com.servicecops.project.integrations.sasula.payments.dto.RequestStatus;
import com.servicecops.project.models.database.BillingInvoice;
import com.servicecops.project.models.database.CreditTransaction;
import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.PartnerPaymentRequest;
import com.servicecops.project.repositories.BillingInvoiceRepository;
import com.servicecops.project.repositories.CreditTransactionRepository;
import com.servicecops.project.repositories.PartnerPaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerTopupService {
    private static final List<String> IN_FLIGHT = List.of(
            PartnerPaymentRequest.PENDING,
            PartnerPaymentRequest.REQUESTED
    );

    private final BillingInvoiceRepository invoiceRepository;
    private final PartnerPaymentRequestRepository paymentRequestRepository;
    private final CreditAccountService creditAccountService;
    private final CreditTransactionRepository creditTransactionRepository;
    private final BillingInvoiceService billingInvoiceService;
    private final SasulaPaymentsClient sasulaPaymentsClient;
    private final SasulaPaymentsProperties sasulaPaymentsProperties;

    @Transactional
    public BillingInvoice createInvoice(Partner partner, BigDecimal amount) {
        BillingInvoice invoice = new BillingInvoice();
        invoice.setPartner(partner);
        invoice.setAmount(amount);
        invoice.setStatus("pending");
        invoice.setDueDate(LocalDate.now().plusDays(7));
        invoice.setCreatedAt(Instant.now());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "dashboard_topup");
        invoice.setMeta(meta);
        return invoiceRepository.saveAndFlush(invoice);
    }

    @Transactional
    public PartnerPaymentRequest payNow(Partner partner, UUID invoiceId, String customerPhone) {
        sasulaPaymentsProperties.requireChannel();
        BillingInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        if (invoice.getPartner() == null || !invoice.getPartner().getId().equals(partner.getId())) {
            throw new IllegalArgumentException("Invoice does not belong to this partner");
        }
        if (!"pending".equalsIgnoreCase(invoice.getStatus())) {
            throw new IllegalArgumentException("This invoice is no longer awaiting payment");
        }
        if (paymentRequestRepository.existsByInvoice_IdAndStatus(invoice.getId(), PartnerPaymentRequest.SUCCESSFUL)) {
            throw new IllegalArgumentException("This invoice has already been paid");
        }

        PartnerPaymentRequest existing = paymentRequestRepository
                .findFirstByInvoice_IdAndStatusInOrderByRequestedAtDesc(invoice.getId(), IN_FLIGHT)
                .orElse(null);
        if (existing != null) {
            return refreshStatus(existing.getId());
        }

        String phone = normalizeMsisdn(customerPhone);
        String externalId = "TOP" + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();

        PartnerPaymentRequest payment = new PartnerPaymentRequest();
        payment.setInvoice(invoice);
        payment.setPartner(partner);
        payment.setAmount(invoice.getAmount());
        payment.setCustomerPhone(phone);
        payment.setExternalTransactionId(externalId);
        payment.setStatus(PartnerPaymentRequest.PENDING);
        payment.setRequestedAt(now);
        payment.setCreatedAt(now);
        payment = paymentRequestRepository.saveAndFlush(payment);

        BusinessDebitRequest debit = BusinessDebitRequest.builder()
                .amount(sasulaAmount(invoice.getAmount()))
                .businessPaymentReference(partner.getPartnerCode())
                .callbackUrl(blankToEmpty(sasulaPaymentsProperties.getCallbackUrl()))
                .channelCode(sasulaPaymentsProperties.getChannelCode())
                .channepPassword(sasulaPaymentsProperties.getChannelPassword())
                .customerPhone(phone)
                .externalTransactionId(externalId)
                .reason("SMS credit top-up " + invoice.getId())
                .build();

        try {
            DebitResponse response = sasulaPaymentsClient.businessPaymentRequest(debit);
            applySasulaResponse(payment, response, false);
        } catch (SasulaPaymentsException error) {
            payment.setStatus(PartnerPaymentRequest.FAILED);
            payment.setReturnMessage(error.getMessage());
            payment.setProcessedAt(Instant.now());
            paymentRequestRepository.save(payment);
            throw new IllegalArgumentException(error.getMessage());
        }
        return paymentRequestRepository.save(payment);
    }

    @Transactional
    public PartnerPaymentRequest refreshStatusForPartner(UUID paymentRequestId, UUID partnerId) {
        PartnerPaymentRequest owned = paymentRequestRepository.findByIdAndPartner_Id(paymentRequestId, partnerId)
                .orElseThrow(() -> new IllegalArgumentException("Payment request not found"));
        return refreshStatus(owned.getId());
    }

    @Transactional
    public PartnerPaymentRequest refreshStatus(UUID paymentRequestId) {
        PartnerPaymentRequest payment = paymentRequestRepository.lockById(paymentRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Payment request not found"));
        return refreshStatus(payment);
    }

    public List<UUID> inFlightIds() {
        return paymentRequestRepository.findByStatusInOrderByRequestedAtAsc(IN_FLIGHT)
                .stream()
                .map(PartnerPaymentRequest::getId)
                .toList();
    }

    @Transactional
    public boolean captureCollection(Partner partner, BigDecimal amountPaid, String reference) {
        PartnerPaymentRequest match = findCollectionMatch(partner, amountPaid, reference);
        if (match == null) {
            return false;
        }
        PartnerPaymentRequest locked = paymentRequestRepository.lockById(match.getId()).orElse(match);
        if (locked.getCreditedAt() != null) {
            markInvoicePaid(locked.getInvoice());
            return true;
        }
        locked.setStatus(PartnerPaymentRequest.SUCCESSFUL);
        if (!StringUtils.hasText(locked.getMmTransactionId())) {
            locked.setMmTransactionId(reference);
        }
        creditIfNeeded(locked, reference);
        markInvoicePaid(locked.getInvoice());
        if (locked.getProcessedAt() == null) {
            locked.setProcessedAt(Instant.now());
        }
        paymentRequestRepository.save(locked);
        return true;
    }

    public Map<String, Object> toTopupResult(Partner partner, BillingInvoice invoice, PartnerPaymentRequest payment) {
        Map<String, Object> payload = toPublicInvoice(partner, invoice);
        payload.put("invoiceId", invoice.getId());
        payload.put("invoice", toPublicInvoice(partner, invoice));
        payload.put("payment", payment == null ? null : toPublicPayment(payment));
        payload.put(
                "instructions",
                "Approve the mobile money prompt on your phone. Credit is applied when the payment succeeds."
        );
        return payload;
    }

    public Map<String, Object> toPublicInvoice(Partner partner, BillingInvoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", invoice.getId());
        data.put("amount", invoice.getAmount());
        data.put("status", invoice.getStatus());
        data.put("dueDate", invoice.getDueDate());
        data.put("createdAt", invoice.getCreatedAt());
        data.put("currency", partner.getCurrency());
        data.put("partnerCode", partner.getPartnerCode());
        data.put("outstanding", billingInvoiceService.sumPendingFor(partner.getId()));
        return data;
    }

    public Map<String, Object> toPublicPayment(PartnerPaymentRequest payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", payment.getId());
        data.put("invoiceId", payment.getInvoice() != null ? payment.getInvoice().getId() : null);
        data.put("amount", payment.getAmount());
        data.put("customerPhone", payment.getCustomerPhone());
        data.put("externalTransactionId", payment.getExternalTransactionId());
        data.put("status", payment.getStatus());
        data.put("mmTransactionId", payment.getMmTransactionId());
        data.put("processingNumber", payment.getProcessingNumber());
        data.put("returnCode", payment.getReturnCode());
        data.put("returnMessage", payment.getReturnMessage());
        data.put("requestedAt", payment.getRequestedAt());
        data.put("processedAt", payment.getProcessedAt());
        data.put("creditedAt", payment.getCreditedAt());
        return data;
    }

    private PartnerPaymentRequest refreshStatus(PartnerPaymentRequest payment) {
        if (PartnerPaymentRequest.SUCCESSFUL.equals(payment.getStatus())
                || PartnerPaymentRequest.FAILED.equals(payment.getStatus())) {
            if (PartnerPaymentRequest.SUCCESSFUL.equals(payment.getStatus())) {
                creditIfNeeded(payment, payment.getMmTransactionId());
                markInvoicePaid(payment.getInvoice());
            }
            return paymentRequestRepository.save(payment);
        }

        sasulaPaymentsProperties.requireChannel();
        DebitRequest statusRequest = DebitRequest.builder()
                .externalTransactionId(payment.getExternalTransactionId())
                .channelCode(sasulaPaymentsProperties.getChannelCode())
                .channepPassword(sasulaPaymentsProperties.getChannelPassword())
                .customerPhone(payment.getCustomerPhone())
                .amount(payment.getAmount())
                .build();
        try {
            DebitResponse response = sasulaPaymentsClient.paymentStatus(statusRequest);
            applySasulaResponse(payment, response, true);
        } catch (SasulaPaymentsException error) {
            log.warn("Sasula paymentStatus failed for {}: {}", payment.getExternalTransactionId(), error.getMessage());
            payment.setReturnMessage(error.getMessage());
            payment.setLastCheckedAt(Instant.now());
        }
        return paymentRequestRepository.save(payment);
    }

    private void applySasulaResponse(PartnerPaymentRequest payment, DebitResponse response, boolean fromStatusCheck) {
        payment.setLastCheckedAt(Instant.now());
        if (response.getReturnCode() != null) {
            payment.setReturnCode(response.getReturnCode());
        }
        payment.setReturnMessage(response.getReturnMessage());
        payment.setSasulaRequestId(response.getRequestId());
        if (StringUtils.hasText(response.getMmTransactionId())) {
            payment.setMmTransactionId(response.getMmTransactionId());
        }
        if (StringUtils.hasText(response.getProcessingNumber())) {
            payment.setProcessingNumber(response.getProcessingNumber());
        }

        String mapped = mapStatus(response);
        payment.setStatus(mapped);
        if (PartnerPaymentRequest.SUCCESSFUL.equals(mapped)) {
            creditIfNeeded(payment, firstText(response.getMmTransactionId(), payment.getExternalTransactionId()));
            markInvoicePaid(payment.getInvoice());
            payment.setProcessedAt(Instant.now());
        } else if (PartnerPaymentRequest.FAILED.equals(mapped)) {
            payment.setProcessedAt(Instant.now());
        } else if (!fromStatusCheck && response.getReturnCode() != null && response.getReturnCode() != 0) {
            payment.setStatus(PartnerPaymentRequest.FAILED);
            payment.setProcessedAt(Instant.now());
        }
    }

    private String mapStatus(DebitResponse response) {
        RequestStatus status = response.getStatus();
        if (status == RequestStatus.SUCCESSFUL) {
            return PartnerPaymentRequest.SUCCESSFUL;
        }
        if (status == RequestStatus.FAILED) {
            return PartnerPaymentRequest.FAILED;
        }
        if (status == RequestStatus.REQUESTED) {
            return PartnerPaymentRequest.REQUESTED;
        }
        if (response.getReturnCode() != null && response.getReturnCode() != 0) {
            return PartnerPaymentRequest.FAILED;
        }
        return PartnerPaymentRequest.PENDING;
    }

    private void creditIfNeeded(PartnerPaymentRequest payment, String preferredReference) {
        if (payment.getCreditedAt() != null) {
            return;
        }
        String reference = firstText(preferredReference, payment.getMmTransactionId(), payment.getExternalTransactionId());
        if (!StringUtils.hasText(reference)) {
            reference = payment.getExternalTransactionId();
        }
        if (alreadyCredited(reference, payment.getExternalTransactionId(), payment.getMmTransactionId())) {
            payment.setCreditedAt(Instant.now());
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", "sasula_momo");
        meta.put("invoiceId", payment.getInvoice() != null ? String.valueOf(payment.getInvoice().getId()) : null);
        meta.put("paymentRequestId", String.valueOf(payment.getId()));
        meta.put("payerNumber", payment.getCustomerPhone());
        creditAccountService.topup(payment.getPartner(), payment.getAmount(), reference, meta);
        payment.setCreditedAt(Instant.now());
    }

    private boolean alreadyCredited(String... references) {
        for (String reference : references) {
            if (StringUtils.hasText(reference)
                    && creditTransactionRepository.existsByTypeAndReference(CreditTransaction.CreditTxType.TOPUP, reference)) {
                return true;
            }
        }
        return false;
    }

    private void markInvoicePaid(BillingInvoice invoice) {
        if (invoice == null || !"pending".equalsIgnoreCase(invoice.getStatus())) {
            return;
        }
        invoice.setStatus("paid");
        invoiceRepository.save(invoice);
    }

    public void markPendingInvoicesPaid(UUID partnerId, BigDecimal amountPaid) {
        List<BillingInvoice> pending = invoiceRepository
                .findByPartner_IdAndStatusIgnoreCaseOrderByCreatedAtAsc(partnerId, "pending");
        BigDecimal remaining = amountPaid;
        for (BillingInvoice invoice : pending) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            invoice.setStatus("paid");
            invoiceRepository.save(invoice);
            remaining = remaining.subtract(invoice.getAmount());
        }
    }

    private PartnerPaymentRequest findCollectionMatch(Partner partner, BigDecimal amountPaid, String reference) {
        if (StringUtils.hasText(reference)) {
            PartnerPaymentRequest byExternal = paymentRequestRepository.findByExternalTransactionId(reference).orElse(null);
            if (byExternal != null && byExternal.getPartner() != null
                    && byExternal.getPartner().getId().equals(partner.getId())) {
                return byExternal;
            }
            PartnerPaymentRequest byMm = paymentRequestRepository
                    .findFirstByMmTransactionIdOrderByRequestedAtDesc(reference)
                    .orElse(null);
            if (byMm != null && byMm.getPartner() != null
                    && byMm.getPartner().getId().equals(partner.getId())) {
                return byMm;
            }
        }
        return paymentRequestRepository
                .findFirstByPartner_IdAndAmountAndStatusInAndCreditedAtIsNullOrderByRequestedAtDesc(
                        partner.getId(),
                        amountPaid,
                        List.of(PartnerPaymentRequest.PENDING, PartnerPaymentRequest.REQUESTED, PartnerPaymentRequest.SUCCESSFUL)
                )
                .orElseGet(() -> paymentRequestRepository
                        .findFirstByPartner_IdAndAmountAndStatusInOrderByRequestedAtDesc(
                                partner.getId(),
                                amountPaid,
                                List.of(PartnerPaymentRequest.SUCCESSFUL)
                        )
                        .orElse(null));
    }

    private String normalizeMsisdn(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("Phone number cannot be empty");
        }
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
        throw new IllegalArgumentException("Enter a valid Ugandan mobile number");
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private BigDecimal sasulaAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized;
    }

    private String blankToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
