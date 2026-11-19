from datetime import datetime, timezone
from sqlalchemy import BigInteger, Column, DateTime, Float, ForeignKey, Integer, String, Text, UniqueConstraint
from .db import Base


def utcnow():
    return datetime.now(timezone.utc)


class Admin(Base):
    __tablename__ = "admins"

    id = Column(Integer, primary_key=True, autoincrement=True)
    admin_uid = Column(String(64), unique=True, nullable=False, index=True)
    full_name = Column(String(150), nullable=False)
    designation = Column(String(120), nullable=False)
    mobile_normalized = Column(String(20), unique=True, nullable=False, index=True)
    cnic_normalized = Column(String(13), unique=True, nullable=False, index=True)
    cnic_formatted = Column(String(15), unique=True, nullable=False)
    email = Column(String(180), unique=True, nullable=False, index=True)
    password_salt_b64 = Column(String(64), nullable=False)
    password_hash_b64 = Column(String(128), nullable=False)
    face_embedding_enc = Column(Text, nullable=False)
    face_thumbnail_b64 = Column(Text, nullable=False)
    cnic_front_doc_path = Column(String(255), nullable=False)
    cnic_back_doc_path = Column(String(255), nullable=False)
    employment_doc_path = Column(String(255), nullable=False)
    employment_doc_mime = Column(String(80), nullable=False)
    authorization_status = Column(String(32), nullable=False, default="APPROVED")
    authorized_by = Column(String(150), nullable=True)
    authorized_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utcnow)


class PasswordResetToken(Base):
    __tablename__ = "password_reset_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    admin_uid = Column(String(64), ForeignKey("admins.admin_uid"), nullable=False, index=True)
    token_hash = Column(String(64), unique=True, nullable=False, index=True)
    expires_at = Column(DateTime(timezone=True), nullable=False)
    used_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utcnow)


class Student(Base):
    __tablename__ = "students"
    __table_args__ = (
        UniqueConstraint("class_name", "roll_number", name="uq_student_class_roll"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    student_uid = Column(String(64), unique=True, nullable=False, index=True)
    student_name = Column(String(150), nullable=False)
    father_name = Column(String(150), nullable=False)
    class_name = Column(String(40), nullable=False)
    roll_number = Column(String(30), nullable=False)
    qr_payload = Column(String(255), unique=True, nullable=False)
    face_embedding_enc = Column(Text, nullable=False)
    face_thumbnail_b64 = Column(Text, nullable=False)
    registered_by_admin_uid = Column(String(64), nullable=False)
    created_at_epoch_ms = Column(BigInteger, nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, default=utcnow)


class AttendanceRecord(Base):
    __tablename__ = "attendance_records"
    __table_args__ = (
        UniqueConstraint("student_uid", "attendance_date", name="uq_attendance_student_date"),
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    local_uid = Column(String(64), unique=True, nullable=False, index=True)
    student_uid = Column(String(64), nullable=False, index=True)
    student_name_snapshot = Column(String(150), nullable=False)
    father_name_snapshot = Column(String(150), nullable=False)
    class_name_snapshot = Column(String(40), nullable=False)
    roll_number_snapshot = Column(String(30), nullable=False)
    status = Column(String(16), nullable=False)
    attendance_date = Column(String(10), nullable=False, index=True)
    marked_at_iso = Column(String(40), nullable=False)
    marked_at_epoch_ms = Column(BigInteger, nullable=False)
    verified_similarity = Column(Float, nullable=False)
    marked_by_admin_uid = Column(String(64), nullable=False)
    synced_at = Column(DateTime(timezone=True), nullable=False, default=utcnow)


class AttendanceAuditLog(Base):
    __tablename__ = "attendance_audit_log"

    id = Column(Integer, primary_key=True, autoincrement=True)
    local_uid = Column(String(64), nullable=False)
    event_type = Column(String(32), nullable=False)
    actor_admin_uid = Column(String(64), nullable=False)
    recorded_at = Column(DateTime(timezone=True), nullable=False, default=utcnow)
