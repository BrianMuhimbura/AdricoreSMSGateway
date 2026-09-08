-- Seed the default administrator used by the SMS gateway console.
-- Password is BCrypt-hashed with the same encoder as UsersService#create (BCryptPasswordEncoder).
-- Plaintext password: admin@123

INSERT INTO public.system_role (role_name, role_code, created_at, domain)
SELECT 'Administrator', 'ADMINISTRATOR', now(), 'BACK_OFFICE'
WHERE NOT EXISTS (
    SELECT 1 FROM public.system_role WHERE role_code = 'ADMINISTRATOR'
);

INSERT INTO public.system_user (
    first_name,
    last_name,
    password,
    email,
    username,
    role_code,
    created_at,
    is_active
)
SELECT
    'System',
    'Admin',
    '$2a$10$FNw9Qn20tegSAqmd5X6m4OFqj7bslYFhDHI1jPEMO/yACUA3z5Xj.',
    'admin@adricore.com',
    'admin',
    'ADMINISTRATOR',
    now(),
    true
WHERE NOT EXISTS (
    SELECT 1 FROM public.system_user WHERE username = 'admin'
);
