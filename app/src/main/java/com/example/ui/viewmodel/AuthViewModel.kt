package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SmartAttendanceApp
import com.example.data.local.AdminEntity
import com.example.data.remote.AdminFaceVerifyRequest
import com.example.data.remote.AdminLoginRequest
import com.example.data.remote.AdminRegistrationRequest
import com.example.data.remote.AttendanceApiService
import com.example.data.remote.DirectPasswordResetRequestDto
import com.example.domain.AdminAuthorizationStatus
import com.example.domain.SchoolConstants
import com.example.domain.SyncStatus
import com.example.domain.UsernameValidationResult
import com.example.domain.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AuthStage {
    LOADING,
    LOGIN,
    REGISTER,
    PASSWORD_RESET,
    LIVE_FACE_VERIFICATION,
    AUTHENTICATED
}

data class AdminAuthUiState(
    val stage: AuthStage = AuthStage.LOADING,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val pendingChallengeAdmin: AdminEntity? = null,
    val pendingServerChallengeToken: String? = null,
    val authenticatedAdmin: AdminEntity? = null,
    val registeredProfile: AdminEntity? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as SmartAttendanceApp).container
    private val adminDao = container.database.adminDao()
    private val crypto = container.cryptoManager
    private val faceEngine = container.faceEngine
    private val prefs = container.sessionPreferences
    private val connectivity = container.connectivityObserver

    private val _uiState = MutableStateFlow(AdminAuthUiState())
    val uiState: StateFlow<AdminAuthUiState> = _uiState.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivity.isOnline
    val backendUrl: StateFlow<String> = prefs.backendBaseUrlFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "http://10.0.2.2:8000/"
    )
    val localAdmins: StateFlow<List<AdminEntity>> = adminDao.observeAllAdmins().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun finishSplashLoading() {
        _uiState.update {
            if (it.stage == AuthStage.LOADING) it.copy(stage = AuthStage.LOGIN) else it
        }
    }

    fun navigateTo(stage: AuthStage) {
        _uiState.update {
            it.copy(
                stage = stage,
                registeredProfile = null,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun dismissRegisteredProfileToLogin() {
        _uiState.update {
            it.copy(
                stage = AuthStage.LOGIN,
                registeredProfile = null,
                errorMessage = null,
                infoMessage = "Profile saved. Sign in with your Mobile (03XXXXXXXXX) or CNIC (XXXXXXXXXXXXX)."
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun updateBackendUrl(newUrl: String) {
        viewModelScope.launch {
            prefs.setBackendBaseUrl(newUrl)
            _uiState.update { it.copy(infoMessage = "Server URL updated.") }
        }
    }

    fun submitCredentials(usernameInput: String, password: String) {
        val parsed = Validators.parseAdminUsername(usernameInput)
        val lookupKey = when (parsed) {
            is UsernameValidationResult.ValidCnic -> parsed.cnic
            is UsernameValidationResult.ValidMobile -> parsed.mobile
            is UsernameValidationResult.Invalid -> {
                _uiState.update { it.copy(errorMessage = parsed.message) }
                return
            }
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your password.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }
            if (connectivity.checkCurrentConnectivity()) {
                try {
                    val api = AttendanceApiService.create(prefs.getBackendBaseUrl())
                    val response = api.loginAdmin(AdminLoginRequest(username = lookupKey, password = password))
                    if (response.isSuccessful) {
                        val body = response.body()!!
                        val dto = body.admin
                        if (dto == null) {
                            _uiState.update {
                                it.copy(isBusy = false, errorMessage = "Incomplete server auth response.")
                            }
                            return@launch
                        }
                        val (salt, hash) = crypto.hashPassword(password)
                        val encEmb = if (dto.faceEmbedding.isNotEmpty()) {
                            crypto.encryptEmbedding(dto.faceEmbedding.toFloatArray())
                        } else {
                            ByteArray(0)
                        }
                        val thumbBytes = if (dto.faceThumbnailB64.isNotBlank()) {
                            runCatching { Base64.decode(dto.faceThumbnailB64, Base64.NO_WRAP) }
                                .getOrDefault(ByteArray(0))
                        } else {
                            ByteArray(0)
                        }
                        val existingLocal = adminDao.findByUid(dto.adminUid)
                        val adminEntity = AdminEntity(
                            adminUid = dto.adminUid,
                            fullName = dto.fullName,
                            designation = dto.designation,
                            mobileNormalized = dto.mobileNormalized,
                            cnicNormalized = dto.cnicNormalized,
                            cnicFormatted = dto.cnicFormatted,
                            email = dto.email,
                            passwordSaltB64 = salt,
                            passwordHashB64 = hash,
                            encryptedFaceEmbedding = encEmb,
                            faceThumbnailBytes = thumbBytes,
                            cnicFrontDocPath = existingLocal?.cnicFrontDocPath ?: "",
                            cnicBackDocPath = existingLocal?.cnicBackDocPath ?: "",
                            employmentDocPath = existingLocal?.employmentDocPath ?: "",
                            authorizationStatus = AdminAuthorizationStatus.APPROVED.name,
                            authorizedBy = "OPEN_ACCESS",
                            createdAtEpochMs = existingLocal?.createdAtEpochMs ?: System.currentTimeMillis(),
                            syncStatus = SyncStatus.SYNCED.name
                        )
                        adminDao.upsertAdmin(adminEntity)
                        if (dto.faceEmbedding.isEmpty() || !body.accessToken.isNullOrBlank()) {
                            body.accessToken?.let { jwt ->
                                prefs.saveEncryptedJwt(jwt, adminEntity.adminUid)
                            }
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    stage = AuthStage.AUTHENTICATED,
                                    authenticatedAdmin = adminEntity,
                                    pendingChallengeAdmin = null,
                                    pendingServerChallengeToken = null,
                                    errorMessage = null,
                                    infoMessage = null
                                )
                            }
                        } else {
                            if (body.challengeToken.isNullOrBlank()) {
                                _uiState.update {
                                    it.copy(isBusy = false, errorMessage = "Incomplete server auth response.")
                                }
                                return@launch
                            }
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    stage = AuthStage.LIVE_FACE_VERIFICATION,
                                    pendingChallengeAdmin = adminEntity,
                                    pendingServerChallengeToken = body.challengeToken,
                                    infoMessage = "Credentials verified. Complete live facial verification to unlock."
                                )
                            }
                        }
                        return@launch
                    } else if (response.code() in 401..403) {
                        val errBody = response.errorBody()?.string().orEmpty()
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = extractServerDetail(errBody)
                                    ?: "Invalid username or password."
                            )
                        }
                        return@launch
                    }
                } catch (_: Throwable) {
                    // Server unreachable: fall through to local offline credential verification
                }
            }

            // Offline credential verification against locally registered users
            val localAdmin = adminDao.findByCnicOrMobile(lookupKey)
            if (localAdmin == null) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "No account found locally for this Mobile/CNIC. Register a user account or connect to server."
                    )
                }
                return@launch
            }
            val passwordOk = crypto.verifyPassword(password, localAdmin.passwordSaltB64, localAdmin.passwordHashB64)
            if (!passwordOk) {
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = "Incorrect password.")
                }
                return@launch
            }
            if (localAdmin.encryptedFaceEmbedding.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        stage = AuthStage.AUTHENTICATED,
                        authenticatedAdmin = localAdmin,
                        pendingChallengeAdmin = null,
                        pendingServerChallengeToken = null,
                        errorMessage = null,
                        infoMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        stage = AuthStage.LIVE_FACE_VERIFICATION,
                        pendingChallengeAdmin = localAdmin,
                        pendingServerChallengeToken = null,
                        infoMessage = "Offline credentials verified. Complete live facial verification."
                    )
                }
            }
        }
    }

    fun verifyAdminLiveFace(capturedBitmap: Bitmap) {
        val targetAdmin = _uiState.value.pendingChallengeAdmin ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null) }
            val extractResult = faceEngine.extractEmbeddingFromLiveCapture(capturedBitmap)
            if (extractResult.isFailure) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = extractResult.exceptionOrNull()?.message
                            ?: "Could not capture clear frontal face."
                    )
                }
                return@launch
            }
            val liveOutput = extractResult.getOrThrow()
            val refEmbedding = runCatching { crypto.decryptEmbedding(targetAdmin.encryptedFaceEmbedding) }
                .getOrNull()
            if (refEmbedding == null) {
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = "Stored admin biometric reference could not be decrypted.")
                }
                return@launch
            }

            val match = faceEngine.compareEmbeddings(refEmbedding, liveOutput.embedding)
            if (!match.isMatch) {
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = match.message)
                }
                return@launch
            }

            // If we have an online challenge token, confirm live face with server too to get JWT
            val challengeToken = _uiState.value.pendingServerChallengeToken
            if (!challengeToken.isNullOrBlank() && connectivity.checkCurrentConnectivity()) {
                try {
                    val api = AttendanceApiService.create(prefs.getBackendBaseUrl())
                    val resp = api.verifyAdminFace(
                        AdminFaceVerifyRequest(
                            challengeToken = challengeToken,
                            liveFaceEmbedding = liveOutput.embedding.toList()
                        )
                    )
                    if (resp.isSuccessful && resp.body()?.verified == true) {
                        resp.body()?.accessToken?.let { jwt ->
                            prefs.saveEncryptedJwt(jwt, targetAdmin.adminUid)
                        }
                    } else if (resp.code() == 401 || resp.code() == 403 || resp.body()?.verified == false) {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = resp.body()?.message ?: "Server rejected live facial verification."
                            )
                        }
                        return@launch
                    }
                } catch (_: Throwable) {
                    // Local match already succeeded; continue offline
                }
            }

            _uiState.update {
                it.copy(
                    isBusy = false,
                    stage = AuthStage.AUTHENTICATED,
                    authenticatedAdmin = targetAdmin,
                    pendingChallengeAdmin = null,
                    pendingServerChallengeToken = null,
                    errorMessage = null,
                    infoMessage = null
                )
            }
        }
    }

    fun registerAdmin(
        fullName: String,
        designation: String,
        mobileInput: String,
        cnicInput: String,
        emailInput: String,
        password: String,
        cnicFrontBytes: ByteArray?,
        cnicBackBytes: ByteArray?,
        employmentDocBytes: ByteArray?,
        employmentDocMime: String
    ) {
        if (fullName.trim().length < 3) {
            _uiState.update { it.copy(errorMessage = "Enter full name.") }
            return
        }
        if (designation.trim().isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter official designation (e.g., Head Teacher).") }
            return
        }
        val mobile = Validators.validateMobile(mobileInput)
        if (mobile == null) {
            _uiState.update { it.copy(errorMessage = "Enter valid mobile number (03XXXXXXXXX).") }
            return
        }
        val cnic = Validators.validateCnic(cnicInput)
        if (cnic == null) {
            _uiState.update { it.copy(errorMessage = "CNIC must be 13 digits (XXXXXXXXXXXXX).") }
            return
        }
        if (!Validators.isValidEmail(emailInput)) {
            _uiState.update { it.copy(errorMessage = "Enter valid email address.") }
            return
        }
        Validators.validatePassword(password)?.let { pwErr ->
            _uiState.update { it.copy(errorMessage = pwErr) }
            return
        }
        if (cnicFrontBytes == null || cnicBackBytes == null || employmentDocBytes == null) {
            _uiState.update {
                it.copy(errorMessage = "CNIC Front, CNIC Back, and Government Employment document are all required.")
            }
            return
        }

        val normalizedEmail = emailInput.trim().lowercase()

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }

            val existingDuplicate = adminDao.findByCnicOrMobile(cnic)
                ?: adminDao.findByCnicOrMobile(mobile)
                ?: adminDao.findByEmail(normalizedEmail)
            if (existingDuplicate != null) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "A user with this CNIC, mobile number, or email is already registered on this device."
                    )
                }
                return@launch
            }

            val adminUid = UUID.randomUUID().toString()
            val (salt, hash) = crypto.hashPassword(password)

            val frontPath = withContext(Dispatchers.IO) {
                crypto.saveEncryptedDocument("${adminUid}_cnic_front", cnicFrontBytes)
            }
            val backPath = withContext(Dispatchers.IO) {
                crypto.saveEncryptedDocument("${adminUid}_cnic_back", cnicBackBytes)
            }
            val empPath = withContext(Dispatchers.IO) {
                crypto.saveEncryptedDocument("${adminUid}_employment_doc", employmentDocBytes)
            }

            var syncStatus = SyncStatus.PENDING.name
            var infoMsg = "Registration complete (saved offline). Sign in with $mobile or $cnic."

            if (connectivity.checkCurrentConnectivity()) {
                try {
                    val api = AttendanceApiService.create(prefs.getBackendBaseUrl())
                    val req = AdminRegistrationRequest(
                        adminUid = adminUid,
                        fullName = fullName.trim(),
                        designation = designation.trim(),
                        mobileNormalized = mobile,
                        cnicNormalized = cnic,
                        cnicFormatted = cnic,
                        email = normalizedEmail,
                        password = password,
                        faceEmbedding = emptyList(),
                        faceThumbnailB64 = "",
                        cnicFrontB64 = Base64.encodeToString(cnicFrontBytes, Base64.NO_WRAP),
                        cnicBackB64 = Base64.encodeToString(cnicBackBytes, Base64.NO_WRAP),
                        employmentDocB64 = Base64.encodeToString(employmentDocBytes, Base64.NO_WRAP),
                        employmentDocMime = employmentDocMime
                    )
                    val resp = api.registerAdmin(req)
                    if (resp.isSuccessful) {
                        syncStatus = SyncStatus.SYNCED.name
                        infoMsg = resp.body()?.message ?: "Registration complete. You can sign in immediately."
                    } else {
                        val err = extractServerDetail(resp.errorBody()?.string().orEmpty())
                            ?: "Server rejected registration (${resp.code()})."
                        _uiState.update { it.copy(isBusy = false, errorMessage = err) }
                        return@launch
                    }
                } catch (_: Throwable) {
                    infoMsg = "Registration complete (offline mode). You can sign in immediately."
                }
            }

            val entity = AdminEntity(
                adminUid = adminUid,
                fullName = fullName.trim(),
                designation = designation.trim(),
                mobileNormalized = mobile,
                cnicNormalized = cnic,
                cnicFormatted = cnic,
                email = normalizedEmail,
                passwordSaltB64 = salt,
                passwordHashB64 = hash,
                encryptedFaceEmbedding = ByteArray(0),
                faceThumbnailBytes = ByteArray(0),
                cnicFrontDocPath = frontPath,
                cnicBackDocPath = backPath,
                employmentDocPath = empPath,
                authorizationStatus = AdminAuthorizationStatus.APPROVED.name,
                authorizedBy = "OPEN_ACCESS",
                createdAtEpochMs = System.currentTimeMillis(),
                syncStatus = syncStatus
            )
            adminDao.upsertAdmin(entity)

            _uiState.update {
                it.copy(
                    isBusy = false,
                    registeredProfile = entity,
                    infoMessage = infoMsg
                )
            }
        }
    }

    fun resetPasswordDirect(
        usernameInput: String,
        newPassword: String,
        confirmPassword: String
    ) {
        val parsed = Validators.parseAdminUsername(usernameInput)
        val lookupKey = when (parsed) {
            is UsernameValidationResult.ValidCnic -> parsed.cnic
            is UsernameValidationResult.ValidMobile -> parsed.mobile
            is UsernameValidationResult.Invalid -> {
                _uiState.update { it.copy(errorMessage = parsed.message) }
                return
            }
        }
        Validators.validatePassword(newPassword)?.let { err ->
            _uiState.update { it.copy(errorMessage = err) }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Create New Password and Confirm New Password must match.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }
            val localAdmin = adminDao.findByCnicOrMobile(lookupKey)
            var updatedOnServer = false

            if (connectivity.checkCurrentConnectivity()) {
                try {
                    val api = AttendanceApiService.create(prefs.getBackendBaseUrl())
                    val resp = api.resetPasswordDirect(
                        DirectPasswordResetRequestDto(
                            username = lookupKey,
                            newPassword = newPassword
                        )
                    )
                    if (resp.isSuccessful) {
                        updatedOnServer = true
                    } else if (resp.code() == 404 && localAdmin == null) {
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                errorMessage = "No registered account found for this Mobile or CNIC."
                            )
                        }
                        return@launch
                    } else if (!resp.isSuccessful && localAdmin == null) {
                        val detail = extractServerDetail(resp.errorBody()?.string().orEmpty())
                            ?: "Password reset failed (${resp.code()})."
                        _uiState.update { it.copy(isBusy = false, errorMessage = detail) }
                        return@launch
                    }
                } catch (_: Throwable) {
                    // Server unreachable: allow local offline password reset if user exists locally
                }
            }

            if (localAdmin == null && !updatedOnServer) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "No account found locally for this Mobile or CNIC."
                    )
                }
                return@launch
            }

            if (localAdmin != null) {
                val (salt, hash) = crypto.hashPassword(newPassword)
                adminDao.updatePasswordVerifier(localAdmin.adminUid, salt, hash)
            }

            _uiState.update {
                it.copy(
                    isBusy = false,
                    stage = AuthStage.LOGIN,
                    errorMessage = null,
                    infoMessage = "Password reset successfully. Sign in with your new password."
                )
            }
        }
    }

    fun readBytesFromUri(uri: Uri): ByteArray? = runCatching {
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    fun logout() {
        viewModelScope.launch {
            prefs.clearJwt()
            _uiState.value = AdminAuthUiState(stage = AuthStage.LOGIN)
        }
    }

    private fun extractServerDetail(json: String): String? {
        val match = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(json)
        return match?.groupValues?.getOrNull(1)
    }
}
