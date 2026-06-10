-- ============================================================
-- ASPCS School ERP - Database Migration V3
-- Fee Management Tables
-- ============================================================

-- ─── Fee Categories ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fee_categories (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL UNIQUE, -- Tuition, Transport, Lab, etc.
    description  TEXT,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── Fee Structures ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fee_structures (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_name      VARCHAR(20)    NOT NULL,
    category_id     UUID           NOT NULL REFERENCES fee_categories(id),
    session_id      UUID           NOT NULL REFERENCES academic_sessions(id),
    amount          DECIMAL(10,2)  NOT NULL,
    due_day         INT            NOT NULL DEFAULT 10, -- day of month
    frequency       VARCHAR(20)    NOT NULL DEFAULT 'MONTHLY', -- MONTHLY, QUARTERLY, ANNUAL
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    UNIQUE(class_name, category_id, session_id)
);

-- ─── Fee Payments ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fee_payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID           NOT NULL REFERENCES students(id),
    session_id      UUID           NOT NULL REFERENCES academic_sessions(id),
    category_id     UUID           NOT NULL REFERENCES fee_categories(id),
    receipt_no      VARCHAR(30)    NOT NULL UNIQUE,
    amount          DECIMAL(10,2)  NOT NULL,
    late_fine       DECIMAL(10,2)  NOT NULL DEFAULT 0,
    total_amount    DECIMAL(10,2)  NOT NULL,
    payment_date    DATE           NOT NULL,
    payment_method  VARCHAR(20)    NOT NULL DEFAULT 'CASH', -- CASH, ONLINE, CHEQUE, DD
    transaction_id  VARCHAR(100),
    month           VARCHAR(20),   -- April, May etc. (for monthly fees)
    remarks         VARCHAR(200),
    collected_by    UUID           REFERENCES admin_users(id),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ─── Fee Dues (outstanding) ───────────────────────────────────
CREATE TABLE IF NOT EXISTS fee_dues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID           NOT NULL REFERENCES students(id),
    session_id      UUID           NOT NULL REFERENCES academic_sessions(id),
    category_id     UUID           NOT NULL REFERENCES fee_categories(id),
    month           VARCHAR(20),
    due_amount      DECIMAL(10,2)  NOT NULL,
    due_date        DATE           NOT NULL,
    is_paid         BOOLEAN        NOT NULL DEFAULT FALSE,
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ─── Razorpay Orders ──────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID           NOT NULL REFERENCES students(id),
    razorpay_order_id VARCHAR(100) NOT NULL UNIQUE,
    amount          DECIMAL(10,2)  NOT NULL,
    currency        VARCHAR(5)     NOT NULL DEFAULT 'INR',
    status          VARCHAR(20)    NOT NULL DEFAULT 'CREATED', -- CREATED, PAID, FAILED
    payment_id      VARCHAR(100),  -- razorpay payment id after success
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW()
);

-- ─── Default Fee Categories ───────────────────────────────────
INSERT INTO fee_categories (name, description, is_mandatory) VALUES
    ('Tuition Fee',      'Monthly tuition charges',            TRUE),
    ('Transport Fee',    'School bus charges',                  FALSE),
    ('Lab Fee',          'Science & Computer lab charges',      FALSE),
    ('Sports Fee',       'Annual sports activities charges',    FALSE),
    ('Library Fee',      'Annual library membership',           FALSE),
    ('Examination Fee',  'Annual examination charges',          TRUE),
    ('Admission Fee',    'One-time admission processing fee',   TRUE),
    ('Annual Fund',      'Annual development fund',             TRUE)
ON CONFLICT (name) DO NOTHING;

-- ─── Indexes ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_payments_student   ON fee_payments(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_date      ON fee_payments(payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_receipt   ON fee_payments(receipt_no);
CREATE INDEX IF NOT EXISTS idx_dues_student       ON fee_dues(student_id);
CREATE INDEX IF NOT EXISTS idx_dues_unpaid        ON fee_dues(is_paid) WHERE is_paid = FALSE;
