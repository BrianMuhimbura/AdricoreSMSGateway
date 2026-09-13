-- Store top-up fee as an amount per SMS and keep selling price equal to cost + fee.

DROP VIEW IF EXISTS v_sms_pricing_profile_cost;

ALTER TABLE sms_pricing_profile DROP CONSTRAINT IF EXISTS chk_sms_profile_topup_fee;

ALTER TABLE sms_pricing_profile
    ALTER COLUMN topup_fee_percent TYPE NUMERIC(19,4);

UPDATE sms_pricing_profile
SET topup_fee_percent = ROUND(GREATEST(0, selling_price_per_sms - provider_cost_per_sms), 4);

ALTER TABLE sms_pricing_profile RENAME COLUMN topup_fee_percent TO topup_fee_per_sms;

ALTER TABLE sms_pricing_profile
    ALTER COLUMN topup_fee_per_sms SET DEFAULT 0;

ALTER TABLE sms_pricing_profile
    ADD CONSTRAINT chk_sms_profile_topup_fee CHECK (topup_fee_per_sms >= 0);

UPDATE sms_pricing_profile
SET selling_price_per_sms = ROUND(provider_cost_per_sms + topup_fee_per_sms, 4);

CREATE VIEW v_sms_pricing_profile_cost AS
SELECT
    p.id,
    p.profile_code,
    p.profile_name,
    p.currency_code,
    p.provider_cost_per_sms,
    p.topup_fee_per_sms,
    p.provider_cost_per_sms AS effective_cost_per_sms,
    p.selling_price_per_sms,
    p.topup_fee_per_sms AS gross_profit_per_sms,
    CASE
        WHEN p.selling_price_per_sms > 0 THEN
            ROUND(p.topup_fee_per_sms / p.selling_price_per_sms * 100, 4)
        ELSE 0
    END AS gross_margin_percent,
    p.minimum_sms_volume,
    p.maximum_sms_volume,
    p.billing_type,
    p.is_default,
    p.is_active,
    p.effective_from,
    p.effective_to
FROM sms_pricing_profile p;
