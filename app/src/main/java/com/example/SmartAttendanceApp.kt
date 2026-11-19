package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.data.sync.SyncWorker

class SmartAttendanceApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runCatching {
            SyncWorker.schedulePeriodicSync(this)
        }
    }
}
