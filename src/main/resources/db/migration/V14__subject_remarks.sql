-- V14: Add per-subject remarks/feedback to assessment_subjects.
-- Teachers can now write individual feedback for each subject
-- (e.g. "Needs to practice multiplication tables" for Maths,
-- "Excellent essay writing skills" for English).
ALTER TABLE assessment_subjects ADD COLUMN IF NOT EXISTS remarks TEXT;
