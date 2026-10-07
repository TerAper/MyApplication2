package com.teraper.printmaster.core.data

import androidx.test.core.app.ApplicationProvider
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflineCompaniesRepository
import com.teraper.printmaster.core.data.repository.OfflinePaymentsRepository
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyDraft
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Real repositories over an in-memory database, wired like the app does. */
internal class TestRepos(clock: Clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)) {
    val db: PrintMasterDatabase = PrintMasterDatabase.create(ApplicationProvider.getApplicationContext(), inMemory = true)
    val companies = OfflineCompaniesRepository(db, db.companyDao(), clock)
    val clients = OfflineClientsRepository(db, db.clientDao(), clock, companies)
    val payments = OfflinePaymentsRepository(db.ledgerDao(), db.clientDao(), db.companyDao(), clock, companies)

    /** Registers as a master with one company and returns its id. */
    suspend fun register(companyName: String = "Main"): Long =
        (companies.register(AccountMode.MASTER, "Master", CompanyDraft(name = companyName)) as SaveCompanyResult.Saved).companyId

    suspend fun addCompany(name: String): Long =
        (companies.saveCompany(CompanyDraft(name = name)) as SaveCompanyResult.Saved).companyId

    fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}
