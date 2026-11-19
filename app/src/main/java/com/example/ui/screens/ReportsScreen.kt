package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.AttendanceStatus
import com.example.domain.ExportFormat
import com.example.domain.ReportPeriod
import com.example.domain.SchoolConstants
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.LeaveAmber
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.viewmodel.MainWorkspaceViewModel

@Composable
fun ReportsScreen(
    viewModel: MainWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.reportsState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshReportPreview()
    }

    val presentCount = state.records.count { it.status == AttendanceStatus.PRESENT.name }
    val absentCount = state.records.count { it.status == AttendanceStatus.ABSENT.name }
    val leaveCount = state.records.count { it.status == AttendanceStatus.LEAVE.name }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ReportPeriod.entries.forEachIndexed { idx, period ->
                SegmentedButton(
                    selected = state.period == period,
                    onClick = { viewModel.setReportPeriod(period) },
                    shape = SegmentedButtonDefaults.itemShape(idx, ReportPeriod.entries.size),
                    modifier = Modifier.testTag("report_period_${period.name.lowercase()}")
                ) {
                    Text(period.label)
                }
            }
        }

        // Date Range Stepper
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.shiftReportAnchor(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                }
                Text(
                    text = state.rangeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { viewModel.shiftReportAnchor(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = state.selectedClass == null,
                    onClick = { viewModel.setReportClassFilter(null) },
                    label = { Text("All Classes") }
                )
            }
            items(SchoolConstants.PRIMARY_CLASSES) { cls ->
                FilterChip(
                    selected = state.selectedClass == cls,
                    onClick = { viewModel.setReportClassFilter(if (state.selectedClass == cls) null else cls) },
                    label = { Text(cls) }
                )
            }
        }

        // Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryBox("Total", state.records.size.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            SummaryBox("PRESENT", presentCount.toString(), VerifiedGreen, Modifier.weight(1f))
            SummaryBox("ABSENT", absentCount.toString(), AbsentRed, Modifier.weight(1f))
            SummaryBox("LEAVE", leaveCount.toString(), LeaveAmber, Modifier.weight(1f))
        }

        // Export Format Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.exportReport(ExportFormat.PDF) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("export_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("PDF")
            }
            Button(
                onClick = { viewModel.exportReport(ExportFormat.XLSX) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("export_xlsx_button")
            ) {
                Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Excel")
            }
            OutlinedButton(
                onClick = { viewModel.exportReport(ExportFormat.CSV) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("export_csv_button")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("CSV")
            }
        }

        state.lastGeneratedFile?.let { gen ->
            Surface(
                color = VerifiedGreenBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.statusMessage ?: gen.file.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VerifiedGreen,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${gen.format.label} • ${gen.recordCount} records",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedGreen
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                gen.file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = gen.format.mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Open or Share ${gen.format.label}"))
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.records.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "No attendance records stored for ${state.rangeLabel}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.records, key = { it.localUid }) { rec ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.studentNameSnapshot, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Father: ${rec.fatherNameSnapshot} • ${rec.classNameSnapshot} #${rec.rollNumberSnapshot}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${rec.attendanceDate} ${rec.markedAtIso.substringAfter('T').take(8)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = rec.status,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = when (AttendanceStatus.fromString(rec.status)) {
                                    AttendanceStatus.PRESENT -> VerifiedGreen
                                    AttendanceStatus.ABSENT -> AbsentRed
                                    AttendanceStatus.LEAVE -> LeaveAmber
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
