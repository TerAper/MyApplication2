package com.teraper.printmaster

import android.app.Application
import com.teraper.printmaster.core.data.AppNames
import com.teraper.printmaster.core.data.calls.CallRecordingsSync
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import com.teraper.printmaster.core.data.repository.PhotoRepository
import com.teraper.printmaster.core.data.sync.LiveSync
import com.teraper.printmaster.core.data.tracking.TrackingRepository
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

    @Inject lateinit var tracking: TrackingRepository

    /** Lives as long as the app process: live sync and its notifications. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Before anything calls Firebase: proves to it that requests come from this app.
        AppCheck.install(this)
        CallRecordingsSync.schedule(this)
        callRecordings.startWatching(appScope)
        liveSync.start(appScope)
        // Photos of deleted client items, parts, orders and expenses.
        appScope.launch { photos.cleanUp() }
        // A master agreed to share where he is on an order: the service sends it while it's open.
        appScope.launch { tracking.observeSharing().collect { if (it.isNotEmpty()) LocationShareService.start(this@PrintMasterApplication) } }
        appScope.launch { syncRunner.reports.collect { SyncNotifications.show(AppNames.wrap(this@PrintMasterApplication), it) } }
    }
}
