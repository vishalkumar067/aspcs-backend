-- ============================================================
-- ASPCS School ERP - Database Migration V1
-- Core Tables: Users, Students, Teachers, Parents
-- ============================================================

-- ─── Admin Users ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    email        VARCHAR(100) NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'EDITOR',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Academic Sessions ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS academic_sessions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(20)  NOT NULL UNIQUE, -- e.g. 2025-26
    start_date   DATE         NOT NULL,
    end_date     DATE         NOT NULL,
    is_current   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Classes ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS classes (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(20)  NOT NULL, -- e.g. Class X
    section      VARCHAR(5),            -- A, B, C
    session_id   UUID REFERENCES academic_sessions(id),
    class_teacher_id UUID,              -- FK to teachers added later
    capacity     INT          NOT NULL DEFAULT 40,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(name, section, session_id)
);

-- ─── Subjects ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS subjects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(100) NOT NULL,
    code         VARCHAR(20)  NOT NULL UNIQUE,
    max_marks    INT          NOT NULL DEFAULT 100,
    pass_marks   INT          NOT NULL DEFAULT 33,
    is_practical BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Class Subjects ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS class_subjects (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id     UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    subject_id   UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    UNIQUE(class_id, subject_id)
);

-- ─── Teachers ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS teachers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     VARCHAR(20)  NOT NULL UNIQUE,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(100) UNIQUE,
    phone           VARCHAR(15)  NOT NULL,
    date_of_birth   DATE,
    gender          VARCHAR(10),
    qualification   VARCHAR(200),
    designation     VARCHAR(50),   -- PGT, TGT, PRT
    department      VARCHAR(50),
    joining_date    DATE,
    address         TEXT,
    photo_url       VARCHAR(500),
    username        VARCHAR(50)  UNIQUE,
    password        VARCHAR(255),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Teacher Assignments ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS teacher_assignments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    teacher_id   UUID NOT NULL REFERENCES teachers(id) ON DELETE CASCADE,
    class_id     UUID NOT NULL REFERENCES classes(id)  ON DELETE CASCADE,
    subject_id   UUID NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    session_id   UUID REFERENCES academic_sessions(id),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(teacher_id, class_id, subject_id, session_id)
);

-- Add FK for class teacher after teachers table created
ALTER TABLE classes ADD CONSTRAINT fk_class_teacher
    FOREIGN KEY (class_teacher_id) REFERENCES teachers(id) ON DELETE SET NULL;

-- ─── Students ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS students (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_no     VARCHAR(30)  NOT NULL UNIQUE,
    roll_no          VARCHAR(10),
    full_name        VARCHAR(100) NOT NULL,
    date_of_birth    DATE,
    gender           VARCHAR(10),
    religion         VARCHAR(30),
    category         VARCHAR(10),  -- GEN, OBC, SC, ST
    aadhar_no        VARCHAR(20)   UNIQUE,
    current_class    VARCHAR(20)   NOT NULL,
    section          VARCHAR(5),
    admission_date   DATE,
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    father_name      VARCHAR(100),
    mother_name      VARCHAR(100),
    guardian_phone   VARCHAR(15)   NOT NULL,
    guardian_email   VARCHAR(100),
    address          TEXT,
    city             VARCHAR(50),
    state            VARCHAR(50),
    pincode          VARCHAR(10),
    previous_school  VARCHAR(200),
    previous_class   VARCHAR(20),
    photo_url        VARCHAR(500),
    username         VARCHAR(50)   UNIQUE,
    password         VARCHAR(255),
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- ─── Student Enrollments ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS student_enrollments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id   UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    class_id     UUID NOT NULL REFERENCES classes(id)  ON DELETE CASCADE,
    session_id   UUID NOT NULL REFERENCES academic_sessions(id),
    roll_no      VARCHAR(10),
    enrolled_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(student_id, session_id)
);

-- ─── Parents ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS parents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name    VARCHAR(100) NOT NULL,
    email        VARCHAR(100) UNIQUE,
    phone        VARCHAR(15)  NOT NULL UNIQUE,
    relation     VARCHAR(20),  -- FATHER, MOTHER, GUARDIAN
    occupation   VARCHAR(100),
    address      TEXT,
    username     VARCHAR(50)  UNIQUE,
    password     VARCHAR(255),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Parent-Student Mapping ───────────────────────────────────
CREATE TABLE IF NOT EXISTS parent_students (
    parent_id    UUID NOT NULL REFERENCES parents(id)  ON DELETE CASCADE,
    student_id   UUID NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    PRIMARY KEY(parent_id, student_id)
);

-- ─── Indexes ─────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_students_admission_no ON students(admission_no);
CREATE INDEX IF NOT EXISTS idx_students_current_class ON students(current_class);
CREATE INDEX IF NOT EXISTS idx_students_status ON students(status);
CREATE INDEX IF NOT EXISTS idx_teachers_employee_id ON teachers(employee_id);
CREATE INDEX IF NOT EXISTS idx_teachers_department ON teachers(department);
