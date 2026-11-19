package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.SchoolConstants
import com.example.ui.components.LiveFaceCameraPanel
import com.example.ui.theme.AbsentRed
import com.example.ui.theme.AbsentRedBg
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.viewmodel.MainWorkspaceViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationScreen(
    adminUid: String,
    viewModel: MainWorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.studentRegState.collectAsStateWithLifecycle()

    var studentName by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(SchoolConstants.PRIMARY_CLASSES.first()) }
    var classDropdownExpanded by remember { mutableStateOf(false) }
    var rollNumber by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val registered = state.registeredStudent
        val qrBmp = state.generatedQrBitmap

        if (registered != null && qrBmp != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, VerifiedGreen)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VerifiedGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Student Registered & QR Generated",
                            style = MaterialTheme.typography.titleLarge,
                            color = VerifiedGreen
                        )
                    }

                    Text(registered.studentName, style = MaterialTheme.typography.headlineSmall)
                    Text("Father Name: ${registered.fatherName}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${registered.className} • Roll #${registered.rollNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Image(
                        bitmap = qrBmp.asImageBitmap(),
                        contentDescription = "Unique student QR code",
                        modifier = Modifier
                            .size(210.dp)
                            .padding(8.dp)
                    )

                    Text(
                        text = registered.qrPayload,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val qrDir = File(context.filesDir, "qr_cards").apply { mkdirs() }
                                val outFile = File(qrDir, "QR_${registered.className.replace(" ", "_")}_${registered.rollNumber}.png")
                                FileOutputStream(outFile).use { qrBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        "${registered.studentName} (${registered.className} - Roll ${registered.rollNumber}) QR Card"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Student QR Code"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share QR")
                        }

                        Button(
                            onClick = {
                                studentName = ""
                                fatherName = ""
                                rollNumber = ""
                                viewModel.resetStudentRegistrationForm()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("register_another_student_button")
                        ) {
                            Text("Register Next")
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Student Registration", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "Each student is registered once with live camera capture. Works completely offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Student Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_name_input")
                    )

                    OutlinedTextField(
                        value = fatherName,
                        onValueChange = { fatherName = it },
                        label = { Text("Father Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("father_name_input")
                    )

                    ExposedDropdownMenuBox(
                        expanded = classDropdownExpanded,
                        onExpandedChange = { classDropdownExpanded = !classDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedClass,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Class") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("student_class_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = classDropdownExpanded,
                            onDismissRequest = { classDropdownExpanded = false }
                        ) {
                            SchoolConstants.PRIMARY_CLASSES.forEach { cls ->
                                DropdownMenuItem(
                                    text = { Text(cls) },
                                    onClick = {
                                        selectedClass = cls
                                        classDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = rollNumber,
                        onValueChange = { rollNumber = it },
                        label = { Text("Roll Number") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_roll_input")
                    )
                }
            }

            LiveFaceCameraPanel(
                title = "Live Student Facial Capture (No Gallery)",
                captureButtonText = if (state.capturedFace == null) "Capture Live Student Face" else "Re-capture Student Face",
                defaultUseFrontCamera = false,
                allowCameraSwitch = true,
                isBusy = state.isSaving,
                onFaceCaptured = { bmp -> viewModel.captureStudentLiveFace(bmp) }
            )

            state.errorMessage?.let { err ->
                Surface(
                    color = AbsentRedBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = AbsentRed,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            state.statusMessage?.let { msg ->
                Surface(
                    color = VerifiedGreenBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = VerifiedGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.registerStudent(
                        studentName = studentName,
                        fatherName = fatherName,
                        className = selectedClass,
                        rollNumber = rollNumber,
                        adminUid = adminUid
                    )
                },
                enabled = !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("register_student_submit_button")
            ) {
                Text("Register Student & Generate QR Code", fontWeight = FontWeight.Bold)
            }
        }
    }
}
