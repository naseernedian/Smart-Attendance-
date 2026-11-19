import base64
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from typing import Dict, List, Optional
from fastapi import Depends, FastAPI, Header, HTTPException, status
from fastapi.middleware.gzip import GZipMiddleware
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .config import settings
from .db import Base, engine, get_db, install_append_only_triggers
from .email_service import send_password_reset_email
from .models import Admin, AttendanceAuditLog, AttendanceRecord, PasswordResetToken, Student
from .security import (
    cosine_similarity,
    create_jwt,
    decode_jwt,
    decrypt_embedding,
    encrypt_document_bytes,
    encrypt_embedding,
    generate_reset_token,
    hash_password,
    hash_reset_token,
    validate_cnic,
    validate_mobile,
    verify_password,
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    Base.metadata.create_all(bind=engine)
    install_append_only_triggers(engine)
    yield


app = FastAPI(
    title="GPS Koz Shinoli Smart Attendance API",
    version="1.0.0",
    lifespan=lifespan,
)
app.add_middleware(GZipMiddleware, minimum_size=512)


# --- Schemas ---
class AdminRegistrationRequest(BaseModel):
    admin_uid: str
    full_name: str = Field(min_length=3, max_length=150)
    designation: str = Field(min_length=2, max_length=120)
    mobile_normalized: str
    cnic_normalized: str
    cnic_formatted: str
    email: str
    password: str = Field(min_length=8)
    face_embedding: List[float] = Field(default_factory=list)
    face_thumbnail_b64: str = ""
    cnic_front_b64: str
    cnic_back_b64: str
    employment_doc_b64: str
    employment_doc_mime: str = "image/jpeg"


class AdminRegistrationResponse(BaseModel):
    admin_uid: str
    authorization_status: str
    message: str


class AdminLoginRequest(BaseModel):
    username: str
    password: str


class AdminProfileDto(BaseModel):
    admin_uid: str
    full_name: str
    designation: str
    mobile_normalized: str
    cnic_normalized: str
    cnic_formatted: str
    email: str
    authorization_status: str
    authorized_by: Optional[str] = None
    face_embedding: List[float]
    face_thumbnail_b64: str


class AdminLoginResponse(BaseModel):
    challenge_token: Optional[str] = None
    access_token: Optional[str] = None
    authorization_status: str
    message: str
    admin: Optional[AdminProfileDto] = None


class AdminFaceVerifyRequest(BaseModel):
    challenge_token: str
    live_face_embedding: List[float]


class AdminFaceVerifyResponse(BaseModel):
    verified: bool
    similarity: float
    access_token: Optional[str] = None
    message: str


class PasswordResetRequestDto(BaseModel):
    email: str


class PasswordResetResponseDto(BaseModel):
    message: str
    email_dispatched: bool


class PasswordResetConfirmDto(BaseModel):
    token: str
    new_password: str = Field(min_length=8)


class DirectPasswordResetRequestDto(BaseModel):
    username: str
    new_password: str = Field(min_length=8)


class StudentSyncItemDto(BaseModel):
    student_uid: str
    student_name: str
    father_name: str
    class_name: str
    roll_number: str
    qr_payload: str
    face_embedding: List[float]
    face_thumbnail_b64: str
    registered_by_admin_uid: str
    created_at_epoch_ms: int


class StudentBatchSyncRequest(BaseModel):
    students: List[StudentSyncItemDto]


class StudentBatchSyncResponse(BaseModel):
    synced_uids: List[str]
    duplicate_uids: List[str]
    rejected: Dict[str, str]


class AttendanceSyncItemDto(BaseModel):
    local_uid: str
    student_uid: str
    student_name_snapshot: str
    father_name_snapshot: str
    class_name_snapshot: str
    roll_number_snapshot: str
    status: str
    attendance_date: str
    marked_at_iso: str
    marked_at_epoch_ms: int
    verified_similarity: float
    marked_by_admin_uid: str


class AttendanceBatchSyncRequest(BaseModel):
    records: List[AttendanceSyncItemDto]


class SyncedAttendanceRefDto(BaseModel):
    local_uid: str
    server_id: str


class AttendanceBatchSyncResponse(BaseModel):
    synced: List[SyncedAttendanceRefDto]
    duplicate_preserved_uids: List[str]


def get_current_admin(
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db),
) -> Admin:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing bearer token.")
    token = authorization.split(" ", 1)[1].strip()
    admin_uid = decode_jwt(token, expected_type="access")
    if not admin_uid:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired access token.")
    admin = db.scalar(select(Admin).where(Admin.admin_uid == admin_uid))
    if not admin:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User account not found.")
    return admin


@app.get("/health")
def health():
    return {"status": "ok", "school": "GPS Koz Shinoli", "emis_code": "61673"}


@app.post("/api/v1/auth/register", response_model=AdminRegistrationResponse)
def register_admin(req: AdminRegistrationRequest, db: Session = Depends(get_db)):
    cnic_pair = validate_cnic(req.cnic_normalized) or validate_cnic(req.cnic_formatted)
    if not cnic_pair:
        raise HTTPException(status_code=400, detail="Invalid Pakistani CNIC (13 digits: XXXXXXXXXXXXX).")
    mobile_pair = validate_mobile(req.mobile_normalized)
    if not mobile_pair:
        raise HTTPException(status_code=400, detail="Invalid mobile number (03XXXXXXXXX).")

    salt_b64, hash_b64 = hash_password(req.password)
    enc_emb = encrypt_embedding(req.face_embedding) if req.face_embedding else ""

    front_path = settings.SECURE_UPLOAD_DIR / f"{req.admin_uid}_cnic_front.enc"
    back_path = settings.SECURE_UPLOAD_DIR / f"{req.admin_uid}_cnic_back.enc"
    emp_path = settings.SECURE_UPLOAD_DIR / f"{req.admin_uid}_employment.enc"

    try:
        front_path.write_bytes(encrypt_document_bytes(base64.b64decode(req.cnic_front_b64)))
        back_path.write_bytes(encrypt_document_bytes(base64.b64decode(req.cnic_back_b64)))
        emp_path.write_bytes(encrypt_document_bytes(base64.b64decode(req.employment_doc_b64)))
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Invalid verification document payload: {exc}")

    admin = Admin(
        admin_uid=req.admin_uid,
        full_name=req.full_name.strip(),
        designation=req.designation.strip(),
        mobile_normalized=mobile_pair[0],
        cnic_normalized=cnic_pair[0],
        cnic_formatted=cnic_pair[0],
        email=req.email.strip().lower(),
        password_salt_b64=salt_b64,
        password_hash_b64=hash_b64,
        face_embedding_enc=enc_emb,
        face_thumbnail_b64=req.face_thumbnail_b64,
        cnic_front_doc_path=str(front_path),
        cnic_back_doc_path=str(back_path),
        employment_doc_path=str(emp_path),
        employment_doc_mime=req.employment_doc_mime,
        authorization_status="APPROVED",
        authorized_by="OPEN_ACCESS",
        authorized_at=datetime.now(timezone.utc),
    )
    db.add(admin)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="User with this CNIC, mobile number, or email already exists.")

    return AdminRegistrationResponse(
        admin_uid=admin.admin_uid,
        authorization_status="APPROVED",
        message="Registration complete. You can sign in immediately.",
    )


@app.post("/api/v1/auth/login", response_model=AdminLoginResponse)
def login_admin(req: AdminLoginRequest, db: Session = Depends(get_db)):
    raw_user = req.username.strip()
    cnic_pair = validate_cnic(raw_user)
    mobile_pair = validate_mobile(raw_user)
    if not cnic_pair and not mobile_pair:
        raise HTTPException(
            status_code=400,
            detail="Username must be a valid Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX).",
        )

    if cnic_pair:
        admin = db.scalar(select(Admin).where(Admin.cnic_normalized == cnic_pair[0]))
    else:
        admin = db.scalar(select(Admin).where(Admin.mobile_normalized == mobile_pair[0]))

    if not admin or not verify_password(req.password, admin.password_salt_b64, admin.password_hash_b64):
        raise HTTPException(status_code=401, detail="Invalid username or password.")

    emb = decrypt_embedding(admin.face_embedding_enc) if admin.face_embedding_enc else []
    profile = AdminProfileDto(
        admin_uid=admin.admin_uid,
        full_name=admin.full_name,
        designation=admin.designation,
        mobile_normalized=admin.mobile_normalized,
        cnic_normalized=admin.cnic_normalized,
        cnic_formatted=admin.cnic_formatted,
        email=admin.email,
        authorization_status="APPROVED",
        authorized_by="OPEN_ACCESS",
        face_embedding=emb,
        face_thumbnail_b64=admin.face_thumbnail_b64 or "",
    )
    if not emb:
        access_jwt = create_jwt(admin.admin_uid, token_type="access", ttl_minutes=settings.ACCESS_TOKEN_TTL_HOURS * 60)
        return AdminLoginResponse(
            challenge_token=None,
            access_token=access_jwt,
            authorization_status="APPROVED",
            message="Signed in successfully.",
            admin=profile,
        )

    challenge = create_jwt(admin.admin_uid, token_type="face_challenge", ttl_minutes=settings.CHALLENGE_TTL_MINUTES)
    return AdminLoginResponse(
        challenge_token=challenge,
        access_token=None,
        authorization_status="APPROVED",
        message="Credentials verified. Proceed to live facial verification.",
        admin=profile,
    )


@app.post("/api/v1/auth/verify-face", response_model=AdminFaceVerifyResponse)
def verify_admin_face(req: AdminFaceVerifyRequest, db: Session = Depends(get_db)):
    admin_uid = decode_jwt(req.challenge_token, expected_type="face_challenge")
    if not admin_uid:
        raise HTTPException(status_code=401, detail="Expired or invalid facial challenge token.")
    admin = db.scalar(select(Admin).where(Admin.admin_uid == admin_uid))
    if not admin:
        raise HTTPException(status_code=404, detail="User account not found.")

    stored_emb = decrypt_embedding(admin.face_embedding_enc)
    sim = cosine_similarity(stored_emb, req.live_face_embedding)
    threshold = 0.78 if len(stored_emb) == 192 else 0.86
    if sim < threshold:
        return AdminFaceVerifyResponse(
            verified=False,
            similarity=sim,
            access_token=None,
            message=f"Live facial verification failed ({int(sim * 100)}% < {int(threshold * 100)}% required).",
        )

    access_jwt = create_jwt(admin.admin_uid, token_type="access", ttl_minutes=settings.ACCESS_TOKEN_TTL_HOURS * 60)
    return AdminFaceVerifyResponse(
        verified=True,
        similarity=sim,
        access_token=access_jwt,
        message="Live facial verification succeeded.",
    )


@app.post("/api/v1/auth/password-reset/request", response_model=PasswordResetResponseDto)
def request_password_reset(req: PasswordResetRequestDto, db: Session = Depends(get_db)):
    admin = db.scalar(select(Admin).where(Admin.email == req.email.strip().lower()))
    if not admin:
        raise HTTPException(status_code=404, detail="No registered admin account found for that email address.")

    raw_token, token_hash = generate_reset_token()
    expires_at = datetime.now(timezone.utc) + timedelta(minutes=settings.RESET_TOKEN_TTL_MINUTES)
    token_row = PasswordResetToken(
        admin_uid=admin.admin_uid,
        token_hash=token_hash,
        expires_at=expires_at,
    )
    db.add(token_row)
    db.commit()

    dispatched, message = send_password_reset_email(admin.email, admin.full_name, raw_token)
    if not dispatched:
        raise HTTPException(status_code=503, detail=message)

    return PasswordResetResponseDto(message=message, email_dispatched=True)


@app.post("/api/v1/auth/password-reset/confirm", response_model=AdminRegistrationResponse)
def confirm_password_reset(req: PasswordResetConfirmDto, db: Session = Depends(get_db)):
    token_hash = hash_reset_token(req.token)
    token_row = db.scalar(select(PasswordResetToken).where(PasswordResetToken.token_hash == token_hash))
    if not token_row:
        raise HTTPException(status_code=400, detail="Invalid password reset link or token.")
    if token_row.used_at is not None:
        raise HTTPException(status_code=400, detail="This password reset link has already been used.")
    expires_at = token_row.expires_at
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if datetime.now(timezone.utc) > expires_at:
        raise HTTPException(status_code=400, detail="This password reset link has expired.")

    admin = db.scalar(select(Admin).where(Admin.admin_uid == token_row.admin_uid))
    if not admin:
        raise HTTPException(status_code=404, detail="Account owner not found.")

    salt_b64, hash_b64 = hash_password(req.new_password)
    admin.password_salt_b64 = salt_b64
    admin.password_hash_b64 = hash_b64
    token_row.used_at = datetime.now(timezone.utc)
    db.commit()

    return AdminRegistrationResponse(
        admin_uid=admin.admin_uid,
        authorization_status=admin.authorization_status,
        message="Password updated successfully. You may now sign in.",
    )


@app.post("/api/v1/auth/password-reset/direct", response_model=AdminRegistrationResponse)
def reset_password_direct(req: DirectPasswordResetRequestDto, db: Session = Depends(get_db)):
    raw_user = req.username.strip()
    cnic_pair = validate_cnic(raw_user)
    mobile_pair = validate_mobile(raw_user)
    if not cnic_pair and not mobile_pair:
        raise HTTPException(
            status_code=400,
            detail="Username must be a valid Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX).",
        )

    if cnic_pair:
        admin = db.scalar(select(Admin).where(Admin.cnic_normalized == cnic_pair[0]))
    else:
        admin = db.scalar(select(Admin).where(Admin.mobile_normalized == mobile_pair[0]))

    if not admin:
        raise HTTPException(status_code=404, detail="No registered account found for that Mobile or CNIC.")

    salt_b64, hash_b64 = hash_password(req.new_password)
    admin.password_salt_b64 = salt_b64
    admin.password_hash_b64 = hash_b64
    db.commit()

    return AdminRegistrationResponse(
        admin_uid=admin.admin_uid,
        authorization_status=admin.authorization_status,
        message="Password reset successfully. You may now sign in.",
    )


@app.post("/api/v1/sync/students", response_model=StudentBatchSyncResponse)
def sync_students(
    req: StudentBatchSyncRequest,
    _: Admin = Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    synced_uids: List[str] = []
    duplicate_uids: List[str] = []
    rejected: Dict[str, str] = {}

    for item in req.students:
        existing_uid = db.scalar(select(Student).where(Student.student_uid == item.student_uid))
        if existing_uid:
            duplicate_uids.append(item.student_uid)
            continue
        existing_roll = db.scalar(
            select(Student).where(
                Student.class_name == item.class_name,
                Student.roll_number == item.roll_number,
            )
        )
        if existing_roll:
            rejected[item.student_uid] = f"Duplicate Class/Roll ({item.class_name} #{item.roll_number})"
            continue

        student = Student(
            student_uid=item.student_uid,
            student_name=item.student_name.strip(),
            father_name=item.father_name.strip(),
            class_name=item.class_name.strip(),
            roll_number=item.roll_number.strip(),
            qr_payload=item.qr_payload,
            face_embedding_enc=encrypt_embedding(item.face_embedding),
            face_thumbnail_b64=item.face_thumbnail_b64,
            registered_by_admin_uid=item.registered_by_admin_uid,
            created_at_epoch_ms=item.created_at_epoch_ms,
        )
        db.add(student)
        try:
            db.commit()
            synced_uids.append(item.student_uid)
        except IntegrityError as exc:
            db.rollback()
            rejected[item.student_uid] = str(exc.orig)

    return StudentBatchSyncResponse(
        synced_uids=synced_uids,
        duplicate_uids=duplicate_uids,
        rejected=rejected,
    )


@app.get("/api/v1/sync/students", response_model=List[StudentSyncItemDto])
def fetch_students(
    _: Admin = Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    rows = db.scalars(select(Student)).all()
    return [
        StudentSyncItemDto(
            student_uid=s.student_uid,
            student_name=s.student_name,
            father_name=s.father_name,
            class_name=s.class_name,
            roll_number=s.roll_number,
            qr_payload=s.qr_payload,
            face_embedding=decrypt_embedding(s.face_embedding_enc),
            face_thumbnail_b64=s.face_thumbnail_b64,
            registered_by_admin_uid=s.registered_by_admin_uid,
            created_at_epoch_ms=s.created_at_epoch_ms,
        )
        for s in rows
    ]


@app.post("/api/v1/sync/attendance", response_model=AttendanceBatchSyncResponse)
def sync_attendance(
    req: AttendanceBatchSyncRequest,
    current_admin: Admin = Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    synced: List[SyncedAttendanceRefDto] = []
    duplicate_preserved_uids: List[str] = []

    for item in req.records:
        if item.status not in ("PRESENT", "ABSENT", "LEAVE"):
            continue
        existing = db.scalar(
            select(AttendanceRecord).where(
                (AttendanceRecord.local_uid == item.local_uid)
                | (
                    (AttendanceRecord.student_uid == item.student_uid)
                    & (AttendanceRecord.attendance_date == item.attendance_date)
                )
            )
        )
        if existing:
            # Duplicate prevented: preserve original attendance timestamp & status
            duplicate_preserved_uids.append(item.local_uid)
            continue

        row = AttendanceRecord(
            local_uid=item.local_uid,
            student_uid=item.student_uid,
            student_name_snapshot=item.student_name_snapshot,
            father_name_snapshot=item.father_name_snapshot,
            class_name_snapshot=item.class_name_snapshot,
            roll_number_snapshot=item.roll_number_snapshot,
            status=item.status,
            attendance_date=item.attendance_date,
            marked_at_iso=item.marked_at_iso,
            marked_at_epoch_ms=item.marked_at_epoch_ms,
            verified_similarity=item.verified_similarity,
            marked_by_admin_uid=item.marked_by_admin_uid,
        )
        db.add(row)
        try:
            db.commit()
            db.refresh(row)
            db.add(
                AttendanceAuditLog(
                    local_uid=row.local_uid,
                    event_type="APPEND_SYNC",
                    actor_admin_uid=current_admin.admin_uid,
                )
            )
            db.commit()
            synced.append(SyncedAttendanceRefDto(local_uid=item.local_uid, server_id=str(row.id)))
        except IntegrityError:
            db.rollback()
            duplicate_preserved_uids.append(item.local_uid)

    return AttendanceBatchSyncResponse(
        synced=synced,
        duplicate_preserved_uids=duplicate_preserved_uids,
    )


@app.get("/api/v1/sync/attendance", response_model=List[AttendanceSyncItemDto])
def fetch_attendance(
    _: Admin = Depends(get_current_admin),
    db: Session = Depends(get_db),
):
    rows = db.scalars(select(AttendanceRecord)).all()
    return [
        AttendanceSyncItemDto(
            local_uid=r.local_uid,
            student_uid=r.student_uid,
            student_name_snapshot=r.student_name_snapshot,
            father_name_snapshot=r.father_name_snapshot,
            class_name_snapshot=r.class_name_snapshot,
            roll_number_snapshot=r.roll_number_snapshot,
            status=r.status,
            attendance_date=r.attendance_date,
            marked_at_iso=r.marked_at_iso,
            marked_at_epoch_ms=r.marked_at_epoch_ms,
            verified_similarity=r.verified_similarity,
            marked_by_admin_uid=r.marked_by_admin_uid,
        )
        for r in rows
    ]
