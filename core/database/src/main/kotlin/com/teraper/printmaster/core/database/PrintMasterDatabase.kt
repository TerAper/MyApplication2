package com.teraper.printmaster.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
abstract class PrintMasterDatabase : RoomDatabase()
