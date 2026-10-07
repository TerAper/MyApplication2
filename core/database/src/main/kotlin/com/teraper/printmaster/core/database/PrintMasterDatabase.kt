package com.teraper.printmaster.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teraper.printmaster.core.database.dao.ClientDao
import com.teraper.printmaster.core.database.entity.BrandEntity
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
import com.teraper.printmaster.core.database.entity.ImportBatchEntity
import com.teraper.printmaster.core.database.entity.ModelCartridgeCrossRef
import com.teraper.printmaster.core.database.entity.OrderEntity
import com.teraper.printmaster.core.database.entity.PaymentEntity
import com.teraper.printmaster.core.database.entity.PrinterModelEntity
import com.teraper.printmaster.core.database.entity.RepairEntity
import com.teraper.printmaster.core.database.entity.RepairItemEntity
import com.teraper.printmaster.core.database.entity.RepairPartEntity

@Database(
    entities = [
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
    ],
    version = 1,
    exportSchema = true,
)
abstract class PrintMasterDatabase : RoomDatabase() {
    abstract fun clientDao(): ClientDao

    companion object {
        private const val DATABASE_NAME = "printmaster.db"

        /** The only way to build the database, so tests get the same setup as the app. */
        fun create(context: Context, inMemory: Boolean = false): PrintMasterDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, PrintMasterDatabase::class.java)
            } else {
                Room.databaseBuilder(context, PrintMasterDatabase::class.java, DATABASE_NAME)
            }
            return builder.addCallback(ForeignKeysOn).build()
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
    }
}
