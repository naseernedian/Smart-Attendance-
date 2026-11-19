-- PostgreSQL Relational Schema for SMART ATTENDANCE APP
-- GPS Koz Shinoli | EMIS Code: 61673

CREATE TABLE IF NOT EXISTS admins (
    id BIGSERIAL PRIMARY KEY,
    admin_uid VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    designation VARCHAR(120) NOT NULL,
    mobile_normalized VARCHAR(20) NOT NULL UNIQUE,
    cnic_normalized CHAR(13) NOT NULL UNIQUE,
    cnic_formatted CHAR(15) NOT NULL UNIQUE,
    email VARCHAR(180) NOT NULL UNIQUE,
    password_salt_b64 VARCHAR(64) NOT NULL,
    password_hash_b64 VARCHAR(128) NOT NULL,
    face_embedding_enc TEXT NOT NULL,
    face_thumbnail_b64 TEXT NOT NULL,
    cnic_front_doc_path VARCHAR(255) NOT NULL,
    cnic_back_doc_path VARCHAR(255) NOT NULL,
    employment_doc_path VARCHAR(255) NOT NULL,
    employment_doc_mime VARCHAR(80) NOT NULL,
    authorization_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED'
        CHECK (authorization_status IN ('PENDING_AUTHORIZATION', 'APPROVED', 'REJECTED')),
    authorized_by VARCHAR(150),
    authorized_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGSERIAL PRIMARY KEY,
    admin_uid VARCHAR(64) NOT NULL REFERENCES admins(admin_uid),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS students (
    id BIGSERIAL PRIMARY KEY,
    student_uid VARCHAR(64) NOT NULL UNIQUE,
    student_name VARCHAR(150) NOT NULL,
    father_name VARCHAR(150) NOT NULL,
    class_name VARCHAR(40) NOT NULL,
    roll_number VARCHAR(30) NOT NULL,
    qr_payload VARCHAR(255) NOT NULL UNIQUE,
    face_embedding_enc TEXT NOT NULL,
    face_thumbnail_b64 TEXT NOT NULL,
    registered_by_admin_uid VARCHAR(64) NOT NULL,
    created_at_epoch_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_student_class_roll UNIQUE (class_name, roll_number)
);

-- Strictly Append-Only Attendance Table
-- Intentionally omits ON DELETE CASCADE and stores immutable student detail snapshots
-- so attendance history remains preserved even if student records are archived/removed.
CREATE TABLE IF NOT EXISTS attendance_records (
    id BIGSERIAL PRIMARY KEY,
    local_uid VARCHAR(64) NOT NULL UNIQUE,
    student_uid VARCHAR(64) NOT NULL,
    student_name_snapshot VARCHAR(150) NOT NULL,
    father_name_snapshot VARCHAR(150) NOT NULL,
    class_name_snapshot VARCHAR(40) NOT NULL,
    roll_number_snapshot VARCHAR(30) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LEAVE')),
    attendance_date DATE NOT NULL,
    marked_at_iso VARCHAR(40) NOT NULL,
    marked_at_epoch_ms BIGINT NOT NULL,
    verified_similarity REAL NOT NULL,
    marked_by_admin_uid VARCHAR(64) NOT NULL,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_attendance_student_date UNIQUE (student_uid, attendance_date)
);

CREATE TABLE IF NOT EXISTS attendance_audit_log (
    id BIGSERIAL PRIMARY KEY,
    local_uid VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_admin_uid VARCHAR(64) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- PostgreSQL Append-Only Protection Trigger
CREATE OR REPLACE FUNCTION prevent_attendance_delete_or_tamper()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Append-only violation: attendance records cannot be permanently deleted.';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'Append-only violation: attendance records are immutable once recorded.';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_attendance_append_only ON attendance_records;
CREATE TRIGGER trg_attendance_append_only
BEFORE UPDATE OR DELETE ON attendance_records
FOR EACH ROW EXECUTE FUNCTION prevent_attendance_delete_or_tamper();

-- Grant only SELECT and INSERT on attendance_records to the application database role:
-- REVOKE UPDATE, DELETE, TRUNCATE ON attendance_records FROM smart_attendance_app_role;
