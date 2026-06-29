-- ============================================================
-- ASPCS School ERP - Database Migration V12
-- Backfill: provision admin_users login rows for teachers that
-- already existed before teacher login was introduced (V11).
-- Skips any teacher whose email collides with an existing
-- admin_users row, falling back to a synthetic staff email.
-- Default password hash below = bcrypt("ChangeMe@2026") — every
-- backfilled teacher MUST be told to change this on first login.
-- ============================================================

INSERT INTO admin_users (id, name, email, password, role, teacher_id, created_at, updated_at)
SELECT
    gen_random_uuid(),
    t.full_name,
    CASE
        WHEN t.email IS NOT NULL
             AND t.email <> ''
             AND NOT EXISTS (SELECT 1 FROM admin_users au WHERE au.email = t.email)
        THEN t.email
        ELSE LOWER(t.employee_id) || '@staff.aspcspatna.ac.in'
    END,
    '$2b$10$VnA4QHs4r6izOUlMfE2Mq.EHCpoH3HhOeShos7uSBDdz7XchhGvLi', -- bcrypt: ChangeMe@2026 (verified)
    'TEACHER',
    t.id,
    NOW(),
    NOW()
FROM teachers t
WHERE NOT EXISTS (SELECT 1 FROM admin_users au WHERE au.teacher_id = t.id)
ON CONFLICT (email) DO NOTHING;
