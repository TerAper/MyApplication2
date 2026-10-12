package com.teraper.printmaster

import android.app.Application
import com.teraper.printmaster.core.data.calls.CallRecordingsSync
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.PhotoRepository
import com.teraper.printmaster.core.data.sync.LiveSync
import com.teraper.printmaster.core.data.sync.SyncRunner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PrintMasterApplication : Application() {

    @Inject lateinit var liveSync: LiveSync

    @Inject lateinit var syncRunner: SyncRunner

    @Inject lateinit var photos: PhotoRepository

    @Inject lateinit var callRecordings: CallRecordingsRepository

    /** Lives as long as the app process: live sync and its notifications. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CallRecordingsSync.schedule(this)
        callRecordings.startWatching(appScope)
        liveSync.start(appScope)
        // Photos of deleted client items, parts, orders and expenses.
        appScope.launch { photos.cleanUp() }
        appScope.launch { syncRunner.reports.collect { SyncNotifications.show(this@PrintMasterApplication, it) } }
    }
}
