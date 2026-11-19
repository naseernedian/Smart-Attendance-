package com.example.data.sync

import android.content.Context
import android.util.Base64
import com.example.data.local.AdminDao
import com.example.data.local.AttendanceRecordDao
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.StudentDao
import com.example.data.local.StudentEntity
import com.example.data.remote.AttendanceApiService
import com.example.data.remote.AttendanceBatchSyncRequest
import com.example.data.remote.AttendanceSyncItemDto
import com.example.data.remote.StudentBatchSyncRequest
import com.example.data.remote.StudentSyncItemDto
import com.example.domain.SyncStatus
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncOutcome(
    val success: Boolean,
    val studentsUploaded: Int,
    val attendanceUploaded: Int,
    val studentsPulled: Int,
    val attendancePulled: Int,
    val message: String
)

class SyncRepository(
    private val context: Context,
    private val adminDao: AdminDao,
    private val studentDao: StudentDao,
    private val attendanceDao: AttendanceRecordDao,
    private val sessionPreferences: SessionPreferences,
    private val cryptoManager: CryptoManager,
    private val connectivityObserver: NetworkConnectivityObserver
) {
    suspend fun syncAll(
        apiProvider: (String) -> AttendanceApiService = { AttendanceApiService.create(it) }
    ): SyncOutcome = withContext(Dispatchers.IO) {
        if (!connectivityObserver.checkCurrentConnectivity()) {
            return@withContext SyncOutcome(
                success = false,
                studentsUploaded = 0,
                attendanceUploaded = 0,
                studentsPulled = 0,
                attendancePulled = 0,
                message = "Offline mode active. Local records are safely stored and queued for sync."
            )
        }

        val token = sessionPreferences.getDecryptedJwt()
        if (token.isNullOrBlank()) {
            return@withContext SyncOutcome(
                success = false,
                studentsUploaded = 0,
                attendanceUploaded = 0,
                studentsPulled = 0,
                attendancePulled = 0,
                message = "Online auth token unavailable. Sign in online to synchronize with server."
            )
        }

        val bearer = "Bearer $token"
        val baseUrl = sessionPreferences.getBackendBaseUrl()
        val api = apiProvider(baseUrl)

        return@withContext try {
            var studentsUploaded = 0
            var attendanceUploaded = 0
            var studentsPulled = 0
            var attendancePulled = 0

            // 1. Upload pending students in compact batches (max 15 per request for slow 2G)
            val pendingStudents = studentDao.getPendingStudents()
            pendingStudents.chunked(15).forEach { chunk ->
                val dtos = chunk.map { student ->
                    val embedding = cryptoManager.decryptEmbedding(student.encryptedFaceEmbedding).toList()
                    val thumbB64 = Base64.encodeToString(student.faceThumbnailBytes, Base64.NO_WRAP)
                    StudentSyncItemDto(
                        studentUid = student.studentUid,
                        studentName = student.studentName,
                        fatherName = student.fatherName,
                        className = student.className,
                        rollNumber = student.rollNumber,
                        qrPayload = student.qrPayload,
                        faceEmbedding = embedding,
                        faceThumbnailB64 = thumbB64,
                        registeredByAdminUid = student.registeredByAdminUid,
                        createdAtEpochMs = student.createdAtEpochMs
                    )
                }
                val resp = api.syncStudents(bearer, StudentBatchSyncRequest(dtos))
                if (resp.isSuccessful) {
                    val body = resp.body()
                    body?.syncedUids?.forEach { uid ->
                        studentDao.markStudentSynced(uid)
                        studentsUploaded++
                    }
                    body?.duplicateUids?.forEach { uid ->
                        studentDao.markStudentSynced(uid)
                    }
                } else {
                    error("Student sync failed (${resp.code()})")
                }
            }

            // 2. Upload pending append-only attendance records in batches of 25
            while (true) {
                val batch = attendanceDao.getPendingAttendanceBatch(25)
                if (batch.isEmpty()) break
                val req = AttendanceBatchSyncRequest(
                    records = batch.map { rec ->
                        AttendanceSyncItemDto(
                            localUid = rec.localUid,
                            studentUid = rec.studentUid,
                            studentNameSnapshot = rec.studentNameSnapshot,
                            fatherNameSnapshot = rec.fatherNameSnapshot,
                            classNameSnapshot = rec.classNameSnapshot,
                            rollNumberSnapshot = rec.rollNumberSnapshot,
                            status = rec.status,
                            attendanceDate = rec.attendanceDate,
                            markedAtIso = rec.markedAtIso,
                            markedAtEpochMs = rec.markedAtEpochMs,
                            verifiedSimilarity = rec.verifiedSimilarity,
                            markedByAdminUid = rec.markedByAdminUid
                        )
                    }
                )
                val resp = api.syncAttendance(bearer, req)
                if (resp.isSuccessful) {
                    val body = resp.body()
                    body?.synced?.forEach { ref ->
                        attendanceDao.markAttendanceSynced(ref.localUid, ref.serverId)
                        attendanceUploaded++
                    }
                    body?.duplicatePreservedUids?.forEach { uid ->
                        attendanceDao.markAttendanceSynced(uid, "DUPLICATE_PRESERVED")
                    }
                } else {
                    error("Attendance sync failed (${resp.code()})")
                }
            }

            // 3. Pull remote students & attendance (idempotent IGNORE so local originals are preserved)
            val remoteStudentsResp = api.fetchRemoteStudents(bearer)
            if (remoteStudentsResp.isSuccessful) {
                remoteStudentsResp.body().orEmpty().forEach { dto ->
                    val encryptedEmbedding = cryptoManager.encryptEmbedding(dto.faceEmbedding.toFloatArray())
                    val thumb = Base64.decode(dto.faceThumbnailB64, Base64.NO_WRAP)
                    val inserted = studentDao.insertStudentIgnore(
                        StudentEntity(
                            studentUid = dto.studentUid,
                            studentName = dto.studentName,
                            fatherName = dto.fatherName,
                            className = dto.className,
                            rollNumber = dto.rollNumber,
                            qrPayload = dto.qrPayload,
                            encryptedFaceEmbedding = encryptedEmbedding,
                            faceThumbnailBytes = thumb,
                            registeredByAdminUid = dto.registeredByAdminUid,
                            createdAtEpochMs = dto.createdAtEpochMs,
                            syncStatus = SyncStatus.SYNCED.name
                        )
                    )
                    if (inserted != -1L) studentsPulled++
                }
            }

            val remoteAttResp = api.fetchRemoteAttendance(bearer)
            if (remoteAttResp.isSuccessful) {
                remoteAttResp.body().orEmpty().forEach { dto ->
                    val inserted = attendanceDao.insertAttendanceAppendOnly(
                        AttendanceRecordEntity(
                            localUid = dto.localUid,
                            studentUid = dto.studentUid,
                            studentNameSnapshot = dto.studentNameSnapshot,
                            fatherNameSnapshot = dto.fatherNameSnapshot,
                            classNameSnapshot = dto.classNameSnapshot,
                            rollNumberSnapshot = dto.rollNumberSnapshot,
                            status = dto.status,
                            attendanceDate = dto.attendanceDate,
                            markedAtIso = dto.markedAtIso,
                            markedAtEpochMs = dto.markedAtEpochMs,
                            verifiedSimilarity = dto.verifiedSimilarity,
                            markedByAdminUid = dto.markedByAdminUid,
                            syncStatus = SyncStatus.SYNCED.name,
                            serverId = dto.localUid
                        )
                    )
                    if (inserted != -1L) attendancePulled++
                }
            }

            sessionPreferences.recordSyncTimestamp(System.currentTimeMillis())
            SyncOutcome(
                success = true,
                studentsUploaded = studentsUploaded,
                attendanceUploaded = attendanceUploaded,
                studentsPulled = studentsPulled,
                attendancePulled = attendancePulled,
                message = "Sync complete: ↑$studentsUploaded students, ↑$attendanceUploaded attendance, ↓$studentsPulled/$attendancePulled pulled."
            )
        } catch (t: Throwable) {
            SyncOutcome(
                success = false,
                studentsUploaded = 0,
                attendanceUploaded = 0,
                studentsPulled = 0,
                attendancePulled = 0,
                message = "Sync paused (${t.message ?: "network error"}). Pending records remain safe offline."
            )
        }
    }
}
