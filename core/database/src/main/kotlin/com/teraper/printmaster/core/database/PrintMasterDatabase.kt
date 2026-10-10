package com.teraper.printmaster.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teraper.printmaster.core.database.dao.CallRecordingDao
import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.dao.ExpenseDao
import com.teraper.printmaster.core.database.dao.ImportDao
import com.teraper.printmaster.core.database.dao.LedgerDao
import com.teraper.printmaster.core.database.dao.OrderDao
import com.teraper.printmaster.core.database.dao.PhotoDao
import com.teraper.printmaster.core.database.dao.PriceListDao
import com.teraper.printmaster.core.database.dao.RepairDao
import com.teraper.printmaster.core.database.dao.ReportDao
import com.teraper.printmaster.core.database.dao.SyncDao
import com.teraper.printmaster.core.database.entity.AppProfileEntity
import com.teraper.printmaster.core.database.entity.BrandEntity
import com.teraper.printmaster.core.database.entity.CallRecordingClientEntity
import com.teraper.printmaster.core.database.entity.CallRecordingEntity
import com.teraper.printmaster.core.database.entity.CartridgeChipCrossRef
import com.teraper.printmaster.core.database.entity.CartridgeEntity
import com.teraper.printmaster.core.database.entity.ChargeEntity
import com.teraper.printmaster.core.database.entity.ChipEntity
import com.teraper.printmaster.core.database.entity.ClientAddressEntity
import com.teraper.printmaster.core.database.entity.ClientAliasEntity
import com.teraper.printmaster.core.database.entity.ClientEntity
import com.teraper.printmaster.core.database.entity.ClientPhoneEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterCartridgeEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterEntity
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.database.entity.CompanyMemberEntity
import com.teraper.printmaster.core.database.entity.ExpenseEntity
import com.teraper.printmaster.core.database.entity.ImportBatchEntity
import com.teraper.printmaster.core.database.entity.MasterEntity
import com.teraper.printmaster.core.database.entity.ModelCartridgeCrossRef
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.MatchRejectionEntity
import com.teraper.printmaster.core.database.entity.PayerAccountEntity
import com.teraper.printmaster.core.database.entity.PhotoEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.database.entity.PrinterModelEntity
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import com.teraper.printmaster.core.database.entity.RepairPartEntity
import com.teraper.printmaster.core.database.entity.SyncOutboxEntity
import com.teraper.printmaster.core.database.entity.SyncStateEntity

@Database(
    entities = [
        CompanyEntity::class,
        CompanyMemberEntity::class,
        MasterEntity::class,
        AppProfileEntity::class,
        ClientEntity::class,
        ClientPhoneEntity::class,
        ClientAddressEntity::class,
        ClientAliasEntity::class,
        BrandEntity::class,
        PrinterModelEntity::class,
        CartridgeEntity::class,
        ChipEntity::class,
        ModelCartridgeCrossRef::class,
        CartridgeChipCrossRef::class,
        ClientPrinterEntity::class,
        ClientPrinterCartridgeEntity::class,
        OrderEntity::class,
        RepairPartEntity::class,
        RepairEntity::class,
        RepairItemEntity::class,
        ImportBatchEntity::class,
        ChargeEntity::class,
        PaymentEntity::class,
        CallRecordingEntity::class,
        CallRecordingClientEntity::class,
        PayerAccountEntity::class,
        MatchRejectionEntity::class,
        SyncOutboxEntity::class,
        SyncStateEntity::class,
        PhotoEntity::class,
        ExpenseEntity::class,
    ],
    version = PrintMasterDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // 3: call recordings.
        AutoMigration(from = 2, to = 3),
        // 4: map point of an address.
        AutoMigration(from = 3, to = 4),
        // 5: import fingerprints, payment matching, payer accounts.
        AutoMigration(from = 4, to = 5),
        // 6: original row data, match reason, rejected matches.
        AutoMigration(from = 5, to = 6),
        // 7: sharing with masters (sync ids, outbox, done time).
        AutoMigration(from = 6, to = 7),
        // 8: photos; ids on client printers (two of one model).
        AutoMigration(from = 7, to = 8),
        // 9: expenses typed by hand.
        AutoMigration(from = 8, to = 9),
        // 10: own and attached companies (shared spaces), attached clients and price lists, order hand-over.
        AutoMigration(from = 9, to = 10),
    ],
)
abstract class PrintMasterDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun catalogDao(): CatalogDao
    abstract fun companyDao(): CompanyDao
    abstract fun orderDao(): OrderDao
    abstract fun priceListDao(): PriceListDao
    abstract fun repairDao(): RepairDao
    abstract fun reportDao(): ReportDao
    abstract fun callRecordingDao(): CallRecordingDao
    abstract fun importDao(): ImportDao
    abstract fun syncDao(): SyncDao
    abstract fun photoDao(): PhotoDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        const val VERSION = 10

        /** Oldest version a backup file may have; older ones only existed on development phones. */
        const val OLDEST_RESTORABLE_VERSION = 2

        const val DATABASE_NAME = "printmaster.db"

        /** The only way to build the database, so tests get the same setup as the app. */
        fun create(context: Context, inMemory: Boolean = false): PrintMasterDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, PrintMasterDatabase::class.java)
            } else {
                Room.databaseBuilder(context, PrintMasterDatabase::class.java, DATABASE_NAME)
            }
            return builder
                .addCallback(ForeignKeysOn)
                // Version 1 only ever existed on development phones (before companies were added).
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1)
                .build()
        }
    }
}

/**
 * SQLite ignores foreign keys unless asked; we rely on them for cascades
 * (delete client → phones) and restrictions (client with orders can't be deleted).
 */
private object ForeignKeysOn : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        SyncTriggers.create(db)
    }
}
