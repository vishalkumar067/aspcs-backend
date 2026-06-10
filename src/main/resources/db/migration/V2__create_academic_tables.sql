-- ============================================================
-- ASPCS School ERP - Database Migration V2
-- Academic Tables: Attendance, Exams, Results, Report Cards
-- ============================================================

-- ─── Attendance ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS attendance (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_id     UUID NOT NULL REFERENCES classes(id)  ON DELETE CASCADE,
    date         DATE NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'PRESENT', -- PRESENT, ABSENT, LATE, HOLIDAY
    remarks      VARCHAR(200),
    marked_by    UUID REFERENCES teachers(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, date)
);

-- ─── Exam Types ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS exam_types (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50)  NOT NULL UNIQUE, -- Unit Test 1, Half Yearly, Annual
    short_name   VARCHAR(10)  NOT NULL,         -- UT1, HY, ANN
    weightage    DECIMAL(5,2) NOT NULL DEFAULT 100, -- % weightage
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Exams ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS exams (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_type_id UUID NOT NULL REFERENCES exam_types(id),
    class_id     UUID NOT NULL REFERENCES classes(id),
    subject_id   UUID NOT NULL REFERENCES subjects(id),
    session_id   UUID NOT NULL REFERENCES academic_sessions(id),
    exam_date    DATE,
    max_marks    INT  NOT NULL DEFAULT 100,
    pass_marks   INT  NOT NULL DEFAULT 33,
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(exam_type_id, class_id, subject_id, session_id)
);

-- ─── Exam Results ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS exam_results (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id      UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id   UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    marks        DECIMAL(6,2),
    grade        VARCHAR(5),   -- A+, A, B+, B, C, D, F
    is_absent    BOOLEAN NOT NULL DEFAULT FALSE,
    remarks      VARCHAR(200),
    entered_by   UUID REFERENCES teachers(id),
    entered_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(exam_id, student_id)
);

-- ─── Report Cards ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS report_cards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_id        UUID NOT NULL REFERENCES classes(id),
    session_id      UUID NOT NULL REFERENCES academic_sessions(id),
    exam_type_id    UUID NOT NULL REFERENCES exam_types(id),
    total_marks     DECIMAL(7,2),
    marks_obtained  DECIMAL(7,2),
    percentage      DECIMAL(5,2),
    grade           VARCHAR(5),
    rank            INT,
    attendance_pct  DECIMAL(5,2),
    teacher_remarks TEXT,
    principal_remarks TEXT,
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, session_id, exam_type_id)
);

-- ─── Homework / Assignments ───────────────────────────────────
CREATE TABLE IF NOT EXISTS assignments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id     UUID NOT NULL REFERENCES classes(id),
    subject_id   UUID NOT NULL REFERENCES subjects(id),
    teacher_id   UUID NOT NULL REFERENCES teachers(id),
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    due_date     DATE,
    max_marks    INT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── Timetable ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS timetable (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id            UUID NOT NULL REFERENCES classes(id),
    subject_id          UUID NOT NULL REFERENCES subjects(id),
    teacher_id          UUID NOT NULL REFERENCES teachers(id),
    day_of_week         VARCHAR(10) NOT NULL, -- MONDAY, TUESDAY...
    period_no           INT  NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,
    session_id          UUID REFERENCES academic_sessions(id),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(class_id, day_of_week, period_no, session_id)
);

-- ─── Indexes ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_attendance_student ON attendance(student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date    ON attendance(date);
CREATE INDEX IF NOT EXISTS idx_attendance_class   ON attendance(class_id);
CREATE INDEX IF NOT EXISTS idx_results_exam       ON exam_results(exam_id);
CREATE INDEX IF NOT EXISTS idx_results_student    ON exam_results(student_id);
CREATE INDEX IF NOT EXISTS idx_report_student     ON report_cards(student_id);

-- ─── Default Exam Types ───────────────────────────────────────
INSERT INTO exam_types (name, short_name, weightage) VALUES
    ('Unit Test 1',     'UT1',  10),
    ('Unit Test 2',     'UT2',  10),
    ('Half Yearly',     'HY',   30),
    ('Annual',          'ANN',  50)
ON CONFLICT (name) DO NOTHING;

-- ─── Default Subjects ─────────────────────────────────────────
INSERT INTO subjects (name, code, max_marks, pass_marks) VALUES
    ('English',          'ENG',  100, 33),
    ('Hindi',            'HIN',  100, 33),
    ('Mathematics',      'MAT',  100, 33),
    ('Science',          'SCI',  100, 33),
    ('Social Science',   'SST',  100, 33),
    ('Sanskrit',         'SAN',  100, 33),
    ('Computer Science', 'CS',   100, 33),
    ('Physical Education','PE',  100, 33)
ON CONFLICT (code) DO NOTHING;
