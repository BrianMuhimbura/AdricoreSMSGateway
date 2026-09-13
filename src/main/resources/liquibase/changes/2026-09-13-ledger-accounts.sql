-- Double-entry ledger: system control accounts plus debit/credit on every transaction.

ALTER TABLE credit_accounts
    ADD COLUMN IF NOT EXISTS account_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS account_name VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS uq_credit_accounts_code
    ON credit_accounts (account_code)
    WHERE account_code IS NOT NULL;

INSERT INTO credit_accounts (account_code, account_name, balance, reserved, currency, updated_at)
SELECT 'ADRICORE_CONTROL', 'Adricore Control', 0, 0, 'UGX', now()
WHERE NOT EXISTS (SELECT 1 FROM credit_accounts WHERE account_code = 'ADRICORE_CONTROL');

INSERT INTO credit_accounts (account_code, account_name, balance, reserved, currency, updated_at)
SELECT 'ADRICORE_OPERATIONS', 'Adricore Operations', 0, 0, 'UGX', now()
WHERE NOT EXISTS (SELECT 1 FROM credit_accounts WHERE account_code = 'ADRICORE_OPERATIONS');

INSERT INTO credit_accounts (account_code, account_name, balance, reserved, currency, updated_at)
SELECT 'ADRICORE_INCOME', 'Adricore Income', 0, 0, 'UGX', now()
WHERE NOT EXISTS (SELECT 1 FROM credit_accounts WHERE account_code = 'ADRICORE_INCOME');

UPDATE credit_accounts c
SET account_name = COALESCE(NULLIF(c.account_name, ''), p.name)
FROM partners p
WHERE c.partner_id = p.id
  AND (c.account_name IS NULL OR btrim(c.account_name) = '');

ALTER TABLE credit_transactions
    ADD COLUMN IF NOT EXISTS debit_account_id uuid,
    ADD COLUMN IF NOT EXISTS credit_account_id uuid,
    ADD COLUMN IF NOT EXISTS debit_balance_after numeric(18,6),
    ADD COLUMN IF NOT EXISTS credit_balance_after numeric(18,6);

UPDATE credit_transactions t
SET debit_account_id = COALESCE(
        t.debit_account_id,
        CASE
            WHEN t.type IN ('TOPUP', 'REFUND') THEN control.id
            ELSE COALESCE(
                (SELECT id FROM credit_accounts WHERE partner_id = t.partner_id LIMIT 1),
                operations.id
            )
        END
    ),
    credit_account_id = COALESCE(
        t.credit_account_id,
        CASE
            WHEN t.type IN ('TOPUP', 'REFUND') THEN COALESCE(
                (SELECT id FROM credit_accounts WHERE partner_id = t.partner_id LIMIT 1),
                operations.id
            )
            ELSE operations.id
        END
    ),
    debit_balance_after = CASE
        WHEN t.type IN ('TOPUP', 'REFUND') THEN t.debit_balance_after
        ELSE COALESCE(t.debit_balance_after, t.balance_after)
    END,
    credit_balance_after = CASE
        WHEN t.type IN ('TOPUP', 'REFUND') THEN COALESCE(t.credit_balance_after, t.balance_after)
        ELSE t.credit_balance_after
    END
FROM credit_accounts control,
     credit_accounts operations
WHERE control.account_code = 'ADRICORE_CONTROL'
  AND operations.account_code = 'ADRICORE_OPERATIONS'
  AND (t.debit_account_id IS NULL OR t.credit_account_id IS NULL);

ALTER TABLE credit_transactions
    ALTER COLUMN debit_account_id SET NOT NULL,
    ALTER COLUMN credit_account_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_credit_tx_distinct_accounts'
    ) THEN
        ALTER TABLE credit_transactions
            ADD CONSTRAINT chk_credit_tx_distinct_accounts
            CHECK (debit_account_id <> credit_account_id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_credit_account_owner'
    ) THEN
        ALTER TABLE credit_accounts
            ADD CONSTRAINT chk_credit_account_owner
            CHECK (
                (partner_id IS NOT NULL AND account_code IS NULL)
                OR (partner_id IS NULL AND account_code IS NOT NULL)
            );
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_credit_tx_debit_account'
    ) THEN
        ALTER TABLE credit_transactions
            ADD CONSTRAINT fk_credit_tx_debit_account
            FOREIGN KEY (debit_account_id) REFERENCES credit_accounts(id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_credit_tx_credit_account'
    ) THEN
        ALTER TABLE credit_transactions
            ADD CONSTRAINT fk_credit_tx_credit_account
            FOREIGN KEY (credit_account_id) REFERENCES credit_accounts(id);
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_credit_tx_debit_account ON credit_transactions(debit_account_id);
CREATE INDEX IF NOT EXISTS idx_credit_tx_credit_account ON credit_transactions(credit_account_id);

CREATE OR REPLACE FUNCTION create_credit_account_on_partner_insert()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO credit_accounts(partner_id, account_name, balance, reserved, currency, updated_at)
  VALUES (NEW.id, NEW.name, 0, 0, COALESCE(NEW.currency, 'UGX'), now())
  ON CONFLICT (partner_id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP FUNCTION IF EXISTS confirm_consume_reserved(uuid, numeric, text);

CREATE OR REPLACE FUNCTION confirm_consume_reserved(
    p_partner uuid,
    p_amount numeric,
    p_reference text,
    p_profit numeric DEFAULT 0
)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_partner_account uuid;
  v_operations uuid;
  v_income uuid;
  v_partner_balance numeric;
  v_operations_balance numeric;
  v_income_balance numeric;
  v_profit numeric;
BEGIN
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'consume amount must be positive';
  END IF;
  v_profit := COALESCE(p_profit, 0);

  UPDATE credit_accounts
  SET reserved = reserved - p_amount,
      updated_at = now()
  WHERE partner_id = p_partner
    AND reserved >= p_amount
  RETURNING id, balance INTO v_partner_account, v_partner_balance;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'insufficient reserved balance for partner %', p_partner;
  END IF;

  UPDATE credit_accounts
  SET balance = balance + p_amount,
      updated_at = now()
  WHERE account_code = 'ADRICORE_OPERATIONS'
  RETURNING id, balance INTO v_operations, v_operations_balance;

  INSERT INTO credit_transactions(
      partner_id, type, amount, balance_after, reference, created_at,
      debit_account_id, credit_account_id, debit_balance_after, credit_balance_after
  )
  VALUES (
      p_partner, 'CONSUMPTION', p_amount, v_partner_balance, p_reference, now(),
      v_partner_account, v_operations, v_partner_balance, v_operations_balance
  );

  IF v_profit > 0 THEN
    UPDATE credit_accounts
    SET balance = balance - v_profit,
        updated_at = now()
    WHERE account_code = 'ADRICORE_OPERATIONS'
      AND balance >= v_profit
    RETURNING id, balance INTO v_operations, v_operations_balance;

    IF NOT FOUND THEN
      RAISE EXCEPTION 'insufficient operations balance to recognise income';
    END IF;

    UPDATE credit_accounts
    SET balance = balance + v_profit,
        updated_at = now()
    WHERE account_code = 'ADRICORE_INCOME'
    RETURNING id, balance INTO v_income, v_income_balance;

    INSERT INTO credit_transactions(
        partner_id, type, amount, balance_after, reference, created_at,
        debit_account_id, credit_account_id, debit_balance_after, credit_balance_after
    )
    VALUES (
        p_partner, 'INCOME', v_profit, v_partner_balance, p_reference, now(),
        v_operations, v_income, v_operations_balance, v_income_balance
    );
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION release_reserved_credit(p_partner uuid, p_amount numeric, p_reason text)
RETURNS void LANGUAGE plpgsql AS $$
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
END;
$$;

CREATE OR REPLACE FUNCTION topup_credit(p_partner uuid, p_amount numeric, p_reference text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
  v_partner_account uuid;
  v_control uuid;
  v_partner_balance numeric;
  v_control_balance numeric;
BEGIN
  IF p_amount <= 0 THEN
    RAISE EXCEPTION 'topup amount must be positive';
  END IF;

  UPDATE credit_accounts
  SET balance = balance + p_amount,
      updated_at = now()
  WHERE partner_id = p_partner
  RETURNING id, balance INTO v_partner_account, v_partner_balance;

  IF NOT FOUND THEN
    INSERT INTO credit_accounts(partner_id, account_name, balance, reserved, currency, updated_at)
    VALUES (p_partner, 'Partner', p_amount, 0, 'UGX', now())
    RETURNING id, balance INTO v_partner_account, v_partner_balance;
  END IF;

  UPDATE credit_accounts
  SET balance = balance - p_amount,
      updated_at = now()
  WHERE account_code = 'ADRICORE_CONTROL'
  RETURNING id, balance INTO v_control, v_control_balance;

  INSERT INTO credit_transactions(
      partner_id, type, amount, balance_after, reference, created_at,
      debit_account_id, credit_account_id, debit_balance_after, credit_balance_after
  )
  VALUES (
      p_partner, 'TOPUP', p_amount, v_partner_balance, p_reference, now(),
      v_control, v_partner_account, v_control_balance, v_partner_balance
  );
END;
$$;
