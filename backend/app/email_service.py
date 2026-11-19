import smtplib
from email.message import EmailMessage
from typing import Tuple
from .config import settings


def send_password_reset_email(recipient_email: str, admin_name: str, reset_token: str) -> Tuple[bool, str]:
    """
    Sends a time-limited, single-use password reset link to the registered user email via SMTP.
    Never simulates or fakes email delivery when SMTP credentials are unconfigured.
    """
    if not settings.SMTP_HOST or not settings.SMTP_USER or not settings.SMTP_PASSWORD:
        return (
            False,
            "SMTP email server is not configured on this backend. Configure SMTP_HOST, SMTP_USER, and SMTP_PASSWORD in backend/.env."
        )

    reset_link = f"{settings.APP_RESET_BASE_URL}?token={reset_token}"
    msg = EmailMessage()
    msg["Subject"] = "GPS Koz Shinoli Smart Attendance — Secure Password Reset"
    msg["From"] = settings.SMTP_FROM
    msg["To"] = recipient_email
    msg.set_content(
        f"Assalam-o-Alaikum {admin_name},\n\n"
        f"A password reset was requested for your account at GPS Koz Shinoli (EMIS Code: 61673).\n\n"
        f"Open the secure reset link below within {settings.RESET_TOKEN_TTL_MINUTES} minutes (single-use):\n"
        f"{reset_link}\n\n"
        f"Or enter this reset token in the app:\n{reset_token}\n\n"
        f"If you did not request this reset, ignore this email."
    )

    try:
        with smtplib.SMTP(settings.SMTP_HOST, settings.SMTP_PORT, timeout=15) as server:
            server.starttls()
            server.login(settings.SMTP_USER, settings.SMTP_PASSWORD)
            server.send_message(msg)
        return True, "A secure time-limited password reset link has been sent to your registered email address."
    except Exception as exc:
        return False, f"Failed to dispatch password reset email: {exc}"
