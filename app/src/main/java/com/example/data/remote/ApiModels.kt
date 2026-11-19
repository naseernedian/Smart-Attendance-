package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdminRegistrationRequest(
    @Json(name = "admin_uid") val adminUid: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "designation") val designation: String,
    @Json(name = "mobile_normalized") val mobileNormalized: String,
    @Json(name = "cnic_normalized") val cnicNormalized: String,
    @Json(name = "cnic_formatted") val cnicFormatted: String,
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "face_embedding") val faceEmbedding: List<Float> = emptyList(),
    @Json(name = "face_thumbnail_b64") val faceThumbnailB64: String = "",
    @Json(name = "cnic_front_b64") val cnicFrontB64: String,
    @Json(name = "cnic_back_b64") val cnicBackB64: String,
    @Json(name = "employment_doc_b64") val employmentDocB64: String,
    @Json(name = "employment_doc_mime") val employmentDocMime: String
)

@JsonClass(generateAdapter = true)
data class AdminRegistrationResponse(
    @Json(name = "admin_uid") val adminUid: String,
    @Json(name = "authorization_status") val authorizationStatus: String,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class AdminLoginRequest(
    @Json(name = "username") val username: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AdminProfileDto(
    @Json(name = "admin_uid") val adminUid: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "designation") val designation: String,
    @Json(name = "mobile_normalized") val mobileNormalized: String,
    @Json(name = "cnic_normalized") val cnicNormalized: String,
    @Json(name = "cnic_formatted") val cnicFormatted: String,
    @Json(name = "email") val email: String,
    @Json(name = "authorization_status") val authorizationStatus: String,
    @Json(name = "authorized_by") val authorizedBy: String? = null,
    @Json(name = "face_embedding") val faceEmbedding: List<Float>,
    @Json(name = "face_thumbnail_b64") val faceThumbnailB64: String
)

@JsonClass(generateAdapter = true)
data class AdminLoginResponse(
    @Json(name = "challenge_token") val challengeToken: String?,
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "authorization_status") val authorizationStatus: String,
    @Json(name = "message") val message: String,
    @Json(name = "admin") val admin: AdminProfileDto?
)

@JsonClass(generateAdapter = true)
data class AdminFaceVerifyRequest(
    @Json(name = "challenge_token") val challengeToken: String,
    @Json(name = "live_face_embedding") val liveFaceEmbedding: List<Float>
)

@JsonClass(generateAdapter = true)
data class AdminFaceVerifyResponse(
    @Json(name = "verified") val verified: Boolean,
    @Json(name = "similarity") val similarity: Float,
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class DirectPasswordResetRequestDto(
    @Json(name = "username") val username: String,
    @Json(name = "new_password") val newPassword: String
)

@JsonClass(generateAdapter = true)
data class StudentSyncItemDto(
    @Json(name = "student_uid") val studentUid: String,
    @Json(name = "student_name") val studentName: String,
    @Json(name = "father_name") val fatherName: String,
    @Json(name = "class_name") val className: String,
    @Json(name = "roll_number") val rollNumber: String,
    @Json(name = "qr_payload") val qrPayload: String,
    @Json(name = "face_embedding") val faceEmbedding: List<Float>,
    @Json(name = "face_thumbnail_b64") val faceThumbnailB64: String,
    @Json(name = "registered_by_admin_uid") val registeredByAdminUid: String,
    @Json(name = "created_at_epoch_ms") val createdAtEpochMs: Long
)

@JsonClass(generateAdapter = true)
data class StudentBatchSyncRequest(
    @Json(name = "students") val students: List<StudentSyncItemDto>
)

@JsonClass(generateAdapter = true)
data class StudentBatchSyncResponse(
    @Json(name = "synced_uids") val syncedUids: List<String>,
    @Json(name = "duplicate_uids") val duplicateUids: List<String>,
    @Json(name = "rejected") val rejected: Map<String, String>
)

@JsonClass(generateAdapter = true)
data class AttendanceSyncItemDto(
    @Json(name = "local_uid") val localUid: String,
    @Json(name = "student_uid") val studentUid: String,
    @Json(name = "student_name_snapshot") val studentNameSnapshot: String,
    @Json(name = "father_name_snapshot") val fatherNameSnapshot: String,
    @Json(name = "class_name_snapshot") val classNameSnapshot: String,
    @Json(name = "roll_number_snapshot") val rollNumberSnapshot: String,
    @Json(name = "status") val status: String,
    @Json(name = "attendance_date") val attendanceDate: String,
    @Json(name = "marked_at_iso") val markedAtIso: String,
    @Json(name = "marked_at_epoch_ms") val markedAtEpochMs: Long,
    @Json(name = "verified_similarity") val verifiedSimilarity: Float,
    @Json(name = "marked_by_admin_uid") val markedByAdminUid: String
)

@JsonClass(generateAdapter = true)
data class AttendanceBatchSyncRequest(
    @Json(name = "records") val records: List<AttendanceSyncItemDto>
)

@JsonClass(generateAdapter = true)
data class SyncedAttendanceRefDto(
    @Json(name = "local_uid") val localUid: String,
    @Json(name = "server_id") val serverId: String
)

@JsonClass(generateAdapter = true)
data class AttendanceBatchSyncResponse(
    @Json(name = "synced") val synced: List<SyncedAttendanceRefDto>,
    @Json(name = "duplicate_preserved_uids") val duplicatePreservedUids: List<String>
)
