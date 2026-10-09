package com.teraper.printmaster.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.model.BilledTotals
import com.teraper.printmaster.core.model.ChargeSource
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.IncomeTotals
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.PaymentMethod
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.RepairCategory
import com.teraper.printmaster.core.model.RepairDraft
import com.teraper.printmaster.core.model.WorkTotals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class OfflineReportsRepositoryTest {

    private val repos = TestRepos()
    private val october = YearMonth.of(2026, 10)

    @After
    fun tearDown() = repos.db.close()

    private suspend fun payment(companyId: Long, clientId: Long?, method: PaymentMethod, dram: Long, date: LocalDate) =
        repos.db.ledgerDao().insertPayment(
            PaymentEntity(
                companyId = companyId, clientId = clientId, method = method, amountMinor = Money.ofDram(dram).minor,
                dateEpochDay = date.toEpochDay(), reference = "$dram$date", rawPayerName = null, orderId = null,
                importBatchId = null, createdAt = 0,
            ),
        )

    @Test
    fun monthTotalsForActiveCompanyOnly() = runTest {
        val alfa = repos.register("Alfa")
        val beta = repos.addCompany("Beta")
        val clientId = (repos.clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId
        repos.priceList.saveItem(PriceItemDraft(category = RepairCategory.CARTRIDGE, name = "Refill", priceDigits = "3000", costDigits = "900"))
        val refill = repos.priceList.observeItems().first().single()
        val orderId = (repos.orders.saveOrder(OrderDraft(clientId = clientId, date = LocalDate.of(2026, 10, 7), description = "Refill")) as SaveOrderResult.Saved).orderId
        repos.repairs.saveRepair(RepairDraft(orderId = orderId).plus(refill).plus(refill))
        // Finished today (7 Oct) and paid in cash.
        repos.repairs.finishOrder(orderId, paidInCash = true)

        payment(alfa, clientId, PaymentMethod.BANK, 10_000, LocalDate.of(2026, 10, 1))
        payment(alfa, clientId, PaymentMethod.BANK, 4_000, LocalDate.of(2026, 9, 30))
        payment(alfa, null, PaymentMethod.BANK, 99_000, LocalDate.of(2026, 10, 2)) // not matched to a client yet
        payment(beta, clientId, PaymentMethod.CASH, 50_000, LocalDate.of(2026, 10, 3))
        repos.db.ledgerDao().insertCharge(
            ChargeEntity(
                companyId = alfa, clientId = clientId, source = ChargeSource.MANUAL, documentNumber = null,
                amountMinor = Money.ofDram(1_500).minor, dateEpochDay = LocalDate.of(2026, 10, 31).toEpochDay(),
                rawName = null, rawTaxId = null, repairId = null, importBatchId = null, createdAt = 0,
            ),
        )

        val report = repos.reports.observeMonth(october).first()
        assertEquals(IncomeTotals(cash = Money.ofDram(6_000), bank = Money.ofDram(10_000)), report.income)
        assertEquals(BilledTotals(repairs = Money.ofDram(6_000), manual = Money.ofDram(1_500)), report.billed)
        assertEquals(WorkTotals(orders = 1, revenue = Money.ofDram(6_000), cost = Money.ofDram(1_800)), report.work)
        assertEquals(70, report.work.marginPercent)

        val history = repos.reports.observeIncomeHistory(october, 3).first()
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), october), history.map { it.month })
        assertEquals(listOf(Money.ZERO, Money.ofDram(4_000), Money.ofDram(16_000)), history.map { it.income.total })

        repos.companies.selectCompany(beta)
        assertEquals(IncomeTotals(cash = Money.ofDram(50_000)), repos.reports.observeMonth(october).first().income)
    }
}
