package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AdminEntity::class,
        StudentEntity::class,
        AttendanceRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SmartAttendanceDatabase : RoomDatabase() {
    abstract fun adminDao(): AdminDao
    abstract fun studentDao(): StudentDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao

    companion object {
        private const val DB_NAME = "smart_attendance_gps61673.db"

        fun create(context: Context): SmartAttendanceDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SmartAttendanceDatabase::class.java,
                DB_NAME
            )
                .addCallback(AppendOnlyProtectionCallback)
                .build()
        }

        fun createInMemory(context: Context): SmartAttendanceDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                SmartAttendanceDatabase::class.java
            )
                .allowMainThreadQueries()
                .addCallback(AppendOnlyProtectionCallback)
                .build()
        }

        val AppendOnlyProtectionCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                installAppendOnlyTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                installAppendOnlyTriggers(db)
            }

            private fun installAppendOnlyTriggers(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS trg_attendance_prevent_delete
                    BEFORE DELETE ON attendance_records
                    BEGIN
                        SELECT RAISE(ABORT, 'Append-only violation: attendance records cannot be deleted.');
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS trg_attendance_prevent_tamper
                    BEFORE UPDATE ON attendance_records
                    WHEN OLD.local_uid != NEW.local_uid
                      OR OLD.student_uid != NEW.student_uid
                      OR OLD.student_name_snapshot != NEW.student_name_snapshot
                      OR OLD.father_name_snapshot != NEW.father_name_snapshot
                      OR OLD.class_name_snapshot != NEW.class_name_snapshot
                      OR OLD.roll_number_snapshot != NEW.roll_number_snapshot
                      OR OLD.status != NEW.status
                      OR OLD.attendance_date != NEW.attendance_date
                      OR OLD.marked_at_iso != NEW.marked_at_iso
                      OR OLD.marked_at_epoch_ms != NEW.marked_at_epoch_ms
                    BEGIN
                        SELECT RAISE(ABORT, 'Append-only violation: immutable attendance fields cannot be modified.');
                    END;
                    """.trimIndent()
                )
            }
        }
    }
}
