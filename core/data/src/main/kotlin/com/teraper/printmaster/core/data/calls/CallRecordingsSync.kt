package com.teraper.printmaster.core.data.calls

import android.content.Context
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.teraper.printmaster.core.data.repository.CallRecordingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration

/**
 * Picks up new call recordings in the background: right after Android adds a new audio
 * file (the recording of the call that just ended), and every few hours in case that was missed.
 */
object CallRecordingsSync {

    private const val AFTER_CALL = "call-recordings-after-call"
    private const val PERIODIC = "call-recordings-periodic"
    private const val NOW = "call-recordings-now"

    /** Call when the app starts and after a folder is picked; also looks at the folder right away. */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<CallRecordingsWorker>().build())
        work.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CallRecordingsWorker>(Duration.ofHours(3)).build(),
        )
        scheduleAfterNextCall(context)
    }

    /** A content trigger fires once, so the worker sets it again after every run. */
    internal fun isAfterCall(tags: Set<String>) = AFTER_CALL in tags

    internal fun scheduleAfterNextCall(context: Context) {
        val constraints = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AFTER_CALL,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CallRecordingsWorker>().setConstraints(constraints).addTag(AFTER_CALL).build(),
        )
    }
}

class CallRecordingsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun callRecordingsRepository(): CallRecordingsRepository
    }

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).callRecordingsRepository()
        repository.scan()
        if (CallRecordingsSync.isAfterCall(tags)) {
            CallRecordingsSync.scheduleAfterNextCall(applicationContext)
        }
        return Result.success()
    }
}
