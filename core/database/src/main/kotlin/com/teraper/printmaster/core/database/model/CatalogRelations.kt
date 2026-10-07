package com.teraper.printmaster.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.teraper.printmaster.core.database.entity.BrandEntity
import com.teraper.printmaster.core.database.entity.CartridgeChipCrossRef
import com.teraper.printmaster.core.database.entity.CartridgeEntity
import com.teraper.printmaster.core.database.entity.ChipEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterCartridgeEntity
import com.teraper.printmaster.core.database.entity.ClientPrinterEntity
import com.teraper.printmaster.core.database.entity.ModelCartridgeCrossRef
import com.teraper.printmaster.core.database.entity.PrinterModelEntity

data class CartridgeWithChips(
    @Embedded val cartridge: CartridgeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(CartridgeChipCrossRef::class, parentColumn = "cartridge_id", entityColumn = "chip_id"),
    )
    val chips: List<ChipEntity>,
)

data class PrinterModelWithDetails(
    @Embedded val model: PrinterModelEntity,
    @Relation(parentColumn = "brand_id", entityColumn = "id")
    val brand: BrandEntity,
    @Relation(
        entity = CartridgeEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(ModelCartridgeCrossRef::class, parentColumn = "model_id", entityColumn = "cartridge_id"),
    )
    val cartridges: List<CartridgeWithChips>,
)

data class ClientPrinterCartridgeWithDetails(
    @Embedded val row: ClientPrinterCartridgeEntity,
    @Relation(entity = CartridgeEntity::class, parentColumn = "cartridge_id", entityColumn = "id")
    val cartridge: CartridgeWithChips,
)

data class ClientPrinterWithDetails(
    @Embedded val printer: ClientPrinterEntity,
    @Relation(entity = PrinterModelEntity::class, parentColumn = "model_id", entityColumn = "id")
    val model: PrinterModelWithDetails,
    @Relation(entity = ClientPrinterCartridgeEntity::class, parentColumn = "id", entityColumn = "client_printer_id")
    val cartridges: List<ClientPrinterCartridgeWithDetails>,
)

/** A count per id, e.g. client printers per model. */
data class IdCount(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "count") val count: Int,
)

data class ModelOwnerRow(
    @ColumnInfo(name = "client_id") val clientId: Long,
    @ColumnInfo(name = "client_name") val clientName: String,
    @ColumnInfo(name = "location") val location: String,
)
