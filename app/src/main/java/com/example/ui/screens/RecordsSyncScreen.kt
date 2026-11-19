package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.biometrics.QrCodeManager
import com.example.data.local.AttendanceRecordEntity
import com.example.data.local.StudentEntity
import com.example.domain.AttendanceStatus
import com.example.domain.SchoolConstants
import com.example.ui.components.LiveQrScannerDialog
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.LeaveAmber
import com.example.ui.theme.LeaveAmberBg
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.viewmodel.MainWorkspaceViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsSyncScreen(
    viewModel: MainWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val attendance by viewModel.allAttendance.collectAsStateWithLifecycle()
    val pendingStudents by viewModel.pendingStudentCount.collectAsStateWithLifecycle()
    val pendingAttendance by viewModel.pendingAttendanceCount.collectAsStateWithLifecycle()
    val lastSyncEpoch by viewModel.lastSyncEpoch.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Attendance Ledger, 1 = Class-wise Student Profiles
    var searchQuery by remember { mutableStateOf("") }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }
    var showQrLookup by remember { mutableStateOf(false) }
    var inspectedStudent by remember { mutableStateOf<StudentEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Offline / Online & Sync Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isOnline) VerifiedGreen else LeaveAmber
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (isOnline) "Online — Sync Ready" else "Offline Mode — Local Storage Active",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Pending Upload: $pendingStudents students, $pendingAttendance attendance",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sync_now_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSyncing) "Syncing" else "Sync")
                    }
                }

                if (lastSyncEpoch > 0L) {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastSyncEpoch))
                    Text(
                        text = "Last successful sync: $fmt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                syncMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Permanent Append-Only Notice (Req 13)
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Protected ledger",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Append-Only Protected Attendance Ledger: Deletion is disabled by SQLite/PostgreSQL triggers and DAO constraints.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                modifier = Modifier.testTag("ledger_tab_button")
            ) {
                Text("Attendance (${attendance.size})")
            }
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                modifier = Modifier.testTag("students_tab_button")
            ) {
                Text("Class Profiles (${students.size})")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search name, class, or roll") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = { showQrLookup = true },
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .testTag("records_scan_qr_button")
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Student QR")
            }
        }

        if (selectedTab == 0) {
            val filteredAtt = remember(attendance, searchQuery) {
                val q = searchQuery.trim()
                if (q.isEmpty()) attendance else attendance.filter {
                    it.studentNameSnapshot.contains(q, ignoreCase = true) ||
                        it.classNameSnapshot.contains(q, ignoreCase = true) ||
                        it.rollNumberSnapshot.contains(q, ignoreCase = true) ||
                        it.attendanceDate.contains(q, ignoreCase = true)
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAtt, key = { it.localUid }) { rec ->
                    AttendanceLedgerRow(record = rec)
                }
            }
        } else {
            // Class-wise filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedClassFilter == null,
                        onClick = { selectedClassFilter = null },
                        label = { Text("All (${students.size})") },
                        modifier = Modifier.testTag("class_chip_all")
                    )
                }
                items(SchoolConstants.PRIMARY_CLASSES) { cls ->
                    val count = remember(students, cls) {
                        students.count { it.className == cls }
                    }
                    FilterChip(
                        selected = selectedClassFilter == cls,
                        onClick = {
                            selectedClassFilter = if (selectedClassFilter == cls) null else cls
                        },
                        label = { Text("$cls ($count)") },
                        modifier = Modifier.testTag("class_chip_${cls.lowercase().replace(" ", "_")}")
                    )
                }
            }

            val attendanceByStudent = remember(attendance) {
                attendance.groupBy { it.studentUid }
            }

            val groupedByClass = remember(students, searchQuery, selectedClassFilter) {
                val q = searchQuery.trim()
                val filtered = students.filter { st ->
                    val matchesClass = selectedClassFilter == null || st.className == selectedClassFilter
                    val matchesQuery = q.isEmpty() ||
                        st.studentName.contains(q, ignoreCase = true) ||
                        st.fatherName.contains(q, ignoreCase = true) ||
                        st.className.contains(q, ignoreCase = true) ||
                        st.rollNumber.contains(q, ignoreCase = true)
                    matchesClass && matchesQuery
                }

                val classOrder = SchoolConstants.PRIMARY_CLASSES.withIndex().associate { it.value to it.index }
                filtered
                    .groupBy { it.className }
                    .entries
                    .sortedWith(compareBy({ classOrder[it.key] ?: Int.MAX_VALUE }, { it.key }))
                    .map { (cls, list) ->
                        cls to list.sortedWith(
                            compareBy(
                                { it.rollNumber.toIntOrNull() ?: Int.MAX_VALUE },
                                { it.rollNumber },
                                { it.studentName }
                            )
                        )
                    }
            }

            if (groupedByClass.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = if (students.isEmpty()) {
                            "No student profiles registered yet."
                        } else {
                            "No student profiles match your filter."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedByClass.forEach { (className, classStudents) ->
                        item(key = "header_$className") {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .testTag("class_header_${className.lowercase().replace(" ", "_")}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.School,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = className,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = "${classStudents.size} Student${if (classStudents.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        items(classStudents, key = { it.studentUid }) { st ->
                            val stRecords = attendanceByStudent[st.studentUid].orEmpty()
                            ClassStudentProfileCard(
                                student = st,
                                records = stRecords,
                                onClick = { inspectedStudent = st }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrLookup) {
        LiveQrScannerDialog(
            onDismiss = { showQrLookup = false },
            onQrDetected = { rawQr ->
                showQrLookup = false
                val parsed = QrCodeManager.parseStudentQrPayload(rawQr)
                val match = students.firstOrNull {
                    it.qrPayload == rawQr || (parsed != null && it.studentUid == parsed.studentUid)
                }
                if (match != null) {
                    inspectedStudent = match
                }
            }
        )
    }

    inspectedStudent?.let { student ->
        val studentHistory = remember(attendance, student.studentUid) {
            attendance.filter { it.studentUid == student.studentUid }
        }
        StudentProfileDetailDialog(
            student = student,
            history = studentHistory,
            onDismiss = { inspectedStudent = null }
        )
    }
}

@Composable
private fun ClassStudentProfileCard(
    student: StudentEntity,
    records: List<AttendanceRecordEntity>,
    onClick: () -> Unit
) {
    val thumb = remember(student.studentUid) {
        runCatching {
            BitmapFactory.decodeByteArray(student.faceThumbnailBytes, 0, student.faceThumbnailBytes.size)
        }.getOrNull()
    }
    val pCount = records.count { it.status == AttendanceStatus.PRESENT.name }
    val aCount = records.count { it.status == AttendanceStatus.ABSENT.name }
    val lCount = records.count { it.status == AttendanceStatus.LEAVE.name }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("student_profile_card_${student.className}_${student.rollNumber}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = "${student.studentName} profile portrait",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Father: ${student.fatherName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Roll #${student.rollNumber} • ${student.syncStatus}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    MiniCountPill("P: $pCount", VerifiedGreen, VerifiedGreenBg)
                    MiniCountPill("A: $aCount", AbsentRed, AbsentRedBg)
                    MiniCountPill("L: $lCount", LeaveAmber, LeaveAmberBg)
                }
            }

            Icon(
                Icons.Default.QrCode,
                contentDescription = "Open full student profile and QR",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MiniCountPill(text: String, fgColor: Color, bgColor: Color) {
    Surface(color = bgColor, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = text,
            color = fgColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StudentProfileDetailDialog(
    student: StudentEntity,
    history: List<AttendanceRecordEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val thumb = remember(student.studentUid) {
        runCatching {
            BitmapFactory.decodeByteArray(student.faceThumbnailBytes, 0, student.faceThumbnailBytes.size)
        }.getOrNull()
    }
    val qrBitmap: Bitmap = remember(student.qrPayload) {
        QrCodeManager.generateQrBitmap(student.qrPayload, 360)
    }
    val registeredDate = remember(student.createdAtEpochMs) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(student.createdAtEpochMs))
    }
    val presentCount = history.count { it.status == AttendanceStatus.PRESENT.name }
    val absentCount = history.count { it.status == AttendanceStatus.ABSENT.name }
    val leaveCount = history.count { it.status == AttendanceStatus.LEAVE.name }
    val attendancePct = if (history.isNotEmpty()) ((presentCount * 100f) / history.size).toInt() else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Student Profile — ${student.className}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "${student.studentName} portrait",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.studentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${student.className} • Roll #${student.rollNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(color = VerifiedGreenBg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "Sync: ${student.syncStatus}",
                                color = VerifiedGreen,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("Father Name: ${student.fatherName}", style = MaterialTheme.typography.bodyMedium)
                        Text("Class: ${student.className}", style = MaterialTheme.typography.bodyMedium)
                        Text("Roll Number: ${student.rollNumber}", style = MaterialTheme.typography.bodyMedium)
                        Text("Registered On: $registeredDate", style = MaterialTheme.typography.bodyMedium)
                        Text("Student ID: ${student.studentUid}", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniCountPill("Present: $presentCount", VerifiedGreen, VerifiedGreenBg)
                    MiniCountPill("Absent: $absentCount", AbsentRed, AbsentRedBg)
                    MiniCountPill("Leave: $leaveCount", LeaveAmber, LeaveAmberBg)
                    MiniCountPill("$attendancePct%", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }

                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Student QR Code",
                    modifier = Modifier.size(150.dp)
                )

                OutlinedButton(
                    onClick = {
                        val dir = File(context.filesDir, "qr_cards").apply { mkdirs() }
                        val safeName = student.studentName.replace(Regex("[^A-Za-z0-9]"), "_")
                        val outFile = File(dir, "${safeName}_Roll_${student.rollNumber}_QR.png")
                        FileOutputStream(outFile).use { fos ->
                            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            outFile
                        )
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${SchoolConstants.SCHOOL_NAME} (EMIS ${SchoolConstants.EMIS_CODE}) — ${student.studentName} (${student.className}, Roll #${student.rollNumber})"
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share Student QR Card"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Student QR Card")
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Text(
                    text = "Attendance History (${history.size} records)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (history.isEmpty()) {
                    Text(
                        text = "No attendance recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Start)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        history.take(15).forEach { rec ->
                            val (bg, fg) = when (AttendanceStatus.fromString(rec.status)) {
                                AttendanceStatus.PRESENT -> VerifiedGreenBg to VerifiedGreen
                                AttendanceStatus.ABSENT -> AbsentRedBg to AbsentRed
                                AttendanceStatus.LEAVE -> LeaveAmberBg to LeaveAmber
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${rec.attendanceDate} • ${rec.markedAtIso.substringAfter('T').take(8)}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                MiniCountPill(rec.status, fg, bg)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AttendanceLedgerRow(record: AttendanceRecordEntity) {
    val (bgColor, fgColor) = when (AttendanceStatus.fromString(record.status)) {
        AttendanceStatus.PRESENT -> VerifiedGreenBg to VerifiedGreen
        AttendanceStatus.ABSENT -> AbsentRedBg to AbsentRed
        AttendanceStatus.LEAVE -> LeaveAmberBg to LeaveAmber
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.studentNameSnapshot, style = MaterialTheme.typography.titleMedium)
                Text(
                    "S/O, D/O ${record.fatherNameSnapshot} • ${record.classNameSnapshot} (Roll #${record.rollNumberSnapshot})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${record.attendanceDate} ${record.markedAtIso.substringAfter('T').take(8)} • ${record.syncStatus}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Surface(color = bgColor, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = record.status,
                    color = fgColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
