package com.teraper.printmaster.core.data

import androidx.test.core.app.ApplicationProvider
import com.teraper.printmaster.core.data.analytics.NoAnalytics
import com.teraper.printmaster.core.data.repository.CatalogWriter
import com.teraper.printmaster.core.data.repository.ImportColumnsRepository
import com.teraper.printmaster.core.data.repository.OfflineBackupRepository
import com.teraper.printmaster.core.data.repository.OfflineCallRecordingsRepository
import com.teraper.printmaster.core.data.repository.OfflineReportsRepository
import com.teraper.printmaster.core.data.repository.OfflineClientsRepository
import com.teraper.printmaster.core.data.repository.OfflineImportRepository
import com.teraper.printmaster.core.data.repository.OfflinePhotoRepository
import com.teraper.printmaster.core.data.repository.OfflineCompaniesRepository
import com.teraper.printmaster.core.data.repository.OfflinePaymentsRepository
import com.teraper.printmaster.core.data.repository.OfflineOrdersRepository
import com.teraper.printmaster.core.data.repository.OfflinePriceListRepository
import com.teraper.printmaster.core.data.repository.OfflineCatalogRepository
import com.teraper.printmaster.core.data.repository.OfflineClientItemsRepository
import com.teraper.printmaster.core.data.repository.OfflineRepairsRepository
import com.teraper.printmaster.core.data.repository.OfflineWarehouseRepository
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.data.sync.SyncBackend
import com.teraper.printmaster.core.data.sync.SyncEngine
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.ImportColumn
import com.teraper.printmaster.core.model.ImportColumns
import com.teraper.printmaster.core.model.ImportKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import com.teraper.printmaster.core.data.repository.SaveClientItemResult
import com.teraper.printmaster.core.data.repository.SaveTypeResult
import com.teraper.printmaster.core.model.ClientItemDraft
import com.teraper.printmaster.core.model.ClientPartDraft
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.ItemDraft
import com.teraper.printmaster.core.model.ItemTypeDraft
import com.teraper.printmaster.core.model.LinkDraft

/** Real repositories over an in-memory database, wired like the app does. */
internal class TestRepos(clock: Clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC)) {
    val db: PrintMasterDatabase = PrintMasterDatabase.create(ApplicationProvider.getApplicationContext(), inMemory = true)
    val companies = OfflineCompaniesRepository(db, db.companyDao(), clock)
    val clients = OfflineClientsRepository(db, db.clientDao(), clock, companies)
    val payments = OfflinePaymentsRepository(db.ledgerDao(), db.clientDao(), db.companyDao(), clock, companies)
    val priceList = OfflinePriceListRepository(db, db.priceListDao())
    val catalog = OfflineCatalogRepository(db, db.catalogDao(), CatalogWriter(db.catalogDao()))
    val clientItems = OfflineClientItemsRepository(db, db.catalogDao(), CatalogWriter(db.catalogDao()))
    val orders = OfflineOrdersRepository(db.orderDao(), db.clientDao(), companies, clock)
    val warehouse = run {
        // A fresh shared warehouse for every test.
        ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("warehouse", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        OfflineWarehouseRepository(ApplicationProvider.getApplicationContext(), db, db.stockDao(), db.catalogDao(), CatalogWriter(db.catalogDao()), companies, clock)
    }
    val repairs = OfflineRepairsRepository(db, db.repairDao(), db.orderDao(), db.ledgerDao(), clientItems, warehouse, clock, NoAnalytics)
    val reports = OfflineReportsRepository(db.reportDao(), db.expenseDao(), companies)
    val expenses = com.teraper.printmaster.core.data.repository.OfflineExpensesRepository(db.expenseDao(), companies, clock)
    val calls = OfflineCallRecordingsRepository(ApplicationProvider.getApplicationContext(), db, db.callRecordingDao(), clock)
    val imports = OfflineImportRepository(ApplicationProvider.getApplicationContext(), db, db.importDao(), db.companyDao(), companies, clock, NoAnalytics, DefaultImportColumns)
    val photos = OfflinePhotoRepository(ApplicationProvider.getApplicationContext(), db.photoDao(), clock)
    val backup = OfflineBackupRepository(ApplicationProvider.getApplicationContext(), db, clock, NoAnalytics)

    private val testClock = clock

    fun syncEngine(backend: SyncBackend) = SyncEngine(
        db, db.syncDao(), db.clientDao(), db.orderDao(), db.repairDao(), db.catalogDao(), CatalogWriter(db.catalogDao()),
        db.companyDao(), repairs, backend, testClock,
    )

    /** Registers as a master with one company and returns its id. */
    suspend fun register(companyName: String = "Main"): Long =
        (companies.register(AccountMode.MASTER, "Master", CompanyDraft(name = companyName)) as SaveCompanyResult.Saved).companyId

    suspend fun addCompany(name: String): Long =
        (companies.saveCompany(CompanyDraft(name = name)) as SaveCompanyResult.Saved).companyId

    /** Printer (Brand before the name) → Cartridge types, made once. */
    suspend fun printerTypes(): Pair<Long, Long> {
        val types = catalog.observeTypes().first()
        types.firstOrNull { it.name == "Printer" }?.let { printer -> return printer.id to types.first { it.name == "Cartridge" }.id }
        val printer = (catalog.saveType(ItemTypeDraft(name = "Printer", fields = listOf(FieldDraft(name = "Brand", inTitle = true)))) as SaveTypeResult.Saved).typeId
        val cartridge = (catalog.saveType(ItemTypeDraft(name = "Cartridge", parentId = printer)) as SaveTypeResult.Saved).typeId
        return printer to cartridge
    }

    /** Gives the client an "HP M125" with cartridge "CF283A"; returns (client item id, part id). */
    suspend fun addPrinter(clientId: Long, place: String = "", name: String = "M125", serial: String = ""): Pair<Long, Long> {
        val (printer, cartridge) = printerTypes()
        val brand = catalog.observeTypes().first().first { it.id == printer }.fields.first().id
        val part = LinkDraft(typeId = cartridge, title = "CF283A")
        val draft = ClientItemDraft(
            clientId = clientId,
            item = ItemDraft(typeId = printer, name = name, values = mapOf(brand to "HP"), parts = listOf(part)),
            parts = listOf(ClientPartDraft(typeId = cartridge, title = part.title)),
            place = place,
            serialNumber = serial,
        )
        val id = (clientItems.saveClientItem(draft) as SaveClientItemResult.Saved).clientItemId
        return id to clientItems.getClientItem(id)!!.parts.single().id
    }

    fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}

/** The default column titles, as on a fresh install. */
internal object DefaultImportColumns : ImportColumnsRepository {
    override fun observeColumns() = flowOf(ImportColumns.DEFAULT)
    override suspend fun setTitles(column: ImportColumn, titles: List<String>) = Unit
    override suspend fun reset(kind: ImportKind) = Unit
}
