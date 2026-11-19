package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.StudentEntity
import com.example.domain.AttendanceStatus
import com.example.domain.SchoolConstants
import com.example.ui.components.LiveFaceCameraPanel
import com.example.ui.components.LiveQrScannerDialog
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveAmber
import com.example.ui.theme.LeaveAmberBg
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.viewmodel.MainWorkspaceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceScreen(
    adminUid: String,
    viewModel: MainWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val students by viewModel.students.collectAsStateWithLifecycle()
    val allAttendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val state by viewModel.attendanceState.collectAsStateWithLifecycle()
    var showQrScanner by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }

    val todayDate = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val todayRecordByStudent = remember(allAttendance, todayDate) {
        allAttendance.filter { it.attendanceDate == todayDate }.associateBy { it.studentUid }
    }
    val scopedStudents = remember(students, selectedClassFilter) {
        if (selectedClassFilter == null) students else students.filter { it.className == selectedClassFilter }
    }
    val presentTodayCount = remember(scopedStudents, todayRecordByStudent) {
        scopedStudents.count { todayRecordByStudent[it.studentUid]?.status == AttendanceStatus.PRESENT.name }
    }
    val absentTodayCount = remember(scopedStudents, todayRecordByStudent) {
        scopedStudents.count { todayRecordByStudent[it.studentUid]?.status == AttendanceStatus.ABSENT.name }
    }
    val leaveTodayCount = remember(scopedStudents, todayRecordByStudent) {
        scopedStudents.count { todayRecordByStudent[it.studentUid]?.status == AttendanceStatus.LEAVE.name }
    }
    val remainingUnmarkedCount = remember(scopedStudents, todayRecordByStudent) {
        scopedStudents.count { !todayRecordByStudent.containsKey(it.studentUid) }
    }

    LaunchedEffect(state.selectedStudent?.studentUid, state.autoAdvancePending) {
        if (state.autoAdvancePending && state.selectedStudent != null) {
            kotlinx.coroutines.delay(2200L)
            viewModel.advanceToNextAutoScan()
        }
    }

    val selectedStudent = state.selectedStudent

    if (selectedStudent == null || state.autoAdvancePending) {
        val filtered = remember(students, searchQuery, selectedClassFilter) {
            students.filter { st ->
                val matchesClass = selectedClassFilter == null || st.className == selectedClassFilter
                val q = searchQuery.trim()
                val matchesQuery = q.isEmpty() ||
                    st.studentName.contains(q, ignoreCase = true) ||
                    st.fatherName.contains(q, ignoreCase = true) ||
                    st.rollNumber.contains(q, ignoreCase = true)
                matchesClass && matchesQuery
            }
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SessionProgressCard(
                    scopeLabel = selectedClassFilter ?: "All Classes",
                    totalStudents = scopedStudents.size,
                    presentCount = presentTodayCount,
                    absentCount = absentTodayCount,
                    leaveCount = leaveTodayCount,
                    remainingCount = remainingUnmarkedCount,
                    onMarkRemainingAbsent = {
                        viewModel.markRemainingStudentsAbsent(
                            adminUid = adminUid,
                            classFilter = selectedClassFilter
                        )
                    }
                )
            }

            if (state.autoAdvancePending && selectedStudent != null) {
                val matchedHistory = allAttendance.filter { it.studentUid == selectedStudent.studentUid }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auto_advance_confirmation_card"),
                        colors = CardDefaults.cardColors(containerColor = VerifiedGreenBg),
                        border = BorderStroke(1.5.dp, VerifiedGreen)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Automatically moving to next student scanner...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = VerifiedGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.advanceToNextAutoScan() },
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("scan_next_now_button")
                                ) {
                                    Text("Scan Next Now")
                                }
                            }
                            MatchedStudentProfileCard(
                                student = selectedStudent,
                                history = matchedHistory,
                                isVerified = state.faceMatchResult?.isMatch == true,
                                similarityPercent = state.lastAutoMarkedPercent
                            )
                        }
                    }
                }
            } else if (state.lastAutoMarkedStudent != null && state.lastAutoMarkedRecord != null) {
                val prevStudent = state.lastAutoMarkedStudent!!
                val prevRecord = state.lastAutoMarkedRecord!!
                item {
                    Surface(
                        color = VerifiedGreenBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, VerifiedGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("last_auto_marked_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VerifiedGreen
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Last Marked: ${prevStudent.studentName} (${prevStudent.className}, Roll #${prevStudent.rollNumber})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = VerifiedGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Status: ${prevRecord.status} at ${prevRecord.markedAtIso.substringAfter('T').take(8)} • Ready for next student",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Automatic Student Face Scan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Scan any student to automatically recognize their face, fetch profile details, mark attendance, and advance automatically to the next student.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = "Auto-Mark Status:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        AutoMarkStatusChips(
                            selectedStatus = state.autoMarkStatus,
                            onSelectStatus = { viewModel.setAutoMarkStatus(it) }
                        )

                        LiveFaceCameraPanel(
                            title = "Live Face Scanner (Auto-Match)",
                            captureButtonText = "Scan Face & Auto-Mark",
                            defaultUseFrontCamera = false,
                            allowCameraSwitch = true,
                            isBusy = state.isVerifyingFace || state.autoAdvancePending,
                            resetTrigger = state.scanSessionKey,
                            onFaceCaptured = { bmp ->
                                viewModel.recognizeAndMarkStudentAutomatically(bmp, adminUid)
                            }
                        )
                    }
                }
            }

            state.statusBanner?.let { msg ->
                item {
                    StatusBannerCard(
                        message = msg,
                        isError = state.isErrorBanner,
                        tag = "verification_status_banner"
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("scan_student_qr_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Or Scan Student QR Code")
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Registered Students (${students.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Student Name or Roll Number") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("attendance_student_search")
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedClassFilter == null,
                                onClick = { selectedClassFilter = null },
                                label = { Text("All Classes") }
                            )
                        }
                        items(SchoolConstants.PRIMARY_CLASSES) { cls ->
                            FilterChip(
                                selected = selectedClassFilter == cls,
                                onClick = {
                                    selectedClassFilter = if (selectedClassFilter == cls) null else cls
                                },
                                label = { Text(cls) }
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (students.isEmpty()) {
                                    "No students registered yet. Open 'Register' tab to enroll students."
                                } else {
                                    "No matching students found."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.studentUid }) { student ->
                    StudentSelectorCard(
                        student = student,
                        todayStatus = todayRecordByStudent[student.studentUid]?.status,
                        onClick = { viewModel.selectStudentForAttendance(student) }
                    )
                }
            }
        }
    } else {
        BackHandler {
            viewModel.startNextAutoScan()
        }

        val studentAttendanceHistory = remember(allAttendance, selectedStudent.studentUid) {
            allAttendance.filter { it.studentUid == selectedStudent.studentUid }
        }

        // Manual / QR Selected Student Profile + Live Facial Check
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startNextAutoScan() },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("scan_next_student_button")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Next Student")
                }
                OutlinedButton(
                    onClick = { viewModel.clearAttendanceSelection() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("back_to_student_list_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                    Spacer(Modifier.width(4.dp))
                    Text("List")
                }
            }

            // Full Matched Student Profile Card
            MatchedStudentProfileCard(
                student = selectedStudent,
                history = studentAttendanceHistory,
                isVerified = state.faceMatchResult?.isMatch == true,
                similarityPercent = state.faceMatchResult?.let { (it.similarityScore * 100).toInt() }
            )

            state.statusBanner?.let { msg ->
                StatusBannerCard(
                    message = msg,
                    isError = state.isErrorBanner,
                    tag = "verification_status_banner"
                )
            }

            val existing = state.existingRecordForToday
            if (existing != null) {
                val (badgeBg, badgeFg) = when (AttendanceStatus.fromString(existing.status)) {
                    AttendanceStatus.PRESENT -> VerifiedGreenBg to VerifiedGreen
                    AttendanceStatus.ABSENT -> AbsentRedBg to AbsentRed
                    AttendanceStatus.LEAVE -> LeaveAmberBg to LeaveAmber
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recorded_attendance_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = VerifiedGreen
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Attendance Recorded",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(color = badgeBg, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = existing.status,
                                    color = badgeFg,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text("Date: ${existing.attendanceDate}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Time: ${existing.markedAtIso.substringAfter('T').take(8)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Match Confidence: ${(existing.verifiedSimilarity * 100).toInt()}% • Sync: ${existing.syncStatus}",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else {
                Text(
                    text = "Auto-Mark Status on Face Match:",
                    style = MaterialTheme.typography.labelLarge
                )
                AutoMarkStatusChips(
                    selectedStatus = state.autoMarkStatus,
                    onSelectStatus = { viewModel.setAutoMarkStatus(it) }
                )

                LiveFaceCameraPanel(
                    title = "Student Live Facial Verification",
                    captureButtonText = "Capture Live Face & Auto-Mark",
                    defaultUseFrontCamera = false,
                    allowCameraSwitch = true,
                    isBusy = state.isVerifyingFace,
                    resetTrigger = state.scanSessionKey,
                    onFaceCaptured = { bmp -> viewModel.verifyStudentLiveFace(bmp, adminUid) }
                )

                val isVerified = state.faceMatchResult?.isMatch == true
                Text(
                    text = if (isVerified) {
                        "Identity VERIFIED. Mark attendance status below:"
                    } else {
                        "Manual status buttons unlock only after live facial VERIFIED status (or match marks automatically)."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isVerified) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.markVerifiedAttendance(AttendanceStatus.PRESENT, adminUid) },
                        enabled = isVerified,
                        colors = ButtonDefaults.buttonColors(containerColor = VerifiedGreen),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("mark_present_button")
                    ) {
                        Text("PRESENT", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.markVerifiedAttendance(AttendanceStatus.ABSENT, adminUid) },
                        enabled = isVerified,
                        colors = ButtonDefaults.buttonColors(containerColor = AbsentRed),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("mark_absent_button")
                    ) {
                        Text("ABSENT", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.markVerifiedAttendance(AttendanceStatus.LEAVE, adminUid) },
                        enabled = isVerified,
                        colors = ButtonDefaults.buttonColors(containerColor = LeaveAmber),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("mark_leave_button")
                    ) {
                        Text("LEAVE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        LiveQrScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrDetected = { qr ->
                showQrScanner = false
                viewModel.locateStudentByQr(qr)
            }
        )
    }
}

@Composable
private fun SessionProgressCard(
    scopeLabel: String,
    totalStudents: Int,
    presentCount: Int,
    absentCount: Int,
    leaveCount: Int,
    remainingCount: Int,
    onMarkRemainingAbsent: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_session_progress_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Today's Attendance Session • $scopeLabel ($totalStudents Registered)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProfileStatChip("Present", presentCount, VerifiedGreen, VerifiedGreenBg, Modifier.weight(1f))
                ProfileStatChip("Absent", absentCount, AbsentRed, AbsentRedBg, Modifier.weight(1f))
                ProfileStatChip("Leave", leaveCount, LeaveAmber, LeaveAmberBg, Modifier.weight(1f))
                ProfileStatChip("Remaining", remainingCount, AbsentRed, MaterialTheme.colorScheme.surface, Modifier.weight(1f))
            }
            if (remainingCount > 0) {
                Button(
                    onClick = onMarkRemainingAbsent,
                    colors = ButtonDefaults.buttonColors(containerColor = AbsentRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("mark_remaining_absent_button")
                ) {
                    Text(
                        text = "Finish Attendance • Mark Remaining ($remainingCount) Absent",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoMarkStatusChips(
    selectedStatus: AttendanceStatus,
    onSelectStatus: (AttendanceStatus) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AttendanceStatus.entries.forEach { status ->
            val selected = selectedStatus == status
            val accent = when (status) {
                AttendanceStatus.PRESENT -> VerifiedGreen
                AttendanceStatus.ABSENT -> AbsentRed
                AttendanceStatus.LEAVE -> LeaveAmber
            }
            val bg = when (status) {
                AttendanceStatus.PRESENT -> VerifiedGreenBg
                AttendanceStatus.ABSENT -> AbsentRedBg
                AttendanceStatus.LEAVE -> LeaveAmberBg
            }
            FilterChip(
                selected = selected,
                onClick = { onSelectStatus(status) },
                label = {
                    Text(
                        text = status.name,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = bg,
                    selectedLabelColor = accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("auto_status_${status.name.lowercase()}")
            )
        }
    }
}

@Composable
private fun StatusBannerCard(
    message: String,
    isError: Boolean,
    tag: String
) {
    Surface(
        color = if (isError) AbsentRedBg else VerifiedGreenBg,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Text(
            text = message,
            color = if (isError) AbsentRed else VerifiedGreen,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun StudentSelectorCard(
    student: StudentEntity,
    todayStatus: String?,
    onClick: () -> Unit
) {
    val thumb = remember(student.studentUid) {
        runCatching {
            BitmapFactory.decodeByteArray(student.faceThumbnailBytes, 0, student.faceThumbnailBytes.size)
        }.getOrNull()
    }
    val (statusLabel, statusBg, statusFg) = when (todayStatus) {
        AttendanceStatus.PRESENT.name -> Triple("PRESENT", VerifiedGreenBg, VerifiedGreen)
        AttendanceStatus.ABSENT.name -> Triple("ABSENT", AbsentRedBg, AbsentRed)
        AttendanceStatus.LEAVE.name -> Triple("LEAVE", LeaveAmberBg, LeaveAmber)
        else -> Triple("UNMARKED (ABSENT IF SKIPPED)", MaterialTheme.colorScheme.surfaceVariant, AbsentRed)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_card_${student.rollNumber}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "${student.studentName} photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(student.studentName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "S/O, D/O ${student.fatherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${student.className} • Roll #${student.rollNumber}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = statusLabel,
                        color = statusFg,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchedStudentProfileCard(
    student: StudentEntity,
    history: List<AttendanceRecordEntity>,
    isVerified: Boolean,
    similarityPercent: Int?
) {
    val thumb = remember(student.studentUid) {
        runCatching {
            BitmapFactory.decodeByteArray(student.faceThumbnailBytes, 0, student.faceThumbnailBytes.size)
        }.getOrNull()
    }
    val registeredDate = remember(student.createdAtEpochMs) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(student.createdAtEpochMs))
    }
    val presentCount = history.count { it.status == AttendanceStatus.PRESENT.name }
    val absentCount = history.count { it.status == AttendanceStatus.ABSENT.name }
    val leaveCount = history.count { it.status == AttendanceStatus.LEAVE.name }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("matched_student_profile_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isVerified) 2.dp else 1.dp,
            color = if (isVerified) VerifiedGreen else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "${student.studentName} reference portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (isVerified) {
                        Surface(
                            color = VerifiedGreenBg,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("verified_status_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Verified,
                                    contentDescription = "Verified",
                                    tint = VerifiedGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "VERIFIED${similarityPercent?.let { " ($it%)" } ?: ""}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = VerifiedGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = student.studentName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Father Name: ${student.fatherName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${student.className} • Roll #${student.rollNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Profile Details • Registered: $registeredDate • Sync: ${student.syncStatus}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "QR Code ID: ${student.qrPayload}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileStatChip("Present", presentCount, VerifiedGreen, VerifiedGreenBg, Modifier.weight(1f))
                ProfileStatChip("Absent", absentCount, AbsentRed, AbsentRedBg, Modifier.weight(1f))
                ProfileStatChip("Leave", leaveCount, LeaveAmber, LeaveAmberBg, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProfileStatChip(
    label: String,
    count: Int,
    fgColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                color = fgColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = fgColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
