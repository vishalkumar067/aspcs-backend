-- V6: Seed default SUPER_ADMIN user
-- Email:    admin@aspcspatna.ac.in
-- Password: ASPCS@Admin2025
-- CHANGE THIS PASSWORD after first login!

INSERT INTO admin_users (id, name, email, password, role, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'Super Admin',
    'admin@aspcspatna.ac.in',
    '$2b$10$SAqkoJp5GgFpB6UgStpyJO8fBO0YdGzyhum2fIMfmX3Tp20U0hzZK',
    'SUPER_ADMIN',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
