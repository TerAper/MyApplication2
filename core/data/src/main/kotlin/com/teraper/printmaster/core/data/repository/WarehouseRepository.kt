package com.teraper.printmaster.core.data.repository

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.dao.StockDao
import com.teraper.printmaster.core.database.entity.StockMoveEntity
import com.teraper.printmaster.core.model.CompanyKind
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.StockDraft
import com.teraper.printmaster.core.model.StockDraftError
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.StockLine
import com.teraper.printmaster.core.model.StockMove
import com.teraper.printmaster.core.model.WarehouseMode
import com.teraper.printmaster.core.model.MastersSee
import com.teraper.printmaster.core.model.CompanyStockLine
import com.teraper.printmaster.core.model.SharedStockItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** The warehouse: how many of each catalog item there are, and every movement that made the count. */
interface WarehouseRepository {
    fun observeMode(): Flow<WarehouseMode>

    /** Switches between one shared warehouse and one per company; the stock moves along. */
    suspend fun setMode(mode: WarehouseMode)

    /** Catalog items (not one-of-a-kind ones) with their count in the warehouse being viewed. */
    fun observeStock(): Flow<List<StockLine>>

    fun observeCount(itemId: Long): Flow<Int>

    /** Counts by item id in the warehouse [companyId]'s orders take goods from. */
    fun observeCountsFor(companyId: Long): Flow<Map<Long, Int>>

    /** Newest first. */
    fun observeMoves(itemId: Long): Flow<List<StockMove>>

    suspend fun save(draft: StockDraft): SaveStockResult

    /** Scanned in: one more of [itemId] received (price not known); returns the movement, to undo it. */
    suspend fun scanIn(itemId: Long): Long

    /** Takes back a receive made by [scanIn]. */
    suspend fun undoReceive(moveId: Long)

    /** What masters attached to the user's companies see: counts only (default) or prices too. */
    fun observeMastersSee(): Flow<MastersSee>

    suspend fun setMastersSee(see: MastersSee)

    /** On a master's phone: the stock attached companies share with him. */
    fun observeCompanyStock(): Flow<List<CompanyStockLine>>
}

sealed interface SaveStockResult {
    data object Saved : SaveStockResult
    data class Invalid(val errors: Set<StockDraftError>) : SaveStockResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class OfflineWarehouseRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val db: PrintMasterDatabase,
    private val stockDao: StockDao,
    private val catalogDao: CatalogDao,
    private val writer: CatalogWriter,
    private val companies: CompaniesRepository,
    private val clock: Clock,
) : WarehouseRepository {

    private val prefs = context.getSharedPreferences("warehouse", Context.MODE_PRIVATE)
    private val mode = MutableStateFlow(
        prefs.getString(MODE, null)?.let { name -> WarehouseMode.entries.firstOrNull { it.name == name } } ?: WarehouseMode.SHARED,
    )

    private val mastersSee = MutableStateFlow(
        prefs.getString(MASTERS_SEE, null)?.let { name -> MastersSee.entries.firstOrNull { it.name == name } } ?: MastersSee.COUNTS,
    )

    override fun observeMode(): Flow<WarehouseMode> = mode.asStateFlow()

    override suspend fun scanIn(itemId: Long): Long = stockDao.insert(
        StockMoveEntity(itemId = itemId, companyId = currentWarehouse(), quantity = 1, kind = StockKind.RECEIVE, at = clock.millis()),
    )

    override suspend fun undoReceive(moveId: Long) = stockDao.deleteReceive(moveId)

    override fun observeMastersSee(): Flow<MastersSee> = mastersSee.asStateFlow()

    override suspend fun setMastersSee(see: MastersSee) {
        mastersSee.value = see
        prefs.edit { putString(MASTERS_SEE, see.name) }
    }

    override fun observeCompanyStock(): Flow<List<CompanyStockLine>> = stockDao.observeAttachedStock().map { rows ->
        rows.map { CompanyStockLine(it.attachedCompanyId, it.type, it.name, it.count, it.priceMinor?.let(::Money)) }
    }

    /**
     * What [companyId]'s masters may see: items sold as goods or with stock, by the warehouse its
     * orders use. Ids are the items' own; only this phone writes them.
     */
    internal suspend fun sharedStock(companyId: Long): List<SharedStockItem> {
        val warehouseId = warehouseFor(companyId)
        val counts = stockDao.observeCounts(warehouseId).first().associate { it.id to it.count }
        val prices = mastersSee.value == MastersSee.COUNTS_AND_PRICES
        return catalogDao.observeSnapshot().first().items.values
            .filter { !it.type.perClient && (it.isGoods || (counts[it.id] ?: 0) != 0) }
            .map { SharedStockItem("item-${it.id}", it.type.name, it.title, counts[it.id] ?: 0, it.price.minor.takeIf { prices }) }
    }

    /** Fires when what masters see may have changed. */
    internal fun observeSharedChanges(): Flow<Any> = combine(stockDao.observeChanges(), mastersSee, mode) { a, b, c -> Triple(a, b, c) }

    override suspend fun setMode(mode: WarehouseMode) {
        if (mode == this.mode.value) return
        db.withTransaction {
            when (mode) {
                WarehouseMode.SHARED -> stockDao.mergeIntoShared()
                // What was shared becomes the company being viewed's (its default when none).
                WarehouseMode.PER_COMPANY -> ownCompanyId()?.let { stockDao.splitSharedTo(it) }
            }
        }
        this.mode.value = mode
        prefs.edit { putString(MODE, mode.name) }
    }

    /** The warehouse being viewed: null = the shared one, else the active own company's. */
    private fun warehouse(): Flow<Long?> = combine(mode, companies.observeActiveCompany()) { mode, company ->
        if (mode == WarehouseMode.SHARED) null else company?.takeIf { it.kind == CompanyKind.OWN }?.id
    }.distinctUntilChanged()

    private suspend fun ownCompanyId(): Long? =
        companies.observeActiveCompany().first()?.takeIf { it.kind == CompanyKind.OWN }?.id

    private suspend fun currentWarehouse(): Long? = if (mode.value == WarehouseMode.SHARED) null else ownCompanyId()

    /** The warehouse [companyId]'s orders take goods from: null = the shared one. */
    internal fun warehouseFor(companyId: Long): Long? = if (mode.value == WarehouseMode.SHARED) null else companyId

    override fun observeCountsFor(companyId: Long): Flow<Map<Long, Int>> = mode.map { warehouseFor(companyId) }.distinctUntilChanged()
        .flatMapLatest { warehouse -> stockDao.observeCounts(warehouse).map { counts -> counts.associate { it.id to it.count } } }

    override fun observeStock(): Flow<List<StockLine>> = warehouse().flatMapLatest { companyId ->
        combine(catalogDao.observeSnapshot(), stockDao.observeCounts(companyId)) { snapshot, counts ->
            val countBy = counts.associate { it.id to it.count }
            snapshot.items.values
                .filterNot { it.type.perClient }
                .map { StockLine(it, countBy[it.id] ?: 0, it.keepAtLeast) }
        }
    }

    override fun observeCount(itemId: Long): Flow<Int> = warehouse().flatMapLatest { companyId ->
        stockDao.observeCounts(companyId).map { counts -> counts.firstOrNull { it.id == itemId }?.count ?: 0 }
    }

    override fun observeMoves(itemId: Long): Flow<List<StockMove>> = warehouse().flatMapLatest { companyId ->
        stockDao.observeMoves(itemId, companyId).map { rows ->
            rows.map {
                StockMove(it.id, it.itemId, it.quantity, it.kind, it.at, Money(it.unitCostMinor), it.reason, it.note, it.orderId, it.otherCompanyId)
            }
        }
    }

    override suspend fun save(draft: StockDraft): SaveStockResult = db.withTransaction {
        val companyId = currentWarehouse()
        val current = stockDao.count(draft.itemId, companyId)
        val errors = draft.validate(current)
        if (errors.isNotEmpty()) return@withTransaction SaveStockResult.Invalid(errors)
        val now = clock.millis()
        val note = draft.note.trim()
        when (draft.kind) {
            StockKind.RECEIVE -> {
                stockDao.insert(
                    StockMoveEntity(itemId = draft.itemId, companyId = companyId, quantity = draft.quantity, kind = StockKind.RECEIVE, at = now, unitCostMinor = draft.unitCost.minor, note = note),
                )
                // What was paid last is what it costs now (for profit and the stock's value).
                if (draft.unitCost.isPositive) {
                    catalogDao.getItem(draft.itemId)?.let { catalogDao.updateItem(it.copy(costMinor = draft.unitCost.minor)) }
                }
            }
            StockKind.ADJUST -> stockDao.insert(
                StockMoveEntity(itemId = draft.itemId, companyId = companyId, quantity = draft.change(current), kind = StockKind.ADJUST, at = now, reason = draft.reason, note = note),
            )
            StockKind.MOVE -> {
                val to = checkNotNull(draft.toCompanyId)
                stockDao.insert(StockMoveEntity(itemId = draft.itemId, companyId = companyId, quantity = -draft.quantity, kind = StockKind.MOVE, at = now, note = note, otherCompanyId = to))
                stockDao.insert(StockMoveEntity(itemId = draft.itemId, companyId = to, quantity = draft.quantity, kind = StockKind.MOVE, at = now, note = note, otherCompanyId = companyId))
            }
            StockKind.ORDER -> Unit
        }
        SaveStockResult.Saved
    }

    private companion object {
        const val MODE = "mode"
        const val MASTERS_SEE = "masters_see"
    }
}
