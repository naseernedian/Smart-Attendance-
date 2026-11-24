package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_attendance_prefs")

class SessionPreferences(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    companion object {
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_base_url")
        private val KEY_ENC_JWT = stringPreferencesKey("enc_auth_jwt")
        private val KEY_LAST_SYNC_EPOCH = longPreferencesKey("last_sync_epoch")
        private val KEY_LAST_ADMIN_UID = stringPreferencesKey("last_admin_uid")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_login_enabled")
        private val KEY_BIOMETRIC_ADMIN_UID = stringPreferencesKey("biometric_admin_uid")
        private val KEY_PENDING_DELETED_STUDENTS = stringSetPreferencesKey("pending_deleted_student_uids")
    }

    val backendBaseUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_BACKEND_URL]?.takeIf { it.isNotBlank() }
            ?: BuildConfig.BACKEND_BASE_URL.takeIf { it.isNotBlank() }
            ?: "http://10.0.2.2:8000/"
        normalizeBaseUrl(raw)
    }

    val lastSyncEpochFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SYNC_EPOCH] ?: 0L
    }

    val biometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ENABLED] ?: false
    }

    val biometricAdminUidFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ADMIN_UID]?.takeIf { it.isNotBlank() }
    }

    suspend fun getBackendBaseUrl(): String = backendBaseUrlFlow.first()

    suspend fun setBackendBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKEND_URL] = normalizeBaseUrl(url)
        }
    }

    suspend fun saveEncryptedJwt(jwt: String, adminUid: String) {
        val enc = cryptoManager.encryptString(jwt)
        context.dataStore.edit { prefs ->
            prefs[KEY_ENC_JWT] = enc
            prefs[KEY_LAST_ADMIN_UID] = adminUid
        }
    }

    suspend fun getDecryptedJwt(): String? {
        val enc = context.dataStore.data.first()[KEY_ENC_JWT] ?: return null
        return runCatching { cryptoManager.decryptString(enc) }.getOrNull()
    }

    suspend fun clearJwt() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ENC_JWT)
        }
    }

    suspend fun enableBiometricForAdmin(adminUid: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = true
            prefs[KEY_BIOMETRIC_ADMIN_UID] = adminUid
        }
    }

    suspend fun disableBiometric() {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = false
            prefs.remove(KEY_BIOMETRIC_ADMIN_UID)
        }
    }

    suspend fun clearBiometricIfMatches(adminUid: String) {
        context.dataStore.edit { prefs ->
            if (prefs[KEY_BIOMETRIC_ADMIN_UID] == adminUid) {
                prefs[KEY_BIOMETRIC_ENABLED] = false
                prefs.remove(KEY_BIOMETRIC_ADMIN_UID)
            }
        }
    }

    suspend fun addPendingDeletedStudentUid(studentUid: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_PENDING_DELETED_STUDENTS].orEmpty()
            prefs[KEY_PENDING_DELETED_STUDENTS] = current + studentUid
        }
    }

    suspend fun getPendingDeletedStudentUids(): Set<String> =
        context.dataStore.data.first()[KEY_PENDING_DELETED_STUDENTS].orEmpty()

    suspend fun removePendingDeletedStudentUid(studentUid: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_PENDING_DELETED_STUDENTS].orEmpty()
            if (studentUid in current) {
                prefs[KEY_PENDING_DELETED_STUDENTS] = current - studentUid
            }
        }
    }

    suspend fun recordSyncTimestamp(epochMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_EPOCH] = epochMs
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
