-- V7: Add date_of_birth to tc_requests for public TC verification
ALTER TABLE tc_requests
    ADD COLUMN IF NOT EXISTS date_of_birth DATE;
