import os
from pathlib import Path
from cryptography.fernet import Fernet


class Settings:
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        f"sqlite:///{Path(__file__).resolve().parent.parent / 'smart_attendance_dev.db'}"
    )
    JWT_SECRET: str = os.getenv("JWT_SECRET", "dev-only-change-in-production-61673")
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_TTL_HOURS: int = 12
    CHALLENGE_TTL_MINUTES: int = 5
    RESET_TOKEN_TTL_MINUTES: int = 15

    _raw_fernet = os.getenv("BIOMETRIC_FERNET_KEY", "")
    BIOMETRIC_FERNET_KEY: bytes = (
        _raw_fernet.encode("utf-8")
        if _raw_fernet
        else Fernet.generate_key()
    )

    SECURE_UPLOAD_DIR: Path = Path(
        os.getenv(
            "SECURE_UPLOAD_DIR",
            str(Path(__file__).resolve().parent.parent / "secure_uploads")
        )
    )

    SMTP_HOST: str = os.getenv("SMTP_HOST", "")
    SMTP_PORT: int = int(os.getenv("SMTP_PORT", "587"))
    SMTP_USER: str = os.getenv("SMTP_USER", "")
    SMTP_PASSWORD: str = os.getenv("SMTP_PASSWORD", "")
    SMTP_FROM: str = os.getenv("SMTP_FROM", "no-reply@gpskozshinoli.edu.pk")
    APP_RESET_BASE_URL: str = os.getenv("APP_RESET_BASE_URL", "smartattendance://reset-password")


settings = Settings()
settings.SECURE_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
