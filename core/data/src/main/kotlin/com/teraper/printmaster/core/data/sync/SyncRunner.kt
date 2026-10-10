package com.teraper.printmaster.core.data.sync

import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.model.SyncReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class SyncStatus(
    val running: Boolean = false,
    val lastSyncAt: Instant? = null,
    val lastReport: SyncReport? = null,
    /** The last attempt failed (no internet, signed out…). */
    val failed: Boolean = false,
)

/** What screens need from the sync: its state and "sync now". */
interface SyncController {
    val status: StateFlow<SyncStatus>

    /** Null when not set up or it failed. */
    suspend fun syncNow(): SyncReport?
}

/** One sync at a time, whoever asks (button, app start, background, live updates). */
@Singleton
class SyncRunner @Inject constructor(
    private val engine: SyncEngine,
    private val backend: SyncBackend,
    private val analytics: AppAnalytics,
    private val clock: Clock,
) : SyncController {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus())
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val _reports = MutableSharedFlow<SyncReport>(extraBufferCapacity = 8)

    /** Every successful sync, for notifications. */
    val reports: SharedFlow<SyncReport> = _reports.asSharedFlow()

    /** True when this phone is in a shared space and signed in. */
    val isActive: Boolean get() = backend.myUid != null

    override suspend fun syncNow(): SyncReport? {
        if (!isActive) return null
        return mutex.withLock {
            _status.update { it.copy(running = true) }
            try {
                val report = engine.sync()
                _status.value = SyncStatus(lastSyncAt = clock.instant(), lastReport = report)
                _reports.tryEmit(report)
                if (report.sent + report.received > 0) analytics.log("sync", mapOf("sent" to report.sent, "received" to report.received))
                report
            } catch (e: CancellationException) {
                _status.update { it.copy(running = false) }
                throw e
            } catch (e: Exception) {
                _status.update { it.copy(running = false, failed = true) }
                null
            }
        }
    }
}
