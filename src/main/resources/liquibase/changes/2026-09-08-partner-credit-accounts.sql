-- Guarantee every partner has a zero-balance credit account.

INSERT INTO credit_accounts (partner_id, balance, reserved, currency, updated_at)
SELECT p.id, 0, 0, COALESCE(NULLIF(p.currency, ''), 'UGX'), now()
FROM partners p
WHERE NOT EXISTS (
    SELECT 1 FROM credit_accounts c WHERE c.partner_id = p.id
);
