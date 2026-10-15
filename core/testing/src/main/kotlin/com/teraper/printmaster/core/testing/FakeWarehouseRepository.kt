package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.SaveStockResult
import com.teraper.printmaster.core.data.repository.WarehouseRepository
import com.teraper.printmaster.core.model.StockDraft
import com.teraper.printmaster.core.model.StockKind
import com.teraper.printmaster.core.model.StockLine
import com.teraper.printmaster.core.model.StockMove
import com.teraper.printmaster.core.model.WarehouseMode
import com.teraper.printmaster.core.model.MastersSee
import com.teraper.printmaster.core.model.CompanyStockLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory warehouse: [lines] as shown, [moves] in the order saved. */
class FakeWarehouseRepository(lines: List<StockLine> = emptyList()) : WarehouseRepository {
    val mode = MutableStateFlow(WarehouseMode.SHARED)
    val lines = MutableStateFlow(lines)
    val moves = MutableStateFlow<List<StockMove>>(emptyList())
    val saved = mutableListOf<StockDraft>()

    override fun observeMode(): Flow<WarehouseMode> = mode

    override suspend fun setMode(mode: WarehouseMode) {
        this.mode.value = mode
    }

    override fun observeStock(): Flow<List<StockLine>> = lines

    override fun observeCount(itemId: Long): Flow<Int> = lines.map { all -> all.firstOrNull { it.item.id == itemId }?.count ?: 0 }

    override suspend fun scanIn(itemId: Long): Long {
        val id = moves.value.size + 1L
        moves.update { it + StockMove(id, itemId, 1, StockKind.RECEIVE, 0L) }
        lines.update { all -> all.map { if (it.item.id == itemId) it.copy(count = it.count + 1) else it } }
        return id
    }

    override suspend fun undoReceive(moveId: Long) {
        val move = moves.value.firstOrNull { it.id == moveId } ?: return
        moves.update { it - move }
        lines.update { all -> all.map { if (it.item.id == move.itemId) it.copy(count = it.count - move.quantity) else it } }
    }

    val mastersSee = MutableStateFlow(MastersSee.COUNTS)
    val companyStock = MutableStateFlow<List<CompanyStockLine>>(emptyList())

    override fun observeMastersSee(): Flow<MastersSee> = mastersSee

    override suspend fun setMastersSee(see: MastersSee) {
        mastersSee.value = see
    }

    override fun observeCompanyStock(): Flow<List<CompanyStockLine>> = companyStock

    override fun observeCountsFor(companyId: Long): Flow<Map<Long, Int>> = lines.map { all -> all.associate { it.item.id to it.count } }

    override fun observeMoves(itemId: Long): Flow<List<StockMove>> = moves.map { all -> all.filter { it.itemId == itemId }.reversed() }

    override suspend fun save(draft: StockDraft): SaveStockResult {
        val current = lines.value.firstOrNull { it.item.id == draft.itemId }?.count ?: 0
        val errors = draft.validate(current)
        if (errors.isNotEmpty()) return SaveStockResult.Invalid(errors)
        saved += draft
        val change = draft.change(current)
        moves.update { it + StockMove(it.size + 1L, draft.itemId, change, draft.kind, 0L, draft.unitCost, draft.reason.takeIf { draft.kind == StockKind.ADJUST }, draft.note) }
        lines.update { all -> all.map { if (it.item.id == draft.itemId) it.copy(count = it.count + change) else it } }
        return SaveStockResult.Saved
    }
}
