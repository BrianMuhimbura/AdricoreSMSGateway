-- Liquibase migration for SMS Pricing Profile

CREATE TABLE IF NOT EXISTS sms_pricing_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    rules JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE partners
ADD COLUMN IF NOT EXISTS sms_pricing_profile_id UUID REFERENCES sms_pricing_profiles(id);

CREATE INDEX IF NOT EXISTS idx_partners_sms_pricing_profile ON partners(sms_pricing_profile_id);

