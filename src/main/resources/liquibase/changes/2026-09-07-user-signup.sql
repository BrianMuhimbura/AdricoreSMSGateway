-- Link console users to a partner and require a password change after self-signup.

ALTER TABLE public.system_user
    ADD COLUMN IF NOT EXISTS partner_id uuid;

ALTER TABLE public.system_user
    ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'system_user_partner_id_fk'
    ) THEN
        ALTER TABLE public.system_user
            ADD CONSTRAINT system_user_partner_id_fk
            FOREIGN KEY (partner_id) REFERENCES public.partners (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS system_user_partner_id_idx ON public.system_user (partner_id);

INSERT INTO public.system_role (role_name, role_code, created_at, domain)
SELECT 'Partner Admin', 'PARTNER_ADMIN', now(), 'CLIENT_SIDE'
WHERE NOT EXISTS (
    SELECT 1 FROM public.system_role WHERE role_code = 'PARTNER_ADMIN'
);
