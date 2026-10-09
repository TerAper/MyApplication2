package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.MonthIncome
import com.teraper.printmaster.core.model.MonthReport
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Money totals of the active company. */
interface ReportsRepository {

    fun observeMonth(month: YearMonth): Flow<MonthReport>

    /** Money received in each of the [count] months up to [last], oldest first. */
    fun observeIncomeHistory(last: YearMonth, count: Int): Flow<List<MonthIncome>>
}
