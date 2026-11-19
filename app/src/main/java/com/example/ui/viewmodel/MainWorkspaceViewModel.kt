package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SmartAttendanceApp
import com.example.biometrics.QrCodeManager
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.StudentEntity
import com.example.data.sync.SyncWorker
import com.example.domain.AttendanceStatus
import com.example.domain.ExportFormat
import com.example.domain.FaceCaptureOutput
import com.example.domain.FaceMatchResult
import com.example.domain.ReportPeriod
import com.example.domain.SyncStatus
import com.example.reports.GeneratedReportFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class AttendanceWorkspaceState(
    val selectedStudent: StudentEntity? = null,
    val existingRecordForToday: AttendanceRecordEntity? = null,
    val faceMatchResult: FaceMatchResult? = null,
    val isVerifyingFace: Boolean = false,
    val statusBanner: String? = null,
    val isErrorBanner: Boolean = false,
    val autoMarkStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val scanSessionKey: Int = 0,
    val autoAdvancePending: Boolean = false,
    val lastAutoMarkedStudent: StudentEntity? = null,
    val lastAutoMarkedRecord: AttendanceRecordEntity? = null,
    val lastAutoMarkedPercent: Int? = null
)

data class StudentRegUiState(
    val isSaving: Boolean = false,
    val capturedFace: FaceCaptureOutput? = null,
    val registeredStudent: StudentEntity? = null,
    val generatedQrBitmap: Bitmap? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

data class ReportsUiState(
    val period: ReportPeriod = ReportPeriod.DAILY,
    val anchorDateMillis: Long = System.currentTimeMillis(),
    val selectedClass: String? = null,
    val records: List<AttendanceRecordEntity> = emptyList(),
    val rangeLabel: String = "",
    val lastGeneratedFile: GeneratedReportFile? = null,
    val statusMessage: String? = null
)

class MainWorkspaceViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as SmartAttendanceApp).container
    private val studentDao = container.database.studentDao()
    private val attendanceDao = container.database.attendanceRecordDao()
    private val crypto = container.cryptoManager
    private val faceEngine = container.faceEngine
    private val reportGenerator = container.reportGenerator
    private val syncRepository = container.syncRepository
    private val prefs = container.sessionPreferences

    val isOnline: StateFlow<Boolean> = container.connectivityObserver.isOnline

    val students: StateFlow<List<StudentEntity>> = studentDao.observeAllStudents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendance: StateFlow<List<AttendanceRecordEntity>> = attendanceDao.observeAllAttendance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingStudentCount: StateFlow<Int> = studentDao.observePendingStudentCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingAttendanceCount: StateFlow<Int> = attendanceDao.observePendingAttendanceCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lastSyncEpoch: StateFlow<Long> = prefs.lastSyncEpochFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _attendanceState = MutableStateFlow(AttendanceWorkspaceState())
    val attendanceState: StateFlow<AttendanceWorkspaceState> = _attendanceState.asStateFlow()

    private val _studentRegState = MutableStateFlow(StudentRegUiState())
    val studentRegState: StateFlow<StudentRegUiState> = _studentRegState.asStateFlow()

    private val _reportsState = MutableStateFlow(ReportsUiState())
    val reportsState: StateFlow<ReportsUiState> = _reportsState.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        refreshReportPreview()
    }

    // --- STUDENT REGISTRATION ---
    fun captureStudentLiveFace(bitmap: Bitmap) {
        viewModelScope.launch {
            _studentRegState.update { it.copy(errorMessage = null, statusMessage = "Processing live facial features...") }
            val result = faceEngine.extractEmbeddingFromLiveCapture(bitmap)
            if (result.isSuccess) {
                val output = result.getOrThrow()
                _studentRegState.update {
                    it.copy(
                        capturedFace = output,
                        statusMessage = output.qualityNote,
                        errorMessage = null
                    )
                }
            } else {
                _studentRegState.update {
                    it.copy(
                        capturedFace = null,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to extract live face.",
                        statusMessage = null
                    )
                }
            }
        }
    }

    fun registerStudent(
        studentName: String,
        fatherName: String,
        className: String,
        rollNumber: String,
        adminUid: String
    ) {
        val cleanName = studentName.trim()
        val cleanFather = fatherName.trim()
        val cleanRoll = rollNumber.trim()
        if (cleanName.length < 2) {
            _studentRegState.update { it.copy(errorMessage = "Enter Student Name.") }
            return
        }
        if (cleanFather.length < 2) {
            _studentRegState.update { it.copy(errorMessage = "Enter Father Name.") }
            return
        }
        if (className.isBlank()) {
            _studentRegState.update { it.copy(errorMessage = "Select Class.") }
            return
        }
        if (cleanRoll.isBlank()) {
            _studentRegState.update { it.copy(errorMessage = "Enter Roll Number.") }
            return
        }
        val face = _studentRegState.value.capturedFace
        if (face == null) {
            _studentRegState.update { it.copy(errorMessage = "Capture student's live facial image first.") }
            return
        }

        viewModelScope.launch {
            _studentRegState.update { it.copy(isSaving = true, errorMessage = null) }

            // Duplicate check 1: Class + Roll Number
            val existingByRoll = studentDao.findByClassAndRoll(className, cleanRoll)
            if (existingByRoll != null) {
                _studentRegState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Duplicate blocked: ${existingByRoll.studentName} is already registered in $className with Roll #$cleanRoll."
                    )
                }
                return@launch
            }

            // Duplicate check 2: Facial similarity against existing students
            val allExisting = studentDao.getAllStudentsOnce()
            val duplicateFaceStudent = allExisting.firstOrNull { candidate ->
                val ref = runCatching { crypto.decryptEmbedding(candidate.encryptedFaceEmbedding) }.getOrNull()
                if (ref != null) {
                    val cmp = faceEngine.compareEmbeddings(ref, face.embedding)
                    cmp.similarityScore >= (cmp.thresholdUsed + 0.05f)
                } else {
                    false
                }
            }
            if (duplicateFaceStudent != null) {
                _studentRegState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Possible duplicate face: matches ${duplicateFaceStudent.studentName} (${duplicateFaceStudent.className}, Roll ${duplicateFaceStudent.rollNumber})."
                    )
                }
                return@launch
            }

            val studentUid = UUID.randomUUID().toString()
            val qrPayload = QrCodeManager.buildStudentQrPayload(studentUid, className, cleanRoll)
            val encEmbedding = crypto.encryptEmbedding(face.embedding)
            val entity = StudentEntity(
                studentUid = studentUid,
                studentName = cleanName,
                fatherName = cleanFather,
                className = className,
                rollNumber = cleanRoll,
                qrPayload = qrPayload,
                encryptedFaceEmbedding = encEmbedding,
                faceThumbnailBytes = face.thumbnailBytes,
                registeredByAdminUid = adminUid,
                createdAtEpochMs = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING.name
            )
            val insertedRow = studentDao.insertStudentIgnore(entity)
            if (insertedRow == -1L) {
                _studentRegState.update {
                    it.copy(isSaving = false, errorMessage = "Student already registered.")
                }
                return@launch
            }

            val qrBmp = QrCodeManager.generateQrBitmap(qrPayload, 512)
            _studentRegState.update {
                it.copy(
                    isSaving = false,
                    registeredStudent = entity,
                    generatedQrBitmap = qrBmp,
                    statusMessage = "Student registered offline & QR code generated."
                )
            }
            SyncWorker.enqueueImmediateWhenOnline(getApplication())
        }
    }

    fun resetStudentRegistrationForm() {
        _studentRegState.value = StudentRegUiState()
    }

    // --- ATTENDANCE & FACIAL VERIFICATION ---
    fun setAutoMarkStatus(status: AttendanceStatus) {
        _attendanceState.update { it.copy(autoMarkStatus = status) }
    }

    fun startNextAutoScan() {
        val current = _attendanceState.value
        _attendanceState.value = AttendanceWorkspaceState(
            autoMarkStatus = current.autoMarkStatus,
            scanSessionKey = current.scanSessionKey + 1
        )
    }

    fun advanceToNextAutoScan() {
        val current = _attendanceState.value
        _attendanceState.value = current.copy(
            selectedStudent = null,
            existingRecordForToday = null,
            faceMatchResult = null,
            isVerifyingFace = false,
            autoAdvancePending = false,
            scanSessionKey = current.scanSessionKey + 1
        )
    }

    fun selectStudentForAttendance(student: StudentEntity) {
        viewModelScope.launch {
            val today = todayDateString()
            val existing = attendanceDao.findByStudentAndDate(student.studentUid, today)
            val current = _attendanceState.value
            _attendanceState.value = AttendanceWorkspaceState(
                selectedStudent = student,
                existingRecordForToday = existing,
                autoMarkStatus = current.autoMarkStatus,
                scanSessionKey = current.scanSessionKey + 1,
                statusBanner = if (existing != null) {
                    "Attendance already recorded today (${existing.status} at ${existing.markedAtIso.substringAfter('T').take(8)}). Original timestamp preserved."
                } else {
                    "Student profile loaded. Scan live face to automatically verify & mark ${current.autoMarkStatus.name}."
                }
            )
        }
    }

    fun locateStudentByQr(rawQr: String): StudentEntity? {
        val parsed = QrCodeManager.parseStudentQrPayload(rawQr)
        val matched = if (parsed != null) {
            students.value.firstOrNull { it.studentUid == parsed.studentUid || it.qrPayload == rawQr }
        } else {
            students.value.firstOrNull { it.qrPayload == rawQr }
        }
        if (matched != null) {
            selectStudentForAttendance(matched)
        } else {
            _attendanceState.update {
                it.copy(
                    statusBanner = "QR code not recognized for GPS Koz Shinoli (EMIS 61673).",
                    isErrorBanner = true
                )
            }
        }
        return matched
    }

    /**
     * Automatic 1:N live facial recognition & attendance marking:
     * Compares captured face against all registered students, fetches the matched student profile
     * with all details, and automatically marks attendance (preserving any existing record today).
     */
    fun recognizeAndMarkStudentAutomatically(bitmap: Bitmap, adminUid: String) {
        viewModelScope.launch {
            _attendanceState.update {
                it.copy(
                    isVerifyingFace = true,
                    faceMatchResult = null,
                    statusBanner = "Recognizing student face against registered profiles...",
                    isErrorBanner = false
                )
            }
            val capture = faceEngine.extractEmbeddingFromLiveCapture(bitmap)
            if (capture.isFailure) {
                _attendanceState.update {
                    it.copy(
                        isVerifyingFace = false,
                        faceMatchResult = null,
                        statusBanner = capture.exceptionOrNull()?.message ?: "Face capture failed.",
                        isErrorBanner = true
                    )
                }
                return@launch
            }
            val probe = capture.getOrThrow()
            val registeredStudents = studentDao.getAllStudentsOnce()
            if (registeredStudents.isEmpty()) {
                _attendanceState.update {
                    it.copy(
                        isVerifyingFace = false,
                        statusBanner = "No registered students found. Register students first.",
                        isErrorBanner = true
                    )
                }
                return@launch
            }

            var bestMatchedStudent: StudentEntity? = null
            var bestMatchedResult: FaceMatchResult? = null
            var highestNonMatch: FaceMatchResult? = null

            for (candidate in registeredStudents) {
                val ref = runCatching { crypto.decryptEmbedding(candidate.encryptedFaceEmbedding) }.getOrNull()
                    ?: continue
                val cmp = faceEngine.compareEmbeddings(ref, probe.embedding)
                if (cmp.isMatch) {
                    if (bestMatchedResult == null || cmp.similarityScore > bestMatchedResult.similarityScore) {
                        bestMatchedStudent = candidate
                        bestMatchedResult = cmp
                    }
                } else {
                    if (highestNonMatch == null || cmp.similarityScore > highestNonMatch.similarityScore) {
                        highestNonMatch = cmp
                    }
                }
            }

            if (bestMatchedStudent != null && bestMatchedResult != null) {
                val targetStatus = _attendanceState.value.autoMarkStatus
                recordAttendanceInternal(
                    student = bestMatchedStudent,
                    status = targetStatus,
                    match = bestMatchedResult,
                    adminUid = adminUid,
                    triggerAutoAdvance = true
                )
            } else {
                val bestPercent = highestNonMatch?.let { (it.similarityScore.coerceIn(0f, 1f) * 100).toInt() } ?: 0
                _attendanceState.update {
                    it.copy(
                        isVerifyingFace = false,
                        faceMatchResult = highestNonMatch,
                        statusBanner = "NOT VERIFIED — Face did not match any registered student profile (best similarity $bestPercent%). Tap Retake Picture to try again.",
                        isErrorBanner = true
                    )
                }
            }
        }
    }

    fun verifyStudentLiveFace(bitmap: Bitmap, adminUid: String) {
        val student = _attendanceState.value.selectedStudent ?: return
        viewModelScope.launch {
            _attendanceState.update { it.copy(isVerifyingFace = true, statusBanner = null, isErrorBanner = false) }
            val capture = faceEngine.extractEmbeddingFromLiveCapture(bitmap)
            if (capture.isFailure) {
                _attendanceState.update {
                    it.copy(
                        isVerifyingFace = false,
                        faceMatchResult = null,
                        statusBanner = capture.exceptionOrNull()?.message ?: "Face capture failed.",
                        isErrorBanner = true
                    )
                }
                return@launch
            }
            val probe = capture.getOrThrow()
            val ref = runCatching { crypto.decryptEmbedding(student.encryptedFaceEmbedding) }.getOrNull()
            if (ref == null) {
                _attendanceState.update {
                    it.copy(
                        isVerifyingFace = false,
                        statusBanner = "Stored facial template unreadable.",
                        isErrorBanner = true
                    )
                }
                return@launch
            }
            val result = faceEngine.compareEmbeddings(ref, probe.embedding)
            if (result.isMatch) {
                val targetStatus = _attendanceState.value.autoMarkStatus
                recordAttendanceInternal(
                    student = student,
                    status = targetStatus,
                    match = result,
                    adminUid = adminUid,
                    triggerAutoAdvance = true
                )
            } else {
                _attendanceState.update {
                    it.copy(
                        isVerifyingFace = false,
                        faceMatchResult = result,
                        statusBanner = result.message,
                        isErrorBanner = true
                    )
                }
            }
        }
    }

    fun markVerifiedAttendance(status: AttendanceStatus, adminUid: String) {
        val current = _attendanceState.value
        val student = current.selectedStudent ?: return
        val match = current.faceMatchResult
        if (match == null || !match.isMatch) {
            _attendanceState.update {
                it.copy(
                    statusBanner = "Student must pass live facial verification before attendance can be marked.",
                    isErrorBanner = true
                )
            }
            return
        }

        viewModelScope.launch {
            recordAttendanceInternal(
                student = student,
                status = status,
                match = match,
                adminUid = adminUid,
                triggerAutoAdvance = true
            )
        }
    }

    fun markRemainingStudentsAbsent(adminUid: String, classFilter: String? = null) {
        viewModelScope.launch {
            val allStudents = studentDao.getAllStudentsOnce()
            val scopedStudents = if (classFilter.isNullOrBlank()) {
                allStudents
            } else {
                allStudents.filter { it.className == classFilter }
            }
            if (scopedStudents.isEmpty()) {
                _attendanceState.update {
                    it.copy(
                        statusBanner = "No registered students found to finalize attendance.",
                        isErrorBanner = true
                    )
                }
                return@launch
            }

            val nowMs = System.currentTimeMillis()
            val today = todayDateString(nowMs)
            val iso = isoTimestamp(nowMs)
            var absentMarkedCount = 0
            var alreadyMarkedCount = 0

            for (student in scopedStudents) {
                val existing = attendanceDao.findByStudentAndDate(student.studentUid, today)
                if (existing != null) {
                    alreadyMarkedCount++
                    continue
                }
                val record = AttendanceRecordEntity(
                    localUid = UUID.randomUUID().toString(),
                    studentUid = student.studentUid,
                    studentNameSnapshot = student.studentName,
                    fatherNameSnapshot = student.fatherName,
                    classNameSnapshot = student.className,
                    rollNumberSnapshot = student.rollNumber,
                    status = AttendanceStatus.ABSENT.name,
                    attendanceDate = today,
                    markedAtIso = iso,
                    markedAtEpochMs = nowMs,
                    verifiedSimilarity = 0f,
                    markedByAdminUid = adminUid,
                    syncStatus = SyncStatus.PENDING.name
                )
                val inserted = attendanceDao.insertAttendanceAppendOnly(record)
                if (inserted != -1L) {
                    absentMarkedCount++
                } else {
                    alreadyMarkedCount++
                }
            }

            val scopeLabel = if (classFilter.isNullOrBlank()) "All Classes" else classFilter
            val current = _attendanceState.value
            _attendanceState.value = AttendanceWorkspaceState(
                autoMarkStatus = current.autoMarkStatus,
                scanSessionKey = current.scanSessionKey + 1,
                statusBanner = if (absentMarkedCount > 0) {
                    "Session finalized ($scopeLabel): Marked $absentMarkedCount remaining registered student(s) ABSENT; $alreadyMarkedCount existing record(s) preserved."
                } else {
                    "All $alreadyMarkedCount registered student(s) in $scopeLabel already have attendance recorded for $today."
                },
                isErrorBanner = false
            )
            refreshReportPreview()
            if (absentMarkedCount > 0) {
                SyncWorker.enqueueImmediateWhenOnline(getApplication())
            }
        }
    }

    private suspend fun recordAttendanceInternal(
        student: StudentEntity,
        status: AttendanceStatus,
        match: FaceMatchResult,
        adminUid: String,
        triggerAutoAdvance: Boolean
    ) {
        val nowMs = System.currentTimeMillis()
        val today = todayDateString(nowMs)
        val percent = (match.similarityScore.coerceIn(0f, 1f) * 100).toInt()
        val existing = attendanceDao.findByStudentAndDate(student.studentUid, today)
        if (existing != null) {
            _attendanceState.update {
                it.copy(
                    selectedStudent = student,
                    faceMatchResult = match,
                    isVerifyingFace = false,
                    existingRecordForToday = existing,
                    autoAdvancePending = triggerAutoAdvance,
                    lastAutoMarkedStudent = student,
                    lastAutoMarkedRecord = existing,
                    lastAutoMarkedPercent = percent,
                    statusBanner = "VERIFIED: ${student.studentName} (${student.className}, Roll #${student.rollNumber}) — Attendance already recorded today (${existing.status} at ${existing.markedAtIso.substringAfter('T').take(8)}). Original timestamp preserved.",
                    isErrorBanner = false
                )
            }
            return
        }

        val iso = isoTimestamp(nowMs)
        val record = AttendanceRecordEntity(
            localUid = UUID.randomUUID().toString(),
            studentUid = student.studentUid,
            studentNameSnapshot = student.studentName,
            fatherNameSnapshot = student.fatherName,
            classNameSnapshot = student.className,
            rollNumberSnapshot = student.rollNumber,
            status = status.name,
            attendanceDate = today,
            markedAtIso = iso,
            markedAtEpochMs = nowMs,
            verifiedSimilarity = match.similarityScore,
            markedByAdminUid = adminUid,
            syncStatus = SyncStatus.PENDING.name
        )
        val inserted = attendanceDao.insertAttendanceAppendOnly(record)
        if (inserted == -1L) {
            val saved = attendanceDao.findByStudentAndDate(student.studentUid, today)
            _attendanceState.update {
                it.copy(
                    selectedStudent = student,
                    faceMatchResult = match,
                    isVerifyingFace = false,
                    existingRecordForToday = saved,
                    autoAdvancePending = false,
                    statusBanner = "Attendance already exists for $today.",
                    isErrorBanner = true
                )
            }
        } else {
            _attendanceState.update {
                it.copy(
                    selectedStudent = student,
                    faceMatchResult = match,
                    isVerifyingFace = false,
                    existingRecordForToday = record,
                    autoAdvancePending = triggerAutoAdvance,
                    lastAutoMarkedStudent = student,
                    lastAutoMarkedRecord = record,
                    lastAutoMarkedPercent = percent,
                    statusBanner = "Matched ($percent%) & automatically marked ${status.name}: ${student.studentName} (${student.className}, Roll #${student.rollNumber}) at ${iso.substringAfter('T').take(8)}.",
                    isErrorBanner = false
                )
            }
            refreshReportPreview()
            SyncWorker.enqueueImmediateWhenOnline(getApplication())
        }
    }

    fun clearAttendanceSelection() {
        startNextAutoScan()
    }

    // --- SYNC ---
    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            val outcome = syncRepository.syncAll()
            _syncMessage.value = outcome.message
            _isSyncing.value = false
            refreshReportPreview()
        }
    }

    // --- REPORTS ---
    fun setReportPeriod(period: ReportPeriod) {
        _reportsState.update { it.copy(period = period, lastGeneratedFile = null, statusMessage = null) }
        refreshReportPreview()
    }

    fun shiftReportAnchor(step: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = _reportsState.value.anchorDateMillis }
        when (_reportsState.value.period) {
            ReportPeriod.DAILY -> cal.add(Calendar.DAY_OF_YEAR, step)
            ReportPeriod.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, step)
            ReportPeriod.MONTHLY -> cal.add(Calendar.MONTH, step)
        }
        _reportsState.update { it.copy(anchorDateMillis = cal.timeInMillis, lastGeneratedFile = null) }
        refreshReportPreview()
    }

    fun setReportClassFilter(className: String?) {
        _reportsState.update { it.copy(selectedClass = className, lastGeneratedFile = null) }
        refreshReportPreview()
    }

    fun refreshReportPreview() {
        viewModelScope.launch {
            val state = _reportsState.value
            val (start, end, label) = computeDateBounds(state.period, state.anchorDateMillis)
            val raw = attendanceDao.getRecordsInDateRange(start, end)
            val filtered = if (state.selectedClass.isNullOrBlank()) raw else {
                raw.filter { it.classNameSnapshot == state.selectedClass }
            }
            _reportsState.update {
                it.copy(records = filtered, rangeLabel = label)
            }
        }
    }

    fun exportReport(format: ExportFormat) {
        viewModelScope.launch {
            val state = _reportsState.value
            val generated = reportGenerator.generateReport(
                records = state.records,
                period = state.period,
                periodRangeLabel = state.rangeLabel,
                classFilter = state.selectedClass,
                format = format
            )
            _reportsState.update {
                it.copy(
                    lastGeneratedFile = generated,
                    statusMessage = "Exported ${generated.recordCount} records to ${generated.file.name}"
                )
            }
        }
    }

    private fun computeDateBounds(period: ReportPeriod, anchorMs: Long): Triple<String, String, String> {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { timeInMillis = anchorMs }
        return when (period) {
            ReportPeriod.DAILY -> {
                val d = fmt.format(cal.time)
                Triple(d, d, d)
            }
            ReportPeriod.WEEKLY -> {
                val endCal = (cal.clone() as Calendar)
                val startCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }
                val s = fmt.format(startCal.time)
                val e = fmt.format(endCal.time)
                Triple(s, e, "$s to $e")
            }
            ReportPeriod.MONTHLY -> {
                val startCal = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                val endCal = (cal.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                }
                val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
                Triple(fmt.format(startCal.time), fmt.format(endCal.time), monthLabel)
            }
        }
    }

    private fun todayDateString(epochMs: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))

    private fun isoTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(epochMs))
}
