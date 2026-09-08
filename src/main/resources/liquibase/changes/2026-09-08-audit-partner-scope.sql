-- Scope audit events to a partner so operators only see their own users' activity.

ALTER TABLE audit_logs
    ADD COLUMN IF NOT EXISTS partner_id uuid REFERENCES partners(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_audit_partner_created ON audit_logs (partner_id, created_at DESC);

UPDATE audit_logs
SET partner_id = NULLIF(details ->> 'actorPartnerId', '')::uuid
WHERE partner_id IS NULL
  AND details ->> 'actorPartnerId' ~* '^[0-9a-f-]{36}$';

UPDATE audit_logs a
SET partner_id = u.partner_id
FROM public.system_user u
WHERE a.partner_id IS NULL
  AND u.partner_id IS NOT NULL
  AND a.actor_id = u.id::text;
