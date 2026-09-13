-- Redesign SMS pricing: named plans with provider cost, top-up fee, and selling price.
-- Partner ids stay UUID; assignment table links partners to profiles.

CREATE TABLE IF NOT EXISTS sms_pricing_profile (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_code            VARCHAR(50) NOT NULL,
    profile_name            VARCHAR(150) NOT NULL,
    description             TEXT,
    currency_code           VARCHAR(3) NOT NULL DEFAULT 'UGX',
    provider_cost_per_sms   NUMERIC(19,4) NOT NULL DEFAULT 25.0000,
    topup_fee_percent       NUMERIC(7,4) NOT NULL DEFAULT 5.0000,
    selling_price_per_sms   NUMERIC(19,4) NOT NULL,
    minimum_sms_volume      BIGINT NOT NULL DEFAULT 0,
    maximum_sms_volume      BIGINT,
    billing_type            VARCHAR(30) NOT NULL DEFAULT 'PER_SMS',
    is_default              BOOLEAN NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    CONSTRAINT uq_sms_pricing_profile_code UNIQUE (profile_code),
    CONSTRAINT chk_sms_profile_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_sms_profile_provider_cost CHECK (provider_cost_per_sms >= 0),
    CONSTRAINT chk_sms_profile_topup_fee CHECK (topup_fee_percent >= 0 AND topup_fee_percent <= 100),
    CONSTRAINT chk_sms_profile_selling_price CHECK (selling_price_per_sms >= 0),
    CONSTRAINT chk_sms_profile_min_volume CHECK (minimum_sms_volume >= 0),
    CONSTRAINT chk_sms_profile_max_volume CHECK (
        maximum_sms_volume IS NULL OR maximum_sms_volume >= minimum_sms_volume
    ),
    CONSTRAINT chk_sms_profile_billing_type CHECK (billing_type IN ('PER_SMS', 'PER_SEGMENT')),
    CONSTRAINT chk_sms_profile_effective_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE OR REPLACE VIEW v_sms_pricing_profile_cost AS
SELECT
    p.id,
    p.profile_code,
    p.profile_name,
    p.currency_code,
    p.provider_cost_per_sms,
    p.topup_fee_percent,
    ROUND(p.provider_cost_per_sms * (1 + p.topup_fee_percent / 100), 4) AS effective_cost_per_sms,
    p.selling_price_per_sms,
    ROUND(
        p.selling_price_per_sms - (p.provider_cost_per_sms * (1 + p.topup_fee_percent / 100)),
        4
    ) AS gross_profit_per_sms,
    CASE
        WHEN p.selling_price_per_sms > 0 THEN
            ROUND(
                (
                    p.selling_price_per_sms
                    - (p.provider_cost_per_sms * (1 + p.topup_fee_percent / 100))
                ) / p.selling_price_per_sms * 100,
                4
            )
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

CREATE TABLE IF NOT EXISTS sms_partner_pricing_profile (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    partner_id          UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    pricing_profile_id  BIGINT NOT NULL REFERENCES sms_pricing_profile(id),
    effective_from      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to        TIMESTAMPTZ,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    CONSTRAINT chk_partner_profile_dates CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE INDEX IF NOT EXISTS idx_partner_sms_profile_partner
    ON sms_partner_pricing_profile (partner_id);

CREATE INDEX IF NOT EXISTS idx_partner_sms_profile_profile
    ON sms_partner_pricing_profile (pricing_profile_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_partner_active_sms_profile
    ON sms_partner_pricing_profile (partner_id)
    WHERE is_active = TRUE AND effective_to IS NULL;

INSERT INTO sms_pricing_profile (
    profile_code, profile_name, description, currency_code,
    provider_cost_per_sms, topup_fee_percent, selling_price_per_sms,
    minimum_sms_volume, maximum_sms_volume, billing_type, is_default
)
VALUES
    ('STARTER', 'Starter Profile', 'For low-volume customers', 'UGX', 25.0000, 5.0000, 30.0000, 0, 1000, 'PER_SMS', TRUE),
    ('BUSINESS', 'Business Profile', 'For regular business customers', 'UGX', 25.0000, 5.0000, 29.0000, 1001, 10000, 'PER_SMS', FALSE),
    ('PROFESSIONAL', 'Professional Profile', 'For high-volume customers', 'UGX', 25.0000, 5.0000, 28.0000, 10001, 50000, 'PER_SMS', FALSE),
    ('ENTERPRISE', 'Enterprise Profile', 'For very high-volume customers', 'UGX', 25.0000, 5.0000, 27.0000, 50001, NULL, 'PER_SMS', FALSE)
ON CONFLICT (profile_code) DO NOTHING;

-- Existing partners without an assignment get STARTER.
INSERT INTO sms_partner_pricing_profile (partner_id, pricing_profile_id, created_by)
SELECT p.id, sp.id, 'system'
FROM partners p
CROSS JOIN sms_pricing_profile sp
WHERE sp.profile_code = 'STARTER'
  AND NOT EXISTS (
      SELECT 1
      FROM sms_partner_pricing_profile a
      WHERE a.partner_id = p.id
        AND a.is_active = TRUE
        AND a.effective_to IS NULL
  );
