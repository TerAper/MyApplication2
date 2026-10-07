package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.LedgerDao
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.LedgerEntry
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.MoneyEntryDraft
import com.teraper.printmaster.core.model.MoneyEntryError
import com.teraper.printmaster.core.model.MoneyEntryKind
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.sortedNewestFirst
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class OfflinePaymentsRepository @Inject constructor(
    private val ledgerDao: LedgerDao,
    private val clientDao: ClientDao,
    private val companyDao: CompanyDao,
    private val clock: Clock,
    private val companies: CompaniesRepository,
) : PaymentsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> forActiveCompany(empty: T, block: (companyId: Long) -> Flow<T>): Flow<T> =
        companies.observeActiveCompany().map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
            if (id == null) flowOf(empty) else block(id)
        }

    override fun observeLedger(clientId: Long): Flow<List<LedgerEntry>> = forActiveCompany(emptyList()) { companyId ->
        combine(
            ledgerDao.observeCharges(clientId, companyId),
            ledgerDao.observePayments(clientId, companyId),
        ) { charges, payments ->
            (charges.map { it.toEntry() } + payments.map { it.toEntry() }).sortedNewestFirst()
        }
    }

    override fun observeIncome(from: LocalDate, to: LocalDate): Flow<IncomeTotals> = forActiveCompany(IncomeTotals()) { companyId ->
        ledgerDao.observeIncomeByMethod(companyId, from.toEpochDay(), to.toEpochDay()).map { rows ->
            val by = rows.associate { it.method to Money(it.totalMinor) }
            IncomeTotals(cash = by[PaymentMethod.CASH] ?: Money.ZERO, bank = by[PaymentMethod.BANK] ?: Money.ZERO)
        }
    }

    override suspend fun saveMoneyEntry(draft: MoneyEntryDraft): SaveMoneyEntryResult {
        val errors = draft.validate().toMutableSet()
        val clientId = draft.clientId
        val companyId = draft.companyId
        if (clientId != null && clientDao.getClient(clientId) == null) errors += MoneyEntryError.CLIENT_REQUIRED
        if (companyId != null && companyDao.getCompany(companyId) == null) errors += MoneyEntryError.COMPANY_REQUIRED
        if (errors.isNotEmpty() || clientId == null || companyId == null) return SaveMoneyEntryResult.Invalid(errors)

        val now = clock.millis()
        val note = draft.note.trim()
        val id = when (draft.kind) {
            MoneyEntryKind.CASH_PAYMENT -> ledgerDao.insertPayment(
                PaymentEntity(
                    companyId = companyId,
                    clientId = clientId,
                    method = PaymentMethod.CASH,
                    amountMinor = draft.amount.minor,
                    dateEpochDay = draft.date.toEpochDay(),
                    reference = null,
                    rawPayerName = null,
                    orderId = null,
                    importBatchId = null,
                    note = note,
                    createdAt = now,
                ),
            )
            MoneyEntryKind.MANUAL_CHARGE -> ledgerDao.insertCharge(
                ChargeEntity(
                    companyId = companyId,
                    clientId = clientId,
                    source = ChargeSource.MANUAL,
                    documentNumber = null,
                    amountMinor = draft.amount.minor,
                    dateEpochDay = draft.date.toEpochDay(),
                    rawName = null,
                    rawTaxId = null,
                    repairId = null,
                    importBatchId = null,
                    note = note,
                    createdAt = now,
                ),
            )
        }
        return SaveMoneyEntryResult.Saved(id)
    }

    override suspend fun deleteEntry(entry: LedgerEntry): Boolean = when (entry) {
        is LedgerEntry.Charge -> entry.canDelete && ledgerDao.deleteManualCharge(entry.id) > 0
        is LedgerEntry.Payment -> entry.canDelete && ledgerDao.deleteCashPayment(entry.id) > 0
    }
}

private fun ChargeEntity.toEntry() = LedgerEntry.Charge(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    amount = Money(amountMinor),
    note = note,
    createdAt = createdAt,
    source = source,
    documentNumber = documentNumber,
)

private fun PaymentEntity.toEntry() = LedgerEntry.Payment(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    amount = Money(amountMinor),
    note = note,
    createdAt = createdAt,
    method = method,
    reference = reference,
)
