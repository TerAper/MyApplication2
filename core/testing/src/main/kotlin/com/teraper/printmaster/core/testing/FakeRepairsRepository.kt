package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.FinishOrderResult
import com.teraper.printmaster.core.data.repository.RepairsRepository
import com.teraper.printmaster.core.data.repository.SaveRepairResult
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderWork
import com.teraper.printmaster.core.model.Repair
import com.teraper.printmaster.core.model.RepairDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** In-memory repairs; finishing just records the call and marks the work billed. */
class FakeRepairsRepository(initial: List<Repair> = emptyList()) : RepairsRepository {
    val repairs = MutableStateFlow(initial)
    val billed = MutableStateFlow<Map<Long, Money>>(emptyMap())
    val saved = mutableListOf<RepairDraft>()
    val finished = mutableListOf<Pair<Long, Boolean>>()
    val reopened = mutableListOf<Long>()

    override fun observeOrderWork(orderId: Long): Flow<OrderWork> = combine(repairs, billed) { list, billed ->
        OrderWork(list.filter { it.orderId == orderId }, isBilled = orderId in billed, paidCash = billed[orderId] ?: Money.ZERO)
    }

    override fun observeRepair(id: Long): Flow<Repair?> = repairs.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun saveRepair(draft: RepairDraft): SaveRepairResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveRepairResult.Invalid(errors)
        if (draft.orderId in billed.value) return SaveRepairResult.Locked
        saved += draft
        val id = if (draft.isNew) (repairs.value.maxOfOrNull { it.id } ?: 0) + 1 else draft.id
        val repair = Repair(id, draft.orderId, note = draft.note, lines = draft.lines)
        repairs.value = repairs.value.filterNot { it.id == id } + repair
        return SaveRepairResult.Saved(id)
    }

    override suspend fun deleteRepair(id: Long): Boolean {
        val repair = repairs.value.firstOrNull { it.id == id } ?: return false
        if (repair.orderId in billed.value) return false
        repairs.value = repairs.value - repair
        return true
    }

    override suspend fun finishOrder(orderId: Long, paidInCash: Boolean, finishedAt: java.time.Instant?): FinishOrderResult {
        val work = repairs.value.filter { it.orderId == orderId }
        if (work.none { it.total.isPositive }) return FinishOrderResult.NO_WORK
        finished += orderId to paidInCash
        billed.value = billed.value + (orderId to if (paidInCash) work.map { it.total }.fold(Money.ZERO, Money::plus) else Money.ZERO)
        return FinishOrderResult.FINISHED
    }

    override suspend fun reopenOrder(orderId: Long) {
        reopened += orderId
        billed.value = billed.value - orderId
    }
}
