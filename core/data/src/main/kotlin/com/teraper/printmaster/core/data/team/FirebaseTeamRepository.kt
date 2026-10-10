package com.teraper.printmaster.core.data.team

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.SyncDao
import com.teraper.printmaster.core.database.entity.MasterEntity
import com.teraper.printmaster.core.model.JoinResult
import com.teraper.printmaster.core.model.TeamMember
import com.teraper.printmaster.core.model.TeamSpace
import com.teraper.printmaster.core.model.TeamState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore layout (rules in firestore.rules):
 *  joinCodes/{code}                 → { workspaceId }
 *  workspaces/{id}                  → { name, ownerUid, joinCode }
 *  workspaces/{id}/members/{uid}    → { name, email, joinCode }
 *  workspaces/{id}/clients|orders|prices/{syncId}  (see FirebaseSyncBackend)
 */
@Singleton
internal class FirebaseTeamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PrintMasterDatabase,
    private val syncDao: SyncDao,
    private val companyDao: CompanyDao,
    private val companies: CompaniesRepository,
    private val analytics: AppAnalytics,
    private val clock: Clock,
) : TeamRepository {

    /** The Firebase plugin initializes Firebase only when google-services.json was in the build. */
    val available: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val prefs = context.getSharedPreferences("team", Context.MODE_PRIVATE)
    private val space = MutableStateFlow(loadSpace())
    private val account = MutableStateFlow(currentAccount())

    private fun currentAccount(): Pair<String, String?>? =
        if (available) FirebaseAuth.getInstance().currentUser?.let { user -> user.email.orEmpty() to user.displayName } else null

    val currentSpace: TeamSpace? get() = space.value
    val spaceFlow: kotlinx.coroutines.flow.StateFlow<TeamSpace?> get() = space
    val uid: String? get() = if (available) FirebaseAuth.getInstance().currentUser?.uid else null
    val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    override val googleClientId: String? =
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName).takeIf { it != 0 }?.let(context::getString)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeState(): Flow<TeamState> {
        if (!available) return flowOf(TeamState(available = false))
        val members = space.flatMapLatest { s -> if (s?.isOwner == true) membersOf(s.id) else flowOf(emptyList()) }
        return combine(account, space, members, companyDao.observeMasters()) { account, space, members, masters ->
            TeamState(
                available = true,
                email = account?.first,
                displayName = account?.second,
                space = space,
                members = members.map { m -> m.copy(masterId = masters.firstOrNull { it.memberUid == m.uid }?.id) },
            )
        }
    }

    private fun membersOf(spaceId: String): Flow<List<TeamMember>> = callbackFlow {
        val registration = firestore.collection("workspaces").document(spaceId).collection("members")
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.documents.orEmpty().map { TeamMember(it.id, it.getString("name").orEmpty(), it.getString("email").orEmpty(), null) })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun signIn(idToken: String): Boolean = attempt {
        FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        account.value = currentAccount()
        analytics.log("sign_in")
    }

    override suspend fun signOut() {
        if (!available) return
        FirebaseAuth.getInstance().signOut()
        account.value = null
    }

    override suspend fun createSpace(): Boolean = attempt {
        val uid = uid ?: error("not signed in")
        val company = companies.observeActiveCompany().first() ?: error("no company")
        val ref = firestore.collection("workspaces").document()
        val code = newCode()
        firestore.batch()
            .set(ref, mapOf("name" to company.name, "ownerUid" to uid, "joinCode" to code, "createdAt" to FieldValue.serverTimestamp()))
            .set(firestore.collection("joinCodes").document(code), mapOf("workspaceId" to ref.id))
            .commit().await()
        saveSpace(TeamSpace(ref.id, company.name, isOwner = true, joinCode = code))
        // Everything already assigned to joined masters, and the price list, goes out with the first sync.
        queueAllForMasters()
        analytics.log("team_space_created")
    }

    override suspend fun newJoinCode(): Boolean = attempt {
        val current = space.value?.takeIf { it.isOwner } ?: error("not owner")
        val code = newCode()
        val batch = firestore.batch()
            .set(firestore.collection("joinCodes").document(code), mapOf("workspaceId" to current.id))
            .update(firestore.collection("workspaces").document(current.id), "joinCode", code)
        current.joinCode?.let { batch.delete(firestore.collection("joinCodes").document(it)) }
        batch.commit().await()
        saveSpace(current.copy(joinCode = code))
    }

    override suspend fun join(code: String, myName: String): JoinResult {
        if (!available) return JoinResult.FAILED
        val uid = uid ?: return JoinResult.NOT_SIGNED_IN
        val clean = code.filter { it.isLetterOrDigit() }.uppercase()
        return try {
            val spaceId = firestore.collection("joinCodes").document(clean).get().await().getString("workspaceId") ?: return JoinResult.WRONG_CODE
            val ref = firestore.collection("workspaces").document(spaceId)
            ref.collection("members").document(uid).set(
                mapOf(
                    "name" to myName.trim(),
                    "email" to FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                    "joinCode" to clean,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            val name = ref.get().await().getString("name").orEmpty()
            saveSpace(TeamSpace(spaceId, name, isOwner = false, joinCode = null))
            analytics.log("team_joined")
            JoinResult.JOINED
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JoinResult.FAILED
        }
    }

    override suspend fun linkMember(uid: String, masterId: Long?) {
        val current = space.value?.takeIf { it.isOwner } ?: return
        val doc = firestore.collection("workspaces").document(current.id).collection("members").document(uid).get().await()
        val name = doc.getString("name").orEmpty()
        val mail = doc.getString("email")
        // One local master per member.
        companyDao.observeMasters().first().filter { it.memberUid == uid && it.id != masterId }.forEach {
            companyDao.updateMaster(it.copy(memberUid = null, memberEmail = null))
        }
        if (masterId == null) {
            companyDao.insertMaster(MasterEntity(name = name.ifBlank { mail.orEmpty() }, createdAt = clock.millis(), memberEmail = mail, memberUid = uid))
        } else {
            companyDao.observeMasters().first().firstOrNull { it.id == masterId }?.let {
                companyDao.updateMaster(it.copy(memberEmail = mail, memberUid = uid))
            }
        }
        queueAllForMasters()
    }

    override suspend fun removeMember(uid: String) {
        val current = space.value?.takeIf { it.isOwner } ?: return
        firestore.collection("workspaces").document(current.id).collection("members").document(uid).delete().await()
        companyDao.observeMasters().first().filter { it.memberUid == uid }.forEach {
            companyDao.updateMaster(it.copy(memberUid = null, memberEmail = null))
        }
    }

    /** Puts orders of joined masters and the whole price list into the outbox. */
    private fun queueAllForMasters() {
        val sql = db.openHelper.writableDatabase
        sql.execSQL(
            """
            INSERT INTO sync_outbox (entity, row_id)
            SELECT 'order', o.id FROM orders o JOIN masters m ON m.id = o.master_id
            WHERE m.member_uid IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sync_outbox WHERE entity = 'order' AND row_id = o.id)
            """,
        )
        sql.execSQL(
            """
            INSERT INTO sync_outbox (entity, row_id)
            SELECT 'price', p.id FROM repair_parts p WHERE NOT EXISTS (SELECT 1 FROM sync_outbox WHERE entity = 'price' AND row_id = p.id)
            """,
        )
    }

    private fun loadSpace(): TeamSpace? {
        val id = prefs.getString("id", null) ?: return null
        return TeamSpace(id, prefs.getString("name", "").orEmpty(), prefs.getBoolean("owner", false), prefs.getString("code", null))
    }

    private fun saveSpace(value: TeamSpace) {
        prefs.edit().putString("id", value.id).putString("name", value.name).putBoolean("owner", value.isOwner).putString("code", value.joinCode).apply()
        space.value = value
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean {
        if (!available) return false
        return try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** 8 letters/digits without look-alikes (0/O, 1/I): easy to read over the phone. */
    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }
}
