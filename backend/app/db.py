from sqlalchemy import create_engine, event, text
from sqlalchemy.orm import declarative_base, sessionmaker
from .config import settings

connect_args = {"check_same_thread": False} if settings.DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(settings.DATABASE_URL, connect_args=connect_args, future=True)
SessionLocal = sessionmaker(bind=engine, autocommit=False, autoflush=False, future=True)
Base = declarative_base()


def install_append_only_triggers(target_engine):
    dialect = target_engine.dialect.name
    with target_engine.begin() as conn:
        if dialect == "sqlite":
            conn.execute(text("""
                CREATE TRIGGER IF NOT EXISTS trg_attendance_no_delete
                BEFORE DELETE ON attendance_records
                BEGIN
                    SELECT RAISE(ABORT, 'Append-only violation: attendance records cannot be deleted.');
                END;
            """))
            conn.execute(text("""
                CREATE TRIGGER IF NOT EXISTS trg_attendance_no_update
                BEFORE UPDATE ON attendance_records
                BEGIN
                    SELECT RAISE(ABORT, 'Append-only violation: attendance records are immutable.');
                END;
            """))
        elif dialect == "postgresql":
            conn.execute(text("""
                CREATE OR REPLACE FUNCTION prevent_attendance_delete_or_tamper()
                RETURNS TRIGGER AS $$
                BEGIN
                    RAISE EXCEPTION 'Append-only violation: attendance records cannot be modified or deleted.';
                END;
                $$ LANGUAGE plpgsql;
            """))
            conn.execute(text("""
                DROP TRIGGER IF EXISTS trg_attendance_append_only ON attendance_records;
                CREATE TRIGGER trg_attendance_append_only
                BEFORE UPDATE OR DELETE ON attendance_records
                FOR EACH ROW EXECUTE FUNCTION prevent_attendance_delete_or_tamper();
            """))


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
