-- Make success/failure filterable without parsing JSONB on every list request.

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS success boolean;

UPDATE audit_logs
SET success = COALESCE((details ->> 'success')::boolean, true)
WHERE success IS NULL;

CREATE INDEX IF NOT EXISTS idx_audit_success_created ON audit_logs (success, created_at DESC);
