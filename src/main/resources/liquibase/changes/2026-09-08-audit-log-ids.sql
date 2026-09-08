-- Actor and resource identifiers are not always UUIDs (system users use bigint).

DROP INDEX IF EXISTS idx_audit_actor_created;

ALTER TABLE audit_logs
    ALTER COLUMN actor_id TYPE text USING actor_id::text,
    ALTER COLUMN resource_id TYPE text USING resource_id::text;

CREATE INDEX IF NOT EXISTS idx_audit_actor_created ON audit_logs (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action_created ON audit_logs (action, created_at DESC);
