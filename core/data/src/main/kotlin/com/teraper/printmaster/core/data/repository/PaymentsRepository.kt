package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryError
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface PaymentsRepository {

    /** The client's charges and payments, newest first. */
    fun observeLedger(clientId: Long): Flow<List<LedgerEntry>>

    /** Money received from [from] to [to] inclusive, split cash / bank. */
    fun observeIncome(from: LocalDate, to: LocalDate): Flow<IncomeTotals>

    /** Saves a cash payment or a hand-typed debt. */
    suspend fun saveMoneyEntry(draft: MoneyEntryDraft): SaveMoneyEntryResult

    /** Deletes a cash payment or manual debt; false if the entry can't be deleted here. */
    suspend fun deleteEntry(entry: LedgerEntry): Boolean
}

sealed interface SaveMoneyEntryResult {
    data class Saved(val id: Long) : SaveMoneyEntryResult
    data class Invalid(val errors: Set<MoneyEntryError>) : SaveMoneyEntryResult
}
