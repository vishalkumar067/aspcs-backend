-- ============================================================
-- ASPCS School ERP - Database Migration V13
-- Student Bulk Import: audit log for CSV/XLSX student imports.
-- Any authenticated user (including TEACHER) can run an import,
-- so every run and every row-level change is logged for review.
-- ============================================================

CREATE TABLE IF NOT EXISTS student_import_batches (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name       VARCHAR(255) NOT NULL,
    total_rows      INT NOT NULL DEFAULT 0,
    created_count   INT NOT NULL DEFAULT 0,
    updated_count   INT NOT NULL DEFAULT 0,
    failed_count    INT NOT NULL DEFAULT 0,
    imported_by     UUID REFERENCES admin_users(id),
    imported_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_import_batches_imported_by ON student_import_batches(imported_by);
CREATE INDEX IF NOT EXISTS idx_import_batches_imported_at ON student_import_batches(imported_at);

-- One row per spreadsheet row processed, regardless of outcome.
-- before_data/after_data let an admin see exactly what an overwrite
-- changed, since teachers are allowed to overwrite existing students.
CREATE TABLE IF NOT EXISTS student_import_rows (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id        UUID NOT NULL REFERENCES student_import_batches(id) ON DELETE CASCADE,
    row_number      INT NOT NULL,
    admission_no    VARCHAR(50),
    student_id      UUID REFERENCES students(id),
    outcome         VARCHAR(20) NOT NULL, -- CREATED, UPDATED, FAILED
    error_message   TEXT,
    before_data     JSONB,
    after_data      JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),

    CHECK (outcome IN ('CREATED', 'UPDATED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_import_rows_batch ON student_import_rows(batch_id);
CREATE INDEX IF NOT EXISTS idx_import_rows_student ON student_import_rows(student_id);
