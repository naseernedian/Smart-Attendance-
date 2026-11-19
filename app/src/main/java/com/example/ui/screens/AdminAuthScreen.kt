package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AdminEntity
import com.example.domain.SchoolConstants
import com.example.ui.components.DocumentCameraCaptureDialog
import com.example.ui.components.LiveFaceCameraPanel
import com.example.ui.theme.VerifiedGreen
import com.example.ui.theme.VerifiedGreenBg
import com.example.ui.viewmodel.AdminAuthUiState
import com.example.ui.viewmodel.AuthStage
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun AdminAuthScreen(
    authViewModel: AuthViewModel
) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by authViewModel.isOnline.collectAsStateWithLifecycle()
    val backendUrl by authViewModel.backendUrl.collectAsStateWithLifecycle()
    var showServerDialog by remember { mutableStateOf(false) }

    if (uiState.stage != AuthStage.LOGIN) {
        BackHandler {
            authViewModel.navigateTo(AuthStage.LOGIN)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Institutional Header (White & Light Green)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = SchoolConstants.SCHOOL_NAME,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "EMIS Code: ${SchoolConstants.EMIS_CODE} • Open User Access",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            onClick = { showServerDialog = true },
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.testTag("server_url_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                    contentDescription = if (isOnline) "Online" else "Offline",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isOnline) "Online" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            uiState.infoMessage?.let { info ->
                Surface(
                    color = VerifiedGreenBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = info,
                        color = VerifiedGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when (uiState.stage) {
                AuthStage.LOGIN -> LoginPanel(
                    uiState = uiState,
                    onSubmit = authViewModel::submitCredentials,
                    onGoRegister = { authViewModel.navigateTo(AuthStage.REGISTER) },
                    onGoReset = { authViewModel.navigateTo(AuthStage.PASSWORD_RESET) }
                )

                AuthStage.LIVE_FACE_VERIFICATION -> AdminLiveFacePanel(
                    uiState = uiState,
                    onFaceCaptured = authViewModel::verifyAdminLiveFace,
                    onBack = { authViewModel.navigateTo(AuthStage.LOGIN) }
                )

                AuthStage.REGISTER -> {
                    val createdProfile = uiState.registeredProfile
                    if (createdProfile != null) {
                        RegisteredUserProfilePanel(
                            profile = createdProfile,
                            onContinueToLogin = authViewModel::dismissRegisteredProfileToLogin
                        )
                    } else {
                        AdminRegisterPanel(
                            authViewModel = authViewModel,
                            uiState = uiState,
                            onBack = { authViewModel.navigateTo(AuthStage.LOGIN) }
                        )
                    }
                }

                AuthStage.PASSWORD_RESET -> AdminPasswordResetPanel(
                    uiState = uiState,
                    onResetPassword = authViewModel::resetPasswordDirect,
                    onBack = { authViewModel.navigateTo(AuthStage.LOGIN) }
                )

                else -> Unit
            }
        }
    }

    if (showServerDialog) {
        var urlInput by remember(backendUrl) { mutableStateOf(backendUrl) }
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("School Backend Server URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Configure the HTTPS/LAN endpoint for the school relational backend API.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Server Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    authViewModel.updateBackendUrl(urlInput)
                    showServerDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LoginPanel(
    uiState: AdminAuthUiState,
    onSubmit: (String, String) -> Unit,
    onGoRegister: () -> Unit,
    onGoReset: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("User Login", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "All registered users can sign in with Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX) and password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filter { ch -> ch.isDigit() }.take(13) },
                label = { Text("Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX)") },
                placeholder = { Text("03XXXXXXXXX or XXXXXXXXXXXXX") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_username_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("toggle_login_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_password_input")
            )

            Button(
                onClick = { onSubmit(username, password) },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("admin_login_button")
            ) {
                Text("Sign In")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onGoReset,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("forgot_password_button")
                ) {
                    Text("Forgot Password?")
                }
                TextButton(
                    onClick = onGoRegister,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("register_admin_button")
                ) {
                    Text("Register New User")
                }
            }
        }
    }
}

@Composable
private fun AdminLiveFacePanel(
    uiState: AdminAuthUiState,
    onFaceCaptured: (Bitmap) -> Unit,
    onBack: () -> Unit
) {
    val admin = uiState.pendingChallengeAdmin
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to login")
                }
                Column {
                    Text("User Live Facial Verification", style = MaterialTheme.typography.titleLarge)
                    if (admin != null) {
                        Text(
                            text = "${admin.fullName} (${admin.designation})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "Live device camera capture is mandatory (supports Back Camera and Front Camera). Gallery images cannot be used.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LiveFaceCameraPanel(
                title = "Live Camera Verification",
                captureButtonText = "Capture Live Face & Sign In",
                defaultUseFrontCamera = false,
                allowCameraSwitch = true,
                isBusy = uiState.isBusy,
                onFaceCaptured = onFaceCaptured
            )
        }
    }
}

@Composable
private fun AdminRegisterPanel(
    authViewModel: AuthViewModel,
    uiState: AdminAuthUiState,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var cnicFrontBytes by remember { mutableStateOf<ByteArray?>(null) }
    var cnicBackBytes by remember { mutableStateOf<ByteArray?>(null) }
    var employmentDocBytes by remember { mutableStateOf<ByteArray?>(null) }
    var employmentMime by remember { mutableStateOf("image/jpeg") }

    var cameraDocTarget by remember { mutableStateOf<String?>(null) }

    val cnicFrontPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { cnicFrontBytes = authViewModel.readBytesFromUri(it) }
    }
    val cnicBackPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { cnicBackBytes = authViewModel.readBytesFromUri(it) }
    }
    val employmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            employmentMime = context.contentResolver.getType(it) ?: "application/octet-stream"
            employmentDocBytes = authViewModel.readBytesFromUri(it)
        }
    }

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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("User Registration", style = MaterialTheme.typography.titleLarge)
            }

            Text(
                text = "Enter your details, CNIC (front & back), and employment document. No picture or approval is required — any registered user can access the app immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = designation,
                onValueChange = { designation = it },
                label = { Text("Designation / Role (e.g., Teacher)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mobile,
                onValueChange = { mobile = it.filter { ch -> ch.isDigit() }.take(11) },
                label = { Text("Mobile Number (03XXXXXXXXX)") },
                placeholder = { Text("03XXXXXXXXX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cnic,
                onValueChange = { cnic = it.filter { ch -> ch.isDigit() }.take(13) },
                label = { Text("Pakistani CNIC (XXXXXXXXXXXXX)") },
                placeholder = { Text("XXXXXXXXXXXXX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 8 chars, letters + digits)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("toggle_register_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input")
            )

            // Verification Documents (CNIC Front, CNIC Back, Government Employment Document)
            DocumentUploadRow(
                label = "a. CNIC Front Side",
                isUploaded = cnicFrontBytes != null,
                onScanCamera = { cameraDocTarget = "CNIC_FRONT" },
                onPickFile = {
                    cnicFrontPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            DocumentUploadRow(
                label = "b. CNIC Back Side",
                isUploaded = cnicBackBytes != null,
                onScanCamera = { cameraDocTarget = "CNIC_BACK" },
                onPickFile = {
                    cnicBackPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            DocumentUploadRow(
                label = "c. Government Employment Verification Doc",
                isUploaded = employmentDocBytes != null,
                onScanCamera = { cameraDocTarget = "EMP_DOC" },
                onPickFile = {
                    employmentPicker.launch(arrayOf("image/*", "application/pdf"))
                }
            )

            Button(
                onClick = {
                    authViewModel.registerAdmin(
                        fullName = fullName,
                        designation = designation,
                        mobileInput = mobile,
                        cnicInput = cnic,
                        emailInput = email,
                        password = password,
                        cnicFrontBytes = cnicFrontBytes,
                        cnicBackBytes = cnicBackBytes,
                        employmentDocBytes = employmentDocBytes,
                        employmentDocMime = employmentMime
                    )
                },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("submit_admin_registration")
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Complete Registration")
            }
        }
    }

    cameraDocTarget?.let { target ->
        val title = when (target) {
            "CNIC_FRONT" -> "Scan CNIC Front Side"
            "CNIC_BACK" -> "Scan CNIC Back Side"
            else -> "Scan Government Employment Document"
        }
        DocumentCameraCaptureDialog(
            title = title,
            onDismiss = { cameraDocTarget = null },
            onDocumentCaptured = { bytes ->
                when (target) {
                    "CNIC_FRONT" -> cnicFrontBytes = bytes
                    "CNIC_BACK" -> cnicBackBytes = bytes
                    else -> {
                        employmentDocBytes = bytes
                        employmentMime = "image/jpeg"
                    }
                }
                cameraDocTarget = null
            }
        )
    }
}

@Composable
private fun DocumentUploadRow(
    label: String,
    isUploaded: Boolean,
    onScanCamera: () -> Unit,
    onPickFile: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isUploaded) Icons.Default.CheckCircle else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (isUploaded) VerifiedGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = if (isUploaded) "Attached" else "Required",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUploaded) VerifiedGreen else MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onScanCamera,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan")
                }
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload")
                }
            }
        }
    }
}

@Composable
private fun AdminPasswordResetPanel(
    uiState: AdminAuthUiState,
    onResetPassword: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Reset Password", style = MaterialTheme.typography.titleLarge)
            }

            Text(
                text = "Enter your registered Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX), then create and confirm your new password.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filter { ch -> ch.isDigit() }.take(13) },
                label = { Text("Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX)") },
                placeholder = { Text("03XXXXXXXXX or XXXXXXXXXXXXX") },
                leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_username_input")
            )

            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("Create New Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { newPasswordVisible = !newPasswordVisible },
                        modifier = Modifier.testTag("toggle_reset_new_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (newPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (newPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_new_password_input")
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm New Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                        modifier = Modifier.testTag("toggle_reset_confirm_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reset_confirm_password_input")
            )

            Button(
                onClick = { onResetPassword(username, newPassword, confirmPassword) },
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("confirm_reset_password_button")
            ) {
                Text("Reset Password")
            }
        }
    }
}

@Composable
private fun RegisteredUserProfilePanel(
    profile: AdminEntity,
    onContinueToLogin: () -> Unit
) {
    val portrait = remember(profile.adminUid) {
        if (profile.faceThumbnailBytes.isEmpty()) {
            null
        } else {
            runCatching {
                BitmapFactory.decodeByteArray(profile.faceThumbnailBytes, 0, profile.faceThumbnailBytes.size)
            }.getOrNull()
        }
    }
    val registeredAtText = remember(profile.createdAtEpochMs) {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(profile.createdAtEpochMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("registered_user_profile_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, VerifiedGreen)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = VerifiedGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Registered User Profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = VerifiedGreen
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (portrait != null) {
                    Image(
                        bitmap = portrait.asImageBitmap(),
                        contentDescription = "${profile.fullName} portrait",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.fullName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        profile.designation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Status: Active (${profile.syncStatus})",
                        style = MaterialTheme.typography.labelMedium,
                        color = VerifiedGreen
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Mobile Number: ${profile.mobileNormalized}", style = MaterialTheme.typography.bodyMedium)
                    Text("CNIC: ${profile.cnicNormalized}", style = MaterialTheme.typography.bodyMedium)
                    Text("Email: ${profile.email}", style = MaterialTheme.typography.bodyMedium)
                    Text("Registered On: $registeredAtText", style = MaterialTheme.typography.bodyMedium)
                    Text("User ID: ${profile.adminUid}", style = MaterialTheme.typography.labelSmall)
                }
            }

            Surface(
                color = VerifiedGreenBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Stored Encrypted Verification Documents:",
                        style = MaterialTheme.typography.labelLarge,
                        color = VerifiedGreen
                    )
                    Text("• CNIC Front: Stored securely", style = MaterialTheme.typography.bodyMedium)
                    Text("• CNIC Back: Stored securely", style = MaterialTheme.typography.bodyMedium)
                    Text("• Government Employment Doc: Stored securely", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = onContinueToLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("proceed_to_login_button")
            ) {
                Text("Proceed to User Login")
            }
        }
    }
}
