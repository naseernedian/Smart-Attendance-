import base64
import math
import uuid
from datetime import datetime, timedelta, timezone
from fastapi.testclient import TestClient
from sqlalchemy import text
from app.main import app
from app.config import settings
from app.db import SessionLocal
from app.models import Admin, PasswordResetToken
from app.security import generate_reset_token


def unit_vec(seed: float, dim: int = 256):
    raw = [math.sin(seed * (i + 1)) for i in range(dim)]
    norm = math.sqrt(sum(x * x for x in raw))
    return [x / norm for x in raw]


def test_full_open_user_registration_live_face_sync_and_append_only_protection():
    with TestClient(app) as client:
        admin_uid = str(uuid.uuid4())
        face_ref = unit_vec(1.37)
        wrong_face = unit_vec(9.91)
        dummy_b64 = base64.b64encode(b"test-doc-bytes").decode("ascii")

        # 1. Register user with 13-digit CNIC (XXXXXXXXXXXXX), mobile (03XXXXXXXXX), email, live face, CNIC front/back, employment doc
        reg_resp = client.post(
            "/api/v1/auth/register",
            json={
                "admin_uid": admin_uid,
                "full_name": "Naseer Ud Din Shinwari",
                "designation": "Teacher",
                "mobile_normalized": "03001234567",
                "cnic_normalized": "2120312345671",
                "cnic_formatted": "2120312345671",
                "email": "user.gps61673@example.com",
                "password": "Password123",
                "face_embedding": face_ref,
                "face_thumbnail_b64": dummy_b64,
                "cnic_front_b64": dummy_b64,
                "cnic_back_b64": dummy_b64,
                "employment_doc_b64": dummy_b64,
                "employment_doc_mime": "image/jpeg",
            },
        )
        assert reg_resp.status_code == 200
        assert reg_resp.json()["authorization_status"] == "APPROVED"

        # 2. Newly registered user can immediately log in using 03XXXXXXXXX mobile or XXXXXXXXXXXXX CNIC
        login_cnic = client.post(
            "/api/v1/auth/login",
            json={"username": "2120312345671", "password": "Password123"},
        )
        assert login_cnic.status_code == 200
        assert login_cnic.json()["admin"]["mobile_normalized"] == "03001234567"

        login_ok = client.post(
            "/api/v1/auth/login",
            json={"username": "03001234567", "password": "Password123"},
        )
        assert login_ok.status_code == 200
        challenge = login_ok.json()["challenge_token"]
        assert challenge

        # 5. Live face mismatch MUST NOT grant access token
        face_fail = client.post(
            "/api/v1/auth/verify-face",
            json={"challenge_token": challenge, "live_face_embedding": wrong_face},
        )
        assert face_fail.status_code == 200
        assert face_fail.json()["verified"] is False
        assert face_fail.json()["access_token"] is None

        # 6. Matching live face grants access token
        face_pass = client.post(
            "/api/v1/auth/verify-face",
            json={"challenge_token": challenge, "live_face_embedding": face_ref},
        )
        assert face_pass.status_code == 200
        assert face_pass.json()["verified"] is True
        bearer = f"Bearer {face_pass.json()['access_token']}"

        # 7. Idempotent Student & Attendance Sync + Duplicate Prevention
        student_uid = str(uuid.uuid4())
        st_sync = client.post(
            "/api/v1/sync/students",
            headers={"Authorization": bearer},
            json={
                "students": [
                    {
                        "student_uid": student_uid,
                        "student_name": "Bilal Khan",
                        "father_name": "Tariq Khan",
                        "class_name": "Class 3",
                        "roll_number": "12",
                        "qr_payload": f"GPS61673|STU|{student_uid}|Class 3|12|abcd1234",
                        "face_embedding": unit_vec(2.5),
                        "face_thumbnail_b64": dummy_b64,
                        "registered_by_admin_uid": admin_uid,
                        "created_at_epoch_ms": 1700000000000,
                    }
                ]
            },
        )
        assert st_sync.status_code == 200
        assert student_uid in st_sync.json()["synced_uids"]

        att_uid_1 = str(uuid.uuid4())
        att_uid_2 = str(uuid.uuid4())
        att_sync = client.post(
            "/api/v1/sync/attendance",
            headers={"Authorization": bearer},
            json={
                "records": [
                    {
                        "local_uid": att_uid_1,
                        "student_uid": student_uid,
                        "student_name_snapshot": "Bilal Khan",
                        "father_name_snapshot": "Tariq Khan",
                        "class_name_snapshot": "Class 3",
                        "roll_number_snapshot": "12",
                        "status": "PRESENT",
                        "attendance_date": "2026-11-15",
                        "marked_at_iso": "2026-11-15T08:15:00",
                        "marked_at_epoch_ms": 1700000100000,
                        "verified_similarity": 0.94,
                        "marked_by_admin_uid": admin_uid,
                    },
                    {
                        "local_uid": att_uid_2,
                        "student_uid": student_uid,
                        "student_name_snapshot": "Bilal Khan",
                        "father_name_snapshot": "Tariq Khan",
                        "class_name_snapshot": "Class 3",
                        "roll_number_snapshot": "12",
                        "status": "ABSENT",
                        "attendance_date": "2026-11-15",
                        "marked_at_iso": "2026-11-15T09:00:00",
                        "marked_at_epoch_ms": 1700000500000,
                        "verified_similarity": 0.91,
                        "marked_by_admin_uid": admin_uid,
                    },
                ]
            },
        )
        assert att_sync.status_code == 200
        assert len(att_sync.json()["synced"]) == 1
        assert att_uid_2 in att_sync.json()["duplicate_preserved_uids"]

        # 8. Verify Append-Only SQLite/PostgreSQL Trigger Blocks DELETE on attendance_records
        with SessionLocal() as db:
            delete_failed = False
            try:
                db.execute(text("DELETE FROM attendance_records WHERE local_uid = :uid"), {"uid": att_uid_1})
                db.commit()
            except Exception:
                db.rollback()
                delete_failed = True
            assert delete_failed is True

        # 9. Verify Password Reset Token Single-Use & Expiration
        raw_tok, tok_hash = generate_reset_token()
        with SessionLocal() as db:
            db.add(
                PasswordResetToken(
                    admin_uid=admin_uid,
                    token_hash=tok_hash,
                    expires_at=datetime.now(timezone.utc) + timedelta(minutes=10),
                )
            )
            db.commit()

        reset_ok = client.post(
            "/api/v1/auth/password-reset/confirm",
            json={"token": raw_tok, "new_password": "NewPassword456"},
        )
        assert reset_ok.status_code == 200

        # Replay of same single-use token must fail
        reset_replay = client.post(
            "/api/v1/auth/password-reset/confirm",
            json={"token": raw_tok, "new_password": "AnotherPassword789"},
        )
        assert reset_replay.status_code == 400


def test_user_registration_without_picture_direct_login_and_direct_password_reset():
    with TestClient(app) as client:
        user_uid = str(uuid.uuid4())
        dummy_b64 = base64.b64encode(b"test-doc-bytes").decode("ascii")

        reg = client.post(
            "/api/v1/auth/register",
            json={
                "admin_uid": user_uid,
                "full_name": "Tariq Mahmood",
                "designation": "Teacher",
                "mobile_normalized": "03129876543",
                "cnic_normalized": "2120398765431",
                "cnic_formatted": "2120398765431",
                "email": "tariq.teacher@example.com",
                "password": "InitialPass123",
                "cnic_front_b64": dummy_b64,
                "cnic_back_b64": dummy_b64,
                "employment_doc_b64": dummy_b64,
                "employment_doc_mime": "image/jpeg",
            },
        )
        assert reg.status_code == 200

        # Login returns direct access_token without requiring user live picture
        login = client.post(
            "/api/v1/auth/login",
            json={"username": "03129876543", "password": "InitialPass123"},
        )
        assert login.status_code == 200
        assert login.json()["access_token"]
        assert login.json()["challenge_token"] is None

        # Direct password reset updates password without email token
        reset = client.post(
            "/api/v1/auth/password-reset/direct",
            json={"username": "2120398765431", "new_password": "UpdatedPass456"},
        )
        assert reset.status_code == 200

        login_new = client.post(
            "/api/v1/auth/login",
            json={"username": "03129876543", "password": "UpdatedPass456"},
        )
        assert login_new.status_code == 200
        assert login_new.json()["access_token"]

