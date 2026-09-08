-- Keep Sasula receipt numbers unique on top-up transactions so duplicate posts are rejected.

CREATE UNIQUE INDEX IF NOT EXISTS idx_credit_tx_topup_reference
    ON credit_transactions (reference)
    WHERE type = 'TOPUP'
      AND reference IS NOT NULL
      AND btrim(reference) <> '';
