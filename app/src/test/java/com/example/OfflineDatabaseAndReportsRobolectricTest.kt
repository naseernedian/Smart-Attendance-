package com.example

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.example.biometrics.QrCodeManager
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.SessionPreferences
import com.example.data.local.SmartAttendanceDatabase
import com.example.data.local.StudentEntity
import com.example.data.remote.AdminFaceVerifyRequest
import com.example.data.remote.AdminFaceVerifyResponse
import com.example.data.remote.AdminLoginRequest
import com.example.data.remote.AdminLoginResponse
import com.example.data.remote.AdminRegistrationRequest
import com.example.data.remote.AdminRegistrationResponse
import com.example.data.remote.AttendanceApiService
import com.example.data.remote.AttendanceBatchSyncRequest
import com.example.data.remote.AttendanceBatchSyncResponse
import com.example.data.remote.AttendanceSyncItemDto
import com.example.data.remote.DirectPasswordResetRequestDto
import com.example.data.remote.StudentBatchSyncRequest
import com.example.data.remote.StudentBatchSyncResponse
import com.example.data.remote.StudentSyncItemDto
import com.example.data.remote.SyncedAttendanceRefDto
import com.example.data.sync.NetworkConnectivityObserver
import com.example.data.sync.SyncRepository
import com.example.domain.AttendanceStatus
import com.example.domain.ExportFormat
import com.example.domain.ReportPeriod
import com.example.domain.SyncStatus
import com.example.reports.ReportGenerator
import com.example.security.CryptoManager
import com.example.security.SoftwareTestKeyProvider
import com.example.ui.viewmodel.MainWorkspaceViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineDatabaseAndReportsRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: SmartAttendanceDatabase
    private lateinit var crypto: CryptoManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = SmartAttendanceDatabase.createInMemory(context)
        crypto = CryptoManager(context, SoftwareTestKeyProvider())
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `duplicate student roll and duplicate daily attendance are prevented and original timestamp preserved`() = runTest {
        val studentDao = db.studentDao()
        val attendanceDao = db.attendanceRecordDao()

        val qr1 = QrCodeManager.buildStudentQrPayload("stu-1", "Class 1", "5")
        val s1 = StudentEntity(
            studentUid = "stu-1",
            studentName = "Ayesha Bibi",
            fatherName = "Gul Khan",
            className = "Class 1",
            rollNumber = "5",
            qrPayload = qr1,
            encryptedFaceEmbedding = crypto.encryptEmbedding(FloatArray(256) { 0.1f }),
            faceThumbnailBytes = byteArrayOf(1, 2, 3),
            registeredByAdminUid = "admin-1",
            createdAtEpochMs = 1700000000000L,
            syncStatus = SyncStatus.PENDING.name
        )
        assertTrue(studentDao.insertStudentIgnore(s1) > 0)

        // Duplicate (Class 1, Roll 5) must be ignored
        val duplicateStudent = s1.copy(
            studentUid = "stu-2",
            qrPayload = QrCodeManager.buildStudentQrPayload("stu-2", "Class 1", "5")
        )
        assertEquals(-1L, studentDao.insertStudentIgnore(duplicateStudent))

        // First attendance insertion succeeds
        val r1 = AttendanceRecordEntity(
            localUid = "att-1",
            studentUid = "stu-1",
            studentNameSnapshot = "Ayesha Bibi",
            fatherNameSnapshot = "Gul Khan",
            classNameSnapshot = "Class 1",
            rollNumberSnapshot = "5",
            status = AttendanceStatus.PRESENT.name,
            attendanceDate = "2026-11-15",
            markedAtIso = "2026-11-15T08:05:00",
            markedAtEpochMs = 1700000100000L,
            verifiedSimilarity = 0.95f,
            markedByAdminUid = "admin-1",
            syncStatus = SyncStatus.PENDING.name
        )
        assertTrue(attendanceDao.insertAttendanceAppendOnly(r1) > 0)

        // Second attendance for same student and same date must be ignored & original timestamp kept
        val r2 = r1.copy(
            localUid = "att-2",
            status = AttendanceStatus.ABSENT.name,
            markedAtIso = "2026-11-15T10:30:00",
            markedAtEpochMs = 1700009900000L
        )
        assertEquals(-1L, attendanceDao.insertAttendanceAppendOnly(r2))

        val saved = attendanceDao.findByStudentAndDate("stu-1", "2026-11-15")
        assertEquals("PRESENT", saved?.status)
        assertEquals("2026-11-15T08:05:00", saved?.markedAtIso)
    }

    @Test
    fun `append only sqlite trigger blocks deletion and preserves history even if student removed`() = runTest {
        val studentDao = db.studentDao()
        val attendanceDao = db.attendanceRecordDao()

        val qr = QrCodeManager.buildStudentQrPayload("stu-99", "Class 4", "9")
        studentDao.insertStudentIgnore(
            StudentEntity(
                studentUid = "stu-99",
                studentName = "Hamza Afridi",
                fatherName = "Saeed Afridi",
                className = "Class 4",
                rollNumber = "9",
                qrPayload = qr,
                encryptedFaceEmbedding = crypto.encryptEmbedding(FloatArray(256) { 0.2f }),
                faceThumbnailBytes = byteArrayOf(4, 5, 6),
                registeredByAdminUid = "admin-1",
                createdAtEpochMs = 1700000000000L,
                syncStatus = SyncStatus.PENDING.name
            )
        )
        attendanceDao.insertAttendanceAppendOnly(
            AttendanceRecordEntity(
                localUid = "att-99",
                studentUid = "stu-99",
                studentNameSnapshot = "Hamza Afridi",
                fatherNameSnapshot = "Saeed Afridi",
                classNameSnapshot = "Class 4",
                rollNumberSnapshot = "9",
                status = AttendanceStatus.LEAVE.name,
                attendanceDate = "2026-11-15",
                markedAtIso = "2026-11-15T08:30:00",
                markedAtEpochMs = 1700000300000L,
                verifiedSimilarity = 0.92f,
                markedByAdminUid = "admin-1",
                syncStatus = SyncStatus.PENDING.name
            )
        )

        // Direct SQL DELETE on attendance_records MUST fail via SQLite trigger
        var deleteBlocked = false
        try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM attendance_records WHERE local_uid = 'att-99'")
        } catch (_: SQLiteException) {
            deleteBlocked = true
        }
        assertTrue(deleteBlocked)

        // Direct SQL UPDATE of status MUST fail via SQLite trigger
        var tamperBlocked = false
        try {
            db.openHelper.writableDatabase.execSQL("UPDATE attendance_records SET status = 'ABSENT' WHERE local_uid = 'att-99'")
        } catch (_: SQLiteException) {
            tamperBlocked = true
        }
        assertTrue(tamperBlocked)

        // Even if student row is deleted from students table, attendance snapshot remains preserved
        db.openHelper.writableDatabase.execSQL("DELETE FROM students WHERE student_uid = 'stu-99'")
        val preserved = attendanceDao.observeAllAttendance().first()
        assertEquals(1, preserved.size)
        assertEquals("Hamza Afridi", preserved.first().studentNameSnapshot)
    }

    @Test
    fun `report generator creates valid csv, pdf, and xlsx files from stored records`() {
        val generator = ReportGenerator(context)
        val records = listOf(
            AttendanceRecordEntity(
                localUid = "att-rep-1",
                studentUid = "stu-1",
                studentNameSnapshot = "Zainab Shinwari",
                fatherNameSnapshot = "Akram Shinwari",
                classNameSnapshot = "Class 2",
                rollNumberSnapshot = "3",
                status = "PRESENT",
                attendanceDate = "2026-11-15",
                markedAtIso = "2026-11-15T08:10:00",
                markedAtEpochMs = 1700000200000L,
                verifiedSimilarity = 0.93f,
                markedByAdminUid = "admin-1",
                syncStatus = "SYNCED"
            )
        )

        val csv = generator.generateReport(records, ReportPeriod.DAILY, "2026-11-15", null, ExportFormat.CSV)
        val csvContent = csv.file.readText()
        assertTrue(csvContent.contains("GPS Koz Shinoli"))
        assertTrue(csvContent.contains("Zainab Shinwari"))
        assertTrue(csvContent.contains("PRESENT"))

        val pdf = generator.generateReport(records, ReportPeriod.WEEKLY, "2026-11-09 to 2026-11-15", null, ExportFormat.PDF)
        val pdfBytes = pdf.file.readBytes()
        assertTrue(pdfBytes.size > 200)
        assertEquals("%PDF-", String(pdfBytes.take(5).toByteArray()))

        val xlsx = generator.generateReport(records, ReportPeriod.MONTHLY, "November 2026", null, ExportFormat.XLSX)
        var foundSheetXml = false
        var sheetText = ""
        ZipInputStream(xlsx.file.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    foundSheetXml = true
                    sheetText = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }
        assertTrue(foundSheetXml)
        assertTrue(sheetText.contains("Zainab Shinwari"))
        assertTrue(sheetText.contains("PRESENT"))
    }

    @Test
    fun `sync repository uploads pending records idempotently`() = runTest {
        val prefs = SessionPreferences(context, crypto)
        prefs.saveEncryptedJwt("valid-jwt-token", "admin-1")

        db.studentDao().insertStudentIgnore(
            StudentEntity(
                studentUid = "stu-sync-1",
                studentName = "Sanaullah",
                fatherName = "Wali Khan",
                className = "Class 5",
                rollNumber = "1",
                qrPayload = QrCodeManager.buildStudentQrPayload("stu-sync-1", "Class 5", "1"),
                encryptedFaceEmbedding = crypto.encryptEmbedding(FloatArray(256) { 0.3f }),
                faceThumbnailBytes = byteArrayOf(7, 8, 9),
                registeredByAdminUid = "admin-1",
                createdAtEpochMs = 1700000000000L,
                syncStatus = SyncStatus.PENDING.name
            )
        )
        db.attendanceRecordDao().insertAttendanceAppendOnly(
            AttendanceRecordEntity(
                localUid = "att-sync-1",
                studentUid = "stu-sync-1",
                studentNameSnapshot = "Sanaullah",
                fatherNameSnapshot = "Wali Khan",
                classNameSnapshot = "Class 5",
                rollNumberSnapshot = "1",
                status = "PRESENT",
                attendanceDate = "2026-11-15",
                markedAtIso = "2026-11-15T08:00:00",
                markedAtEpochMs = 1700000000000L,
                verifiedSimilarity = 0.96f,
                markedByAdminUid = "admin-1",
                syncStatus = SyncStatus.PENDING.name
            )
        )

        var simulatedOnline = false
        val fakeConnectivity = object : NetworkConnectivityObserver(context) {
            override fun checkCurrentConnectivity(): Boolean = simulatedOnline
        }
        val repo = SyncRepository(
            context = context,
            adminDao = db.adminDao(),
            studentDao = db.studentDao(),
            attendanceDao = db.attendanceRecordDao(),
            sessionPreferences = prefs,
            cryptoManager = crypto,
            connectivityObserver = fakeConnectivity
        )

        // 1. Offline mode: records stay safely queued as PENDING
        val offlineOutcome = repo.syncAll { FakeApiService() }
        assertEquals(false, offlineOutcome.success)
        assertEquals(1, db.attendanceRecordDao().getPendingAttendanceBatch(10).size)

        // 2. Connectivity restored: records sync once and are marked SYNCED
        simulatedOnline = true
        val onlineOutcome = repo.syncAll { FakeApiService() }
        assertTrue(onlineOutcome.success)
        assertEquals(1, onlineOutcome.studentsUploaded)
        assertEquals(1, onlineOutcome.attendanceUploaded)
        assertEquals(0, db.attendanceRecordDao().getPendingAttendanceBatch(10).size)

        // 3. Repeat sync causes zero duplicate uploads
        val secondSync = repo.syncAll { FakeApiService() }
        assertTrue(secondSync.success)
        assertEquals(0, secondSync.studentsUploaded)
        assertEquals(0, secondSync.attendanceUploaded)
    }

    private class FakeApiService : AttendanceApiService {
        override suspend fun registerAdmin(request: AdminRegistrationRequest): Response<AdminRegistrationResponse> =
            error("unused")
        override suspend fun loginAdmin(request: AdminLoginRequest): Response<AdminLoginResponse> =
            error("unused")
        override suspend fun verifyAdminFace(request: AdminFaceVerifyRequest): Response<AdminFaceVerifyResponse> =
            error("unused")
        override suspend fun resetPasswordDirect(request: DirectPasswordResetRequestDto): Response<AdminRegistrationResponse> =
            error("unused")

        override suspend fun syncStudents(
            bearerToken: String,
            request: StudentBatchSyncRequest
        ): Response<StudentBatchSyncResponse> = Response.success(
            StudentBatchSyncResponse(
                syncedUids = request.students.map { it.studentUid },
                duplicateUids = emptyList(),
                rejected = emptyMap()
            )
        )

        override suspend fun fetchRemoteStudents(bearerToken: String): Response<List<StudentSyncItemDto>> =
            Response.success(emptyList())

        override suspend fun syncAttendance(
            bearerToken: String,
            request: AttendanceBatchSyncRequest
        ): Response<AttendanceBatchSyncResponse> = Response.success(
            AttendanceBatchSyncResponse(
                synced = request.records.map { SyncedAttendanceRefDto(it.localUid, "srv-${it.localUid}") },
                duplicatePreservedUids = emptyList()
            )
        )

        override suspend fun fetchRemoteAttendance(bearerToken: String): Response<List<AttendanceSyncItemDto>> =
            Response.success(emptyList())
    }

    @Test
    fun `finalizing attendance session marks remaining registered students absent and preserves present timestamps`() = runTest {
        val app = ApplicationProvider.getApplicationContext<SmartAttendanceApp>()
        val appDb = app.container.database
        val appStudentDao = appDb.studentDao()
        val appAttendanceDao = appDb.attendanceRecordDao()
        val appCrypto = app.container.cryptoManager

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val presentUid = "stu-session-present-${System.nanoTime()}"
        val absentUid = "stu-session-absent-${System.nanoTime()}"

        appStudentDao.insertStudentIgnore(
            StudentEntity(
                studentUid = presentUid,
                studentName = "Bilal Khan",
                fatherName = "Tariq Khan",
                className = "Class 3",
                rollNumber = "101-${System.nanoTime()}",
                qrPayload = QrCodeManager.buildStudentQrPayload(presentUid, "Class 3", "101"),
                encryptedFaceEmbedding = appCrypto.encryptEmbedding(FloatArray(256) { 0.4f }),
                faceThumbnailBytes = byteArrayOf(1, 2, 3),
                registeredByAdminUid = "admin-1",
                createdAtEpochMs = 1700000000000L,
                syncStatus = SyncStatus.PENDING.name
            )
        )
        appStudentDao.insertStudentIgnore(
            StudentEntity(
                studentUid = absentUid,
                studentName = "Zara Bibi",
                fatherName = "Noor Bibi",
                className = "Class 3",
                rollNumber = "102-${System.nanoTime()}",
                qrPayload = QrCodeManager.buildStudentQrPayload(absentUid, "Class 3", "102"),
                encryptedFaceEmbedding = appCrypto.encryptEmbedding(FloatArray(256) { 0.5f }),
                faceThumbnailBytes = byteArrayOf(4, 5, 6),
                registeredByAdminUid = "admin-1",
                createdAtEpochMs = 1700000001000L,
                syncStatus = SyncStatus.PENDING.name
            )
        )

        val originalIso = "${today}T08:05:12"
        appAttendanceDao.insertAttendanceAppendOnly(
            AttendanceRecordEntity(
                localUid = "att-present-${System.nanoTime()}",
                studentUid = presentUid,
                studentNameSnapshot = "Bilal Khan",
                fatherNameSnapshot = "Tariq Khan",
                classNameSnapshot = "Class 3",
                rollNumberSnapshot = "101",
                status = AttendanceStatus.PRESENT.name,
                attendanceDate = today,
                markedAtIso = originalIso,
                markedAtEpochMs = 1700000500000L,
                verifiedSimilarity = 0.94f,
                markedByAdminUid = "admin-1",
                syncStatus = SyncStatus.PENDING.name
            )
        )

        val viewModel = MainWorkspaceViewModel(app)
        val initialKey = viewModel.attendanceState.value.scanSessionKey
        viewModel.advanceToNextAutoScan()
        assertNull(viewModel.attendanceState.value.selectedStudent)
        assertFalse(viewModel.attendanceState.value.autoAdvancePending)
        assertEquals(initialKey + 1, viewModel.attendanceState.value.scanSessionKey)

        viewModel.markRemainingStudentsAbsent(adminUid = "admin-1", classFilter = "Class 3")

        var absentRecord = appAttendanceDao.findByStudentAndDate(absentUid, today)
        var attempts = 0
        while (absentRecord == null && attempts < 40) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            absentRecord = appAttendanceDao.findByStudentAndDate(absentUid, today)
            attempts++
        }

        assertEquals(AttendanceStatus.ABSENT.name, absentRecord?.status)
        assertEquals(0f, absentRecord?.verifiedSimilarity)

        val presentRecord = appAttendanceDao.findByStudentAndDate(presentUid, today)
        assertEquals(AttendanceStatus.PRESENT.name, presentRecord?.status)
        assertEquals(originalIso, presentRecord?.markedAtIso)
    }
}
