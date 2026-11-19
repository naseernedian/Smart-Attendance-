package com.example

import android.content.Context
import com.example.biometrics.FaceRecognitionEngine
import com.example.data.local.SessionPreferences
import com.example.data.local.SmartAttendanceDatabase
import com.example.data.sync.NetworkConnectivityObserver
import com.example.data.sync.SyncRepository
import com.example.reports.ReportGenerator
import com.example.security.CryptoManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val cryptoManager: CryptoManager by lazy { CryptoManager(appContext) }
    val database: SmartAttendanceDatabase by lazy { SmartAttendanceDatabase.create(appContext) }
    val sessionPreferences: SessionPreferences by lazy { SessionPreferences(appContext, cryptoManager) }
    val connectivityObserver: NetworkConnectivityObserver by lazy { NetworkConnectivityObserver(appContext) }
    val faceEngine: FaceRecognitionEngine by lazy { FaceRecognitionEngine(appContext) }
    val reportGenerator: ReportGenerator by lazy { ReportGenerator(appContext) }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            context = appContext,
            adminDao = database.adminDao(),
            studentDao = database.studentDao(),
            attendanceDao = database.attendanceRecordDao(),
            sessionPreferences = sessionPreferences,
            cryptoManager = cryptoManager,
            connectivityObserver = connectivityObserver
        )
    }
}
