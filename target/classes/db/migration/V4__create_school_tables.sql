-- ============================================================
-- ASPCS School ERP - Database Migration V4
-- Communication, Documents, Careers Tables
-- ============================================================

-- ─── Notices ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notices (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(300) NOT NULL,
    description  TEXT,
    category     VARCHAR(20)  NOT NULL DEFAULT 'GENERAL',
    pdf_url      VARCHAR(500),
    image_url    VARCHAR(500),
    important    BOOLEAN      NOT NULL DEFAULT FALSE,
    published    BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP,
    expires_at   TIMESTAMP,
    created_by   UUID REFERENCES admin_users(id),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Messages (Parent ↔ Teacher) ─────────────────────────────
CREATE TABLE IF NOT EXISTS messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_type    VARCHAR(10)  NOT NULL, -- PARENT, TEACHER, ADMIN
    from_id      UUID         NOT NULL,
    to_type      VARCHAR(10)  NOT NULL,
    to_id        UUID         NOT NULL,
    subject      VARCHAR(200),
    content      TEXT         NOT NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at      TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Announcements ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS announcements (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    target       VARCHAR(20)  NOT NULL DEFAULT 'ALL', -- ALL, STUDENTS, PARENTS, TEACHERS
    class_name   VARCHAR(20), -- NULL means all classes
    sent_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    sent_by      UUID REFERENCES admin_users(id)
);

-- ─── TC Requests ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tc_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admission_no     VARCHAR(30)  NOT NULL,
    student_name     VARCHAR(100) NOT NULL,
    class_studying   VARCHAR(30),
    reason           TEXT,
    applicant_name   VARCHAR(100) NOT NULL,
    applicant_phone  VARCHAR(15)  NOT NULL,
    applicant_email  VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    tc_number        VARCHAR(30)  UNIQUE,
    issue_date       DATE,
    admin_remarks    TEXT,
    requested_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Gallery Albums ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gallery_albums (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    category      VARCHAR(30)  NOT NULL DEFAULT 'EVENTS',
    cover_image_url VARCHAR(500),
    published     BOOLEAN      NOT NULL DEFAULT FALSE,
    event_date    DATE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Gallery Images ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS gallery_images (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    album_id      UUID NOT NULL REFERENCES gallery_albums(id) ON DELETE CASCADE,
    url           VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    public_id     VARCHAR(200),
    caption       VARCHAR(300),
    alt           VARCHAR(200),
    width         INT,
    height        INT,
    display_order INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ─── Admission Inquiries ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS admission_inquiries (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_name     VARCHAR(100) NOT NULL,
    date_of_birth    DATE,
    grade_applying   VARCHAR(20)  NOT NULL,
    parent_name      VARCHAR(100) NOT NULL,
    parent_email     VARCHAR(100) NOT NULL,
    parent_phone     VARCHAR(15)  NOT NULL,
    address          TEXT,
    previous_school  VARCHAR(200),
    message          TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    admin_notes      TEXT,
    submitted_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Events ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    start_date   DATE         NOT NULL,
    end_date     DATE,
    venue        VARCHAR(200),
    image_url    VARCHAR(500),
    is_highlight BOOLEAN      NOT NULL DEFAULT FALSE,
    category     VARCHAR(20)  NOT NULL DEFAULT 'ACADEMIC',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Job Listings ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS job_listings (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    department       VARCHAR(100) NOT NULL,
    type             VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME',
    description      TEXT         NOT NULL,
    requirements     TEXT,
    responsibilities TEXT,
    experience       VARCHAR(50),
    qualification    VARCHAR(200),
    salary           VARCHAR(100),
    last_date        DATE,
    vacancies        INT          NOT NULL DEFAULT 1,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Job Applications ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS job_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES job_listings(id) ON DELETE CASCADE,
    job_title       VARCHAR(200),
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(15),
    qualification   VARCHAR(200),
    experience      VARCHAR(100),
    cover_letter    TEXT,
    resume_url      VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'APPLIED',
    admin_notes     TEXT,
    applied_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ─── Indexes ──────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_notices_published  ON notices(published);
CREATE INDEX IF NOT EXISTS idx_notices_category   ON notices(category);
CREATE INDEX IF NOT EXISTS idx_tc_admission_no    ON tc_requests(admission_no);
CREATE INDEX IF NOT EXISTS idx_tc_status          ON tc_requests(status);
CREATE INDEX IF NOT EXISTS idx_gallery_album      ON gallery_images(album_id);
CREATE INDEX IF NOT EXISTS idx_admissions_status  ON admission_inquiries(status);
CREATE INDEX IF NOT EXISTS idx_jobs_active        ON job_listings(active);
