-- Track mobile-money debit requests raised against billing invoices.

CREATE TABLE IF NOT EXISTS payment_requests (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id                  UUID NOT NULL REFERENCES billing_invoices(id) ON DELETE CASCADE,
    partner_id                  UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    amount                      NUMERIC(18,6) NOT NULL,
    customer_phone              VARCHAR(32) NOT NULL,
    external_transaction_id     VARCHAR(80) NOT NULL,
    status                      VARCHAR(30) NOT NULL,
    sasula_request_id           BIGINT,
    mm_transaction_id           VARCHAR(120),
    processing_number           VARCHAR(120),
    return_code                 INTEGER,
    return_message              TEXT,
    requested_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at                TIMESTAMPTZ,
    last_checked_at             TIMESTAMPTZ,
    credited_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_requests_ext_tx UNIQUE (external_transaction_id),
    CONSTRAINT chk_payment_requests_status CHECK (
        status IN ('PENDING', 'REQUESTED', 'SUCCESSFUL', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_payment_requests_invoice
    ON payment_requests (invoice_id);

CREATE INDEX IF NOT EXISTS idx_payment_requests_partner
    ON payment_requests (partner_id);

CREATE INDEX IF NOT EXISTS idx_payment_requests_status
    ON payment_requests (status);
