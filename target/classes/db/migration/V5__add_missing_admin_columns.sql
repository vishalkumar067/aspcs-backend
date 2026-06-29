-- V5: Add missing columns to admin_users table
-- The AdminUser entity was updated to include updated_at and changed role to enum string

ALTER TABLE admin_users
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Backfill updated_at for existing rows
UPDATE admin_users SET updated_at = created_at WHERE updated_at IS NULL;

-- If role column exists as VARCHAR but needs to match enum values, ensure valid values
-- SUPER_ADMIN, ADMIN, EDITOR are the valid Role enum values
UPDATE admin_users
SET role = 'ADMIN'
WHERE role NOT IN ('SUPER_ADMIN', 'ADMIN', 'EDITOR');
