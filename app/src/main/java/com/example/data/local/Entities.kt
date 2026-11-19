package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "admins",
    indices = [
        Index(value = ["cnic_normalized"], unique = true),
        Index(value = ["mobile_normalized"], unique = true),
        Index(value = ["email"], unique = true)
    ]
)
data class AdminEntity(
    @PrimaryKey
    @ColumnInfo(name = "admin_uid")
    val adminUid: String,

    @ColumnInfo(name = "full_name")
    val fullName: String,

    @ColumnInfo(name = "designation")
    val designation: String,

    @ColumnInfo(name = "mobile_normalized")
    val mobileNormalized: String,

    @ColumnInfo(name = "cnic_normalized")
    val cnicNormalized: String,

    @ColumnInfo(name = "cnic_formatted")
    val cnicFormatted: String,

    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "password_salt_b64")
    val passwordSaltB64: String,

    @ColumnInfo(name = "password_hash_b64")
    val passwordHashB64: String,

    @ColumnInfo(name = "encrypted_face_embedding", typeAffinity = ColumnInfo.BLOB)
    val encryptedFaceEmbedding: ByteArray,

    @ColumnInfo(name = "face_thumbnail_bytes", typeAffinity = ColumnInfo.BLOB)
    val faceThumbnailBytes: ByteArray,

    @ColumnInfo(name = "cnic_front_doc_path")
    val cnicFrontDocPath: String,

    @ColumnInfo(name = "cnic_back_doc_path")
    val cnicBackDocPath: String,

    @ColumnInfo(name = "employment_doc_path")
    val employmentDocPath: String,

    @ColumnInfo(name = "authorization_status")
    val authorizationStatus: String, // PENDING_AUTHORIZATION, APPROVED, REJECTED

    @ColumnInfo(name = "authorized_by")
    val authorizedBy: String? = null,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String // PENDING, SYNCED
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdminEntity) return false
        return adminUid == other.adminUid &&
            authorizationStatus == other.authorizationStatus &&
            syncStatus == other.syncStatus
    }

    override fun hashCode(): Int = adminUid.hashCode()
}

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["class_name", "roll_number"], unique = true),
        Index(value = ["qr_payload"], unique = true)
    ]
)
data class StudentEntity(
    @PrimaryKey
    @ColumnInfo(name = "student_uid")
    val studentUid: String,

    @ColumnInfo(name = "student_name")
    val studentName: String,

    @ColumnInfo(name = "father_name")
    val fatherName: String,

    @ColumnInfo(name = "class_name")
    val className: String,

    @ColumnInfo(name = "roll_number")
    val rollNumber: String,

    @ColumnInfo(name = "qr_payload")
    val qrPayload: String,

    @ColumnInfo(name = "encrypted_face_embedding", typeAffinity = ColumnInfo.BLOB)
    val encryptedFaceEmbedding: ByteArray,

    @ColumnInfo(name = "face_thumbnail_bytes", typeAffinity = ColumnInfo.BLOB)
    val faceThumbnailBytes: ByteArray,

    @ColumnInfo(name = "registered_by_admin_uid")
    val registeredByAdminUid: String,

    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String // PENDING, SYNCED
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StudentEntity) return false
        return studentUid == other.studentUid && syncStatus == other.syncStatus
    }

    override fun hashCode(): Int = studentUid.hashCode()
}

/**
 * Append-only attendance entity.
 * Intentionally avoids ForeignKey ON DELETE CASCADE and captures immutable student detail snapshots
 * so attendance history remains preserved even if student records are removed or archived.
 */
@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["student_uid", "attendance_date"], unique = true),
        Index(value = ["attendance_date"]),
        Index(value = ["class_name_snapshot"])
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "local_uid")
    val localUid: String,

    @ColumnInfo(name = "student_uid")
    val studentUid: String,

    @ColumnInfo(name = "student_name_snapshot")
    val studentNameSnapshot: String,

    @ColumnInfo(name = "father_name_snapshot")
    val fatherNameSnapshot: String,

    @ColumnInfo(name = "class_name_snapshot")
    val classNameSnapshot: String,

    @ColumnInfo(name = "roll_number_snapshot")
    val rollNumberSnapshot: String,

    @ColumnInfo(name = "status")
    val status: String, // PRESENT, ABSENT, LEAVE

    @ColumnInfo(name = "attendance_date")
    val attendanceDate: String, // yyyy-MM-dd

    @ColumnInfo(name = "marked_at_iso")
    val markedAtIso: String,

    @ColumnInfo(name = "marked_at_epoch_ms")
    val markedAtEpochMs: Long,

    @ColumnInfo(name = "verified_similarity")
    val verifiedSimilarity: Float,

    @ColumnInfo(name = "marked_by_admin_uid")
    val markedByAdminUid: String,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String, // PENDING, SYNCED

    @ColumnInfo(name = "server_id")
    val serverId: String? = null
)
