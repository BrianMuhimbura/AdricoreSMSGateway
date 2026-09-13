-- Selling price is always provider cost plus the top-up fee amount.

UPDATE sms_pricing_profile
SET selling_price_per_sms = ROUND(COALESCE(provider_cost_per_sms, 0) + COALESCE(topup_fee_per_sms, 0), 4);

ALTER TABLE sms_pricing_profile DROP CONSTRAINT IF EXISTS chk_sms_profile_selling_price;
ALTER TABLE sms_pricing_profile DROP CONSTRAINT IF EXISTS chk_sms_profile_selling_equals_cost_plus_fee;

ALTER TABLE sms_pricing_profile
    ADD CONSTRAINT chk_sms_profile_selling_equals_cost_plus_fee
    CHECK (selling_price_per_sms = ROUND(provider_cost_per_sms + topup_fee_per_sms, 4));
