# SMART ATTENDANCE APP — GPS Koz Shinoli (EMIS Code: 61673)
**Developed by Naseer Ud Din Shinwari**

## 1. Architecture & Offline/Online Data Flow
- **Offline-First Local Layer (Android):**
  - **CameraX + ML Kit Face Detection (Bundled):** Performs offline live face detection, 3D pose check, eye-open check, and eye-aligned `112x112` face cropping.
  - **On-Device Facial Recognition Engine (`FaceRecognitionEngine`):**
    - Uses **TensorFlow Lite (`MobileFaceNet`)** automatically if `app/src/main/assets/mobilefacenet.tflite` is bundled (192-D embedding, cosine threshold `0.78`).
    - Falls back automatically to the built-in deterministic **Aligned Multi-Scale LBP + Oriented Gradient Histogram (HOG) + Normalized Landmark Geometry Extractor** (256-D embedding, cosine threshold `0.86`) when no `.tflite` file is placed in `assets/`.
  - **Android Keystore (`AES-256-GCM`):** Encrypts stored student/admin facial embeddings and CNIC/employment documents at rest.
  - **Room SQLite Database:** Enforces unique `(class_name, roll_number)` for students and unique `(student_uid, attendance_date)` for attendance, plus SQLite `BEFORE DELETE` and `BEFORE UPDATE` triggers preventing attendance deletion or tampering.
  - **ZXing + ML Kit Barcode Scanner:** Generates signed student QR payloads (`GPS61673|STU|<uid>|<class>|<roll>|<checksum>`) and scans them offline.
  - **ReportGenerator:** Exports **Daily**, **Weekly**, and **Monthly** reports in **PDF**, **Excel (.xlsx)**, and **CSV** directly from local Room records.
- **Online Sync & Relational Backend (`/backend/`):**
  - **FastAPI + PostgreSQL (`schema.sql`):** Stores admins, password reset tokens, students, append-only `attendance_records` (with `BEFORE UPDATE OR DELETE` PostgreSQL trigger), and `attendance_audit_log`.
  - **Slow-Internet Optimization:** GZIP response compression, `<= 24 KB` compressed face thumbnails, batched sync (15 students / 25 attendance records per request), and WorkManager exponential backoff when connectivity returns.

## 2. External Services Requiring Configuration
1. **Relational Database (PostgreSQL):**
   - Create a database and apply `/backend/schema.sql`.
   - Copy `/backend/.env.example` to `/backend/.env` and set `DATABASE_URL`, `JWT_SECRET`, and `BIOMETRIC_FERNET_KEY`.
2. **SMTP Email Service (Password Reset):**
   - Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, and `SMTP_FROM` in `/backend/.env` so time-limited, single-use reset links can be delivered to registered user emails.
3. **Optional Neural Model (`mobilefacenet.tflite`):**
   - To use deep-learning neural face embeddings on-device, place an open-source `mobilefacenet.tflite` (input `1x112x112x3` float32 `[-1,1]`, output `1x192` float32) inside `app/src/main/assets/mobilefacenet.tflite`.
4. **Open User Registration & Formats:**
   - All registered users are immediately active (`APPROVED`) and can log in without external approval.
   - Mobile numbers use `03XXXXXXXXX` (11 digits) and Pakistani CNIC uses `XXXXXXXXXXXXX` (13 digits).

## 3. Running the Backend Locally
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env  # Configure DATABASE_URL, secrets, and SMTP
uvicorn app.main:app --host 0.0.0.0 --port 8000
```
Run backend tests:
```bash
cd backend && pytest -v
```

## 4. Building & Installing the Android APK on an Android Phone
1. **Configure Backend URL:**
   - Copy `.env.example` to `.env` in the project root and set `BACKEND_BASE_URL=https://your-school-server.example/` (or use the in-app server badge on the login screen for LAN testing).
2. **Build Debug or Release APK:**
   - Using Gradle:
     ```bash
     gradle :app:assembleDebug
     ```
     APK output: `app/build/outputs/apk/debug/app-debug.apk`
   - Or in **Android Studio**: Open the project root, let Gradle sync, select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. **Install on Android Phone:**
   - Transfer `app-debug.apk` via USB or enable **USB Debugging** and run:
     ```bash
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```
   - On Android 8+, allow installing from the file manager / USB source if prompted, then grant **Camera** permission on first face capture or QR scan.

## 5. Attendance Record Protection Limitations
- Database triggers (`BEFORE DELETE` / `BEFORE UPDATE`), append-only Room DAOs, `REVOKE DELETE` SQL role permissions, and omitted `ON DELETE CASCADE` protect attendance records from accidental or API-level deletion.
- Technical limitations: Device-level OS root access, full app uninstall/factory reset prior to online sync, or PostgreSQL database superuser (`postgres`) intervention can still remove physical storage outside application control. Periodic encrypted offsite PostgreSQL backups are recommended.
