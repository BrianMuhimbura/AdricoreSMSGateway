-- sms_migration.sql
-- Full PostgreSQL migration script for SMS Aggregation Gateway (MVP)
-- Run on PostgreSQL 12+ (adjust as needed). Intended as a starting point.

-- ==============
-- Extensions
-- ==============
CREATE EXTENSION IF NOT EXISTS pgcrypto; -- for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- optional, useful for search/indexing

-- ==============
-- Helper: timestamp function
-- ==============
-- Nothing required; we use now() and timestamptz.

-- ==============
-- Organizations & Partners
-- ==============
CREATE TABLE IF NOT EXISTS organizations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  metadata jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Sequence for partner_code (starts at 100001)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = 'partner_code_seq') THEN
    CREATE SEQUENCE partner_code_seq START 100001 INCREMENT 1 MINVALUE 100001;
  END IF;
END$$;

CREATE TABLE IF NOT EXISTS partners (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id uuid REFERENCES organizations(id) ON DELETE CASCADE,
  parent_partner_id uuid REFERENCES partners(id) ON DELETE SET NULL,
  partner_code text UNIQUE NOT NULL DEFAULT 'ADRS' || nextval('partner_code_seq'),
  name text NOT NULL,
  email text,
  phone text,
  status text NOT NULL DEFAULT 'active', -- consider enum in future
  currency text NOT NULL DEFAULT 'UGX',
  price_profile jsonb, -- flexible pricing rules
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

-- Ensure sequence owned by the column (idempotent)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'partner_code_seq') THEN
    BEGIN
      ALTER SEQUENCE partner_code_seq OWNED BY partners.partner_code;
    EXCEPTION WHEN undefined_column THEN
      -- Column may not exist yet in some re-run scenarios; ignore
    END;
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_partners_org ON partners(organization_id);
CREATE INDEX IF NOT EXISTS idx_partners_parent ON partners(parent_partner_id);

-- auto-update updated_at on partners
CREATE OR REPLACE FUNCTION trg_updated_at_column()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS partners_updated_at ON partners;
CREATE TRIGGER partners_updated_at
  BEFORE UPDATE ON partners
  FOR EACH ROW EXECUTE FUNCTION trg_updated_at_column();

-- ==============
-- API Credentials
-- ==============
CREATE TABLE IF NOT EXISTS api_credentials (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  partner_id uuid REFERENCES partners(id) ON DELETE CASCADE,
  api_key text UNIQUE NOT NULL,
  secret_hash text NOT NULL, -- hash of secret (bcrypt/argon2 stored by app) or encrypted secret
  allowed_ips text[], -- optional IP whitelist
  callback_url text,   -- optional default DLR callback
  meta jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_api_credentials_partner ON api_credentials(partner_id);

-- ==============
-- Credit accounts & transactions
-- ==============
CREATE TABLE IF NOT EXISTS credit_accounts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  partner_id uuid UNIQUE REFERENCES partners(id) ON DELETE CASCADE,
  balance numeric(18,6) NOT NULL DEFAULT 0,   -- available balance
  reserved numeric(18,6) NOT NULL DEFAULT 0,  -- reserved for in-flight requests
  currency text NOT NULL DEFAULT 'UGX',
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_credit_accounts_partner ON credit_accounts(partner_id);

-- updated_at trigger
DROP TRIGGER IF EXISTS credit_accounts_updated_at ON credit_accounts;
CREATE TRIGGER credit_accounts_updated_at
  BEFORE UPDATE ON credit_accounts
  FOR EACH ROW EXECUTE FUNCTION trg_updated_at_column();

-- Transaction types
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'credit_tx_type') THEN
    CREATE TYPE credit_tx_type AS ENUM ('TOPUP','ALLOCATE','CONSUMPTION','REFUND','REVERSAL');
  END IF;
END$$;

CREATE TABLE IF NOT EXISTS credit_transactions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  partner_id uuid REFERENCES partners(id) ON DELETE CASCADE,
  type credit_tx_type NOT NULL,
  amount numeric(18,6) NOT NULL, -- positive for topups, negative for consumption? keep positive and use type
  balance_after numeric(18,6) NOT NULL,
  reference text,
  meta jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_credit_tx_partner_created ON credit_transactions(partner_id, created_at DESC);

-- ==============
-- Providers & Routes
-- ==============
CREATE TABLE IF NOT EXISTS providers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  protocol text NOT NULL, -- 'SMPP' | 'HTTP'
  config jsonb,           -- connection config (host, port, credentials); encrypted at app layer
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS routes (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_id uuid REFERENCES providers(id) ON DELETE SET NULL,
  name text,
  priority int NOT NULL DEFAULT 10,
  filter_rules jsonb, -- e.g. country, mcc, etc.
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_routes_provider ON routes(provider_id);

-- ==============
-- Messages & Delivery Receipts
-- ==============
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'message_status') THEN
    CREATE TYPE message_status AS ENUM('QUEUED','SENDING','SENT','ACCEPTED','DELIVERED','FAILED','REJECTED','EXPIRED');
  END IF;
END$$;

CREATE TABLE IF NOT EXISTS messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  partner_id uuid REFERENCES partners(id) ON DELETE SET NULL,
  credential_id uuid REFERENCES api_credentials(id) ON DELETE SET NULL,
  from_addr text,
  to_addr text NOT NULL,
  text text NOT NULL,
  encoding text DEFAULT 'GSM_7', -- GSM_7 | UCS2 | etc
  segments int DEFAULT 1,
  cost numeric(18,6) DEFAULT 0,
  status message_status DEFAULT 'QUEUED',
  upstream_id text, -- id returned by provider
  route_id uuid REFERENCES routes(id) ON DELETE SET NULL,
  callback_url text, -- per-message override
  provider_response jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_partner_status ON messages(partner_id, status);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);
CREATE INDEX IF NOT EXISTS idx_messages_upstream_id ON messages(upstream_id);

-- updated_at trigger for messages
DROP TRIGGER IF EXISTS messages_updated_at ON messages;
CREATE TRIGGER messages_updated_at
  BEFORE UPDATE ON messages
  FOR EACH ROW EXECUTE FUNCTION trg_updated_at_column();

CREATE TABLE IF NOT EXISTS delivery_receipts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id uuid REFERENCES messages(id) ON DELETE CASCADE,
  provider_status text,
  provider_code text,
  raw_payload jsonb,
  received_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dlr_message ON delivery_receipts(message_id);

-- ==============
-- Billing & Invoices (Basic)
-- ==============
CREATE TABLE IF NOT EXISTS billing_invoices (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  partner_id uuid REFERENCES partners(id) ON DELETE CASCADE,
  amount numeric(18,6) NOT NULL,
  due_date date,
  status text NOT NULL DEFAULT 'pending',
  meta jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_billing_partner ON billing_invoices(partner_id);

-- ==============
-- Admin / Audit trail
-- ==============
CREATE TABLE IF NOT EXISTS audit_logs (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_type text, -- e.g. 'admin','partner','system'
  actor_id uuid,
  action text NOT NULL,
  resource_type text,
  resource_id uuid,
  details jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_actor_created ON audit_logs(actor_id, created_at DESC);

-- ==============
-- Useful helper functions for credit operations
-- These functions perform atomic balance checks and updates to avoid race conditions.
-- ==============

-- Function: try_reserve_credit(partner_id, amount)
-- Reserves `amount` by moving from balance -> reserved. Returns true if successful.
CREATE OR REPLACE FUNCTION try_reserve_credit(p_partner uuid, p_amount numeric)
RETURNS boolean LANGUAGE plpgsql AS $$
DECLARE
  v_balance numeric;
BEGIN
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'reserve amount must be positive';
  END IF;

  -- Lock the credit_accounts row to avoid race conditions
  UPDATE credit_accounts
  SET balance = balance - p_amount,
      reserved = reserved + p_amount,
      updated_at = now()
  WHERE partner_id = p_partner
    AND balance >= p_amount;

  IF FOUND THEN
    RETURN TRUE;
  ELSE
    RETURN FALSE;
  END IF;
END;
$$;

-- Function: confirm_consume_reserved(partner_id, amount, reference)
-- Consumes reserved credit (after a message is accepted/sent). Decreases reserved and records transaction.
CREATE OR REPLACE FUNCTION confirm_consume_reserved(p_partner uuid, p_amount numeric, p_reference text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_account_id uuid;
  v_new_balance numeric;
BEGIN
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'consume amount must be positive';
  END IF;

  -- Ensure reserved is sufficient and deduct
  UPDATE credit_accounts
  SET reserved = reserved - p_amount,
      updated_at = now()
  WHERE partner_id = p_partner
    AND reserved >= p_amount;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'insufficient reserved balance for partner %', p_partner;
  END IF;

  -- get resulting balance (available)
  SELECT balance INTO v_new_balance FROM credit_accounts WHERE partner_id = p_partner;

  -- Log transaction
  INSERT INTO credit_transactions(partner_id, type, amount, balance_after, reference, created_at)
  VALUES (p_partner, 'consumption', p_amount, v_new_balance, p_reference, now());
END;
$$;

-- Function: release_reserved_credit(partner_id, amount, reason)
-- Releases reserved credit back to available balance (e.g., on failure)
CREATE OR REPLACE FUNCTION release_reserved_credit(p_partner uuid, p_amount numeric, p_reason text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_new_balance numeric;
BEGIN
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'release amount must be positive';
  END IF;

  UPDATE credit_accounts
  SET reserved = reserved - p_amount,
      balance = balance + p_amount,
      updated_at = now()
  WHERE partner_id = p_partner
    AND reserved >= p_amount;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'insufficient reserved to release for partner %', p_partner;
  END IF;

  SELECT balance INTO v_new_balance FROM credit_accounts WHERE partner_id = p_partner;

  INSERT INTO credit_transactions(partner_id, type, amount, balance_after, reference, created_at)
  VALUES (p_partner, 'refund', p_amount, v_new_balance, p_reason, now());
END;
$$;

-- Function: topup_credit(partner_id, amount, reference)
-- Adds amount to partner balance and logs transaction.
CREATE OR REPLACE FUNCTION topup_credit(p_partner uuid, p_amount numeric, p_reference text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_new_balance numeric;
BEGIN
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'topup amount must be positive';
  END IF;

  UPDATE credit_accounts
  SET balance = balance + p_amount,
      updated_at = now()
  WHERE partner_id = p_partner;

  IF NOT FOUND THEN
    -- create account if missing
    INSERT INTO credit_accounts(partner_id, balance, reserved, currency, updated_at)
    VALUES (p_partner, p_amount, 0, 'UGX', now());
  END IF;

  SELECT balance INTO v_new_balance FROM credit_accounts WHERE partner_id = p_partner;

  INSERT INTO credit_transactions(partner_id, type, amount, balance_after, reference, created_at)
  VALUES (p_partner, 'topup', p_amount, v_new_balance, p_reference, now());
END;
$$;

-- ==============
-- Example triggers to maintain referential integrity or denormalizations
-- ==============
-- Optionally add a trigger to auto-create credit_account when a partner is created
CREATE OR REPLACE FUNCTION create_credit_account_on_partner_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO credit_accounts(partner_id, balance, reserved, currency, updated_at)
  VALUES (NEW.id, 0, 0, COALESCE(NEW.currency, 'UGX'), now())
  ON CONFLICT (partner_id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_after_partner_insert ON partners;
CREATE TRIGGER trg_after_partner_insert
  AFTER INSERT ON partners
  FOR EACH ROW EXECUTE FUNCTION create_credit_account_on_partner_insert();

-- ==============
-- Sample data (optional)
-- ==============
-- INSERT INTO organizations (name) VALUES ('Default Org');
-- INSERT INTO partners (organization_id, name, email) VALUES ((SELECT id FROM organizations LIMIT 1), 'Test Partner', 'test@partner.local');

-- ==============
-- Performance & maintenance notes
-- ==============
-- Add more indexes for queries you make often (e.g. messages by partner + date).
-- Consider partitioning messages by range (created_at) if you will store millions per month.
-- Use connection pooling and avoid long-running transactions for DLR processing.
-- Protect secrets (provider config, api secret) using an external secrets manager in production.

-- End of migration
