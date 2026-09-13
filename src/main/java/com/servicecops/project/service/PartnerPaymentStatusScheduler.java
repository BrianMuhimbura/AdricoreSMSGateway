package com.servicecops.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerPaymentStatusScheduler {
    private final PartnerTopupService partnerTopupService;

    @Scheduled(fixedDelayString = "${sasula.payments.status-poll-ms:15000}")
    public void poll() {
        for (java.util.UUID paymentRequestId : partnerTopupService.inFlightIds()) {
            try {
                partnerTopupService.refreshStatus(paymentRequestId);
            } catch (Exception error) {
                log.warn("Unable to refresh payment {}: {}", paymentRequestId, error.getMessage());
            }
        }
    }
}
