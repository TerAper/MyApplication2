package com.teraper.printmaster.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.ListenerRegistration
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.SyncDao
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.data.team.FirebaseTeamRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the phone in step with the shared space without anyone pressing "Sync":
 * a few seconds after a change here, at once when the other side changes something
 * (Firestore live listener, while the app runs), and every 15 minutes in the background.
 */
@Singleton
class LiveSync @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val team: FirebaseTeamRepository,
    private val syncDao: SyncDao,
    private val companyDao: CompanyDao,
    private val runner: SyncRunner,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var listeners: List<ListenerRegistration> = emptyList()

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (!team.available) return
        scope.launch {
            team.moveOldSpace()
            requests.receiveAsFlow().debounce(2_000).collect {
                // No internet: let WorkManager run it once the phone is online again.
                if (runner.isActive && runner.syncNow() == null) syncWhenOnline(context)
            }
        }
        scope.launch { syncDao.observeOutboxCount().filter { it > 0 }.collect { requests.trySend(Unit) } }
        scope.launch {
            // Listen to every space this phone shares: own companies that invited masters, attached companies.
            companyDao.observeSharedCompanies().map { list -> list.map { Triple(it.id, it.spaceId!!, it.kind) } }.distinctUntilChanged().collect { spaces ->
                listeners.forEach { it.remove() }
                listeners = emptyList()
                val uid = team.uid
                if (spaces.isEmpty() || uid == null) return@collect
                schedule(context)
                requests.trySend(Unit)
                listeners = spaces.flatMap { (_, spaceId, kind) ->
                    val ref = team.firestore.collection("workspaces").document(spaceId)
                    // Only what this phone may read there: the rules reject wider listeners for masters.
                    val queries = if (kind == CompanyKind.OWN) {
                        listOf(ref.collection("orders"), ref.collection("clients"), ref.collection("members"))
                    } else {
                        listOf(ref.collection("orders").whereEqualTo("masterUid", uid), ref.collection("clients").whereArrayContains("visibleTo", uid), ref.collection("prices"))
                    }
                    queries.map { query ->
                        query.addSnapshotListener { snapshot, _ ->
                            // Our own writes echo back first with pending writes; only others' changes matter.
                            if (snapshot != null && !snapshot.metadata.hasPendingWrites() && !snapshot.metadata.isFromCache) requests.trySend(Unit)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val PERIODIC = "shared-space-sync"

        fun schedule(context: Context) {
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SharedSpaceWorker>(Duration.ofMinutes(15)).setConstraints(online).build(),
            )
        }

        /** One sync as soon as there is internet (e.g. a change made offline). */
        fun syncWhenOnline(context: Context) {
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "shared-space-sync-once",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SharedSpaceWorker>().setConstraints(online).build(),
            )
        }
    }
}

class SharedSpaceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun syncRunner(): SyncRunner
    }

    override suspend fun doWork(): Result {
        val runner = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java).syncRunner()
        if (!runner.isActive) return Result.success()
        return if (runner.syncNow() != null) Result.success() else Result.retry()
    }
}
