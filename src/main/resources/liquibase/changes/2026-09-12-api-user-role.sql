-- Partner API users authenticate with JWT and may only call SMS send.

INSERT INTO public.system_role (role_name, role_code, created_at, domain)
SELECT 'API User', 'API_USER', now(), 'CLIENT_SIDE'
WHERE NOT EXISTS (
    SELECT 1 FROM public.system_role WHERE role_code = 'API_USER'
);
