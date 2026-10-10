package com.teraper.printmaster.core.data.team

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.SyncDao
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.database.entity.CompanyMemberEntity
import com.teraper.printmaster.core.database.entity.SyncStateEntity
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.AttachResult
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.TeamState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore layout (rules in firestore.rules), one space per own company that invited masters:
 *  joinCodes/{code}                 → { workspaceId }
 *  workspaces/{id}                  → { name, ownerUid, ownerName, joinCode }
 *  workspaces/{id}/members/{uid}    → { name, email, joinCode }
 *  workspaces/{id}/clients|orders|prices/{syncId}  (see FirebaseSyncBackend)
 * Locally a space is a company row: OWN with space_id/join_code, or ATTACHED (another owner's).
 */
@Singleton
internal class FirebaseTeamRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PrintMasterDatabase,
    private val syncDao: SyncDao,
    private val companyDao: CompanyDao,
    private val analytics: AppAnalytics,
    private val clock: Clock,
) : TeamRepository {

    /** The Firebase plugin initializes Firebase only when google-services.json was in the build. */
    val available: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val account = MutableStateFlow(currentAccount())

    val uid: String? get() = if (available) FirebaseAuth.getInstance().currentUser?.uid else null
    val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    override val googleClientId: String? =
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName).takeIf { it != 0 }?.let(context::getString)

    private fun currentAccount(): Pair<String, String?>? =
        if (available) FirebaseAuth.getInstance().currentUser?.let { user -> user.email.orEmpty() to user.displayName } else null

    override fun observeState(): Flow<TeamState> {
        if (!available) return flowOf(TeamState(available = false))
        return account.map { TeamState(available = true, email = it?.first, displayName = it?.second) }
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

    override suspend fun inviteCode(companyId: Long): String? {
        val company = companyDao.getCompany(companyId)?.takeIf { it.kind == CompanyKind.OWN } ?: return null
        if (company.spaceId != null && company.joinCode != null) return company.joinCode
        return attemptOrNull {
            val uid = uid ?: error("not signed in")
            val ref = firestore.collection("workspaces").document()
            val code = newCode()
            firestore.batch()
                .set(ref, mapOf("name" to company.name, "ownerUid" to uid, "ownerName" to ownerName(), "joinCode" to code, "createdAt" to FieldValue.serverTimestamp()))
                .set(firestore.collection("joinCodes").document(code), mapOf("workspaceId" to ref.id))
                .commit().await()
            companyDao.setSpace(company.id, ref.id, code)
            // The price list goes out with the first sync; orders once a master attaches and gets one.
            queuePrices()
            analytics.log("team_space_created")
            code
        }
    }

    override suspend fun newJoinCode(companyId: Long): String? {
        val company = companyDao.getCompany(companyId)?.takeIf { it.kind == CompanyKind.OWN && it.spaceId != null } ?: return null
        val spaceId = checkNotNull(company.spaceId)
        return attemptOrNull {
            val code = newCode()
            val batch = firestore.batch()
                .set(firestore.collection("joinCodes").document(code), mapOf("workspaceId" to spaceId))
                .update(firestore.collection("workspaces").document(spaceId), "joinCode", code)
            company.joinCode?.let { batch.delete(firestore.collection("joinCodes").document(it)) }
            batch.commit().await()
            companyDao.setSpace(company.id, spaceId, code)
            code
        }
    }

    override suspend fun removeMember(companyId: Long, masterId: Long): Boolean {
        val company = companyDao.getCompany(companyId) ?: return false
        val memberUid = syncDao.getMaster(masterId)?.memberUid ?: return false
        return attempt {
            company.spaceId?.let { firestore.collection("workspaces").document(it).collection("members").document(memberUid).delete().await() }
            companyDao.deleteMembership(companyId, masterId)
        }
    }

    override suspend fun attachCompany(code: String, myName: String): AttachResult {
        if (!available) return AttachResult.Failed
        val uid = uid ?: return AttachResult.NotSignedIn
        val clean = code.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.isEmpty()) return AttachResult.WrongCode
        return try {
            val spaceId = firestore.collection("joinCodes").document(clean).get().await().getString("workspaceId") ?: return AttachResult.WrongCode
            companyDao.getCompanyBySpace(spaceId)?.let { existing ->
                return if (existing.kind == CompanyKind.OWN) AttachResult.OwnCompany else AttachResult.AlreadyAttached
            }
            val ref = firestore.collection("workspaces").document(spaceId)
            ref.collection("members").document(uid).set(
                mapOf(
                    "name" to myName.trim(),
                    "email" to FirebaseAuth.getInstance().currentUser?.email.orEmpty(),
                    "joinCode" to clean,
                    "joinedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
            val space = ref.get().await()
            val name = space.getString("name").orEmpty()
            val owner = space.getString("ownerName").orEmpty()
            val used = companyDao.observeAttachedCompanies().first().map { it.colorIndex } + companyDao.observeCompanies().first().map { it.colorIndex }
            companyDao.insertCompany(
                CompanyEntity(
                    name = name, taxId = null, createdAt = clock.millis(), kind = CompanyKind.ATTACHED,
                    spaceId = spaceId, ownerName = owner, colorIndex = (0 until PALETTE_SIZE).firstOrNull { it !in used } ?: used.size % PALETTE_SIZE,
                ),
            )
            analytics.log("team_joined")
            AttachResult.Attached(name, owner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AttachResult.Failed
        }
    }

    override suspend fun leaveCompany(companyId: Long): Boolean {
        val company = companyDao.getCompany(companyId)?.takeIf { it.kind == CompanyKind.ATTACHED } ?: return false
        val spaceId = company.spaceId ?: return true
        return attempt {
            uid?.let { firestore.collection("workspaces").document(spaceId).collection("members").document(it).delete().await() }
            companyDao.setSpace(company.id, null, null)
        }
    }

    /**
     * Phones from before own/attached companies had one space, kept in preferences.
     * The owner's becomes its default company's space; a joined master's company becomes attached.
     */
    suspend fun moveOldSpace() {
        val prefs = context.getSharedPreferences("team", Context.MODE_PRIVATE)
        val id = prefs.getString("id", null) ?: return
        db.withTransaction {
            val profile = companyDao.getProfile() ?: return@withTransaction
            val company = companyDao.getCompany(profile.defaultCompanyId) ?: return@withTransaction
            if (prefs.getBoolean("owner", false)) {
                companyDao.setSpace(company.id, id, prefs.getString("code", null))
                companyDao.observeMasters().first().filter { it.memberUid != null }.forEach {
                    companyDao.insertMembership(CompanyMemberEntity(company.id, it.id))
                }
            } else if (profile.mode == AccountMode.JOINED) {
                companyDao.updateCompany(company.copy(kind = CompanyKind.ATTACHED, spaceId = id, taxId = null))
                syncDao.attachAllClients(company.id)
                syncDao.attachAllParts(company.id)
            }
            syncDao.getState("cursor")?.let { syncDao.setState(SyncStateEntity("cursor:$id", it)) }
        }
        prefs.edit { clear() }
    }

    private suspend fun ownerName(): String = companyDao.getProfile()?.ownerName?.ifBlank { null }
        ?: FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()

    /** Puts the own price list into the outbox (a new space needs all of it). */
    private fun queuePrices() {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO sync_outbox (entity, row_id)
            SELECT 'price', p.id FROM repair_parts p
            WHERE p.attached_company_id IS NULL AND NOT EXISTS (SELECT 1 FROM sync_outbox WHERE entity = 'price' AND row_id = p.id)
            """,
        )
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = attemptOrNull { block(); true } ?: false

    private suspend fun <T> attemptOrNull(block: suspend () -> T): T? {
        if (!available) return null
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 8 letters/digits without look-alikes (0/O, 1/I): easy to read over the phone. */
    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private companion object {
        /** Same number of colors as the company badge palette. */
        const val PALETTE_SIZE = 8
    }
}
