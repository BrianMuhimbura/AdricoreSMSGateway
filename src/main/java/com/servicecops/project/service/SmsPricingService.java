package com.servicecops.project.service;

import com.servicecops.project.models.database.Partner;
import com.servicecops.project.models.database.SmsPricingProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class SmsPricingService {
    private static final String PER_SEGMENT = "PER_SEGMENT";

    private final PartnerPricingService partnerPricingService;

    public record SmsQuote(
            SmsPricingProfile profile,
            int segments,
            int billedUnits,
            BigDecimal unitPrice,
            BigDecimal cost,
            BigDecimal unitCost,
            BigDecimal profit
    ) {
    }

    public SmsQuote quote(Partner partner, String text) {
        return quote(partner, segmentCount(text));
    }

    public SmsQuote quote(Partner partner, int segments) {
        SmsPricingProfile profile = partnerPricingService.activeProfileFor(partner);
        BigDecimal unit = profile.getSellingPricePerSms();
        if (unit == null) {
            throw new IllegalStateException("Pricing profile has no selling price");
        }
        int quantity = 1;
        if (PER_SEGMENT.equalsIgnoreCase(profile.getBillingType()) && segments > 1) {
            quantity = segments;
        }
        BigDecimal billed = BigDecimal.valueOf(quantity);
        BigDecimal cost = unit.multiply(billed);
        BigDecimal unitCost = nz(profile.getProviderCostPerSms()).setScale(6, RoundingMode.HALF_UP);
        BigDecimal profit = nz(profile.getTopupFeePerSms()).multiply(billed);
        return new SmsQuote(profile, Math.max(segments, 1), quantity, unit, cost, unitCost, profit);
    }

    public BigDecimal calculateSmsCost(Partner partner, int segments) {
        return quote(partner, segments).cost();
    }

    public int segmentCount(String text) {
        int length = text == null ? 0 : text.length();
        if (length <= 160) {
            return 1;
        }
        return (length + 152) / 153;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
