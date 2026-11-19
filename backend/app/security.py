import base64
import hashlib
import hmac
import json
import math
import re
import secrets
from datetime import datetime, timedelta, timezone
from typing import List, Optional, Tuple
import jwt
from cryptography.fernet import Fernet
from .config import settings

fernet = Fernet(settings.BIOMETRIC_FERNET_KEY)

CNIC_RAW_RE = re.compile(r"^\d{13}$")
MOBILE_RAW_RE = re.compile(r"^03\d{9}$")


def validate_cnic(raw: str) -> Optional[Tuple[str, str]]:
    s = raw.strip()
    if not CNIC_RAW_RE.match(s):
        return None
    return s, s


def validate_mobile(raw: str) -> Optional[Tuple[str, str]]:
    s = raw.strip()
    if not MOBILE_RAW_RE.match(s):
        return None
    return s, s


def normalize_mobile(raw: str) -> Optional[str]:
    pair = validate_mobile(raw)
    return pair[0] if pair else None


def hash_password(password: str) -> Tuple[str, str]:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 120_000)
    return base64.b64encode(salt).decode("ascii"), base64.b64encode(digest).decode("ascii")


def verify_password(password: str, salt_b64: str, hash_b64: str) -> bool:
    salt = base64.b64decode(salt_b64)
    expected = base64.b64decode(hash_b64)
    actual = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 120_000)
    return hmac.compare_digest(actual, expected)


def encrypt_embedding(embedding: List[float]) -> str:
    payload = json.dumps(embedding, separators=(",", ":")).encode("utf-8")
    return fernet.encrypt(payload).decode("ascii")


def decrypt_embedding(encrypted_blob: str) -> List[float]:
    raw = fernet.decrypt(encrypted_blob.encode("ascii"))
    return json.loads(raw.decode("utf-8"))


def encrypt_document_bytes(raw_bytes: bytes) -> bytes:
    return fernet.encrypt(raw_bytes)


def cosine_similarity(a: List[float], b: List[float]) -> float:
    if len(a) != len(b) or not a:
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if na < 1e-6 or nb < 1e-6:
        return 0.0
    return max(-1.0, min(1.0, dot / (na * nb)))


def create_jwt(admin_uid: str, token_type: str, ttl_minutes: int) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": admin_uid,
        "type": token_type,
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(minutes=ttl_minutes)).timestamp()),
    }
    return jwt.encode(payload, settings.JWT_SECRET, algorithm=settings.JWT_ALGORITHM)


def decode_jwt(token: str, expected_type: str) -> Optional[str]:
    try:
        payload = jwt.decode(token, settings.JWT_SECRET, algorithms=[settings.JWT_ALGORITHM])
        if payload.get("type") != expected_type:
            return None
        return payload.get("sub")
    except jwt.PyJWTError:
        return None


def generate_reset_token() -> Tuple[str, str]:
    raw_token = secrets.token_urlsafe(32)
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    return raw_token, token_hash


def hash_reset_token(raw_token: str) -> str:
    return hashlib.sha256(raw_token.strip().encode("utf-8")).hexdigest()
