package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrintType

@Entity(tableName = "brands", indices = [Index(value = ["name"], unique = true)])
data class BrandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "printer_models",
    foreignKeys = [
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brand_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["brand_id", "name"], unique = true)],
)
data class PrinterModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "brand_id") val brandId: Long,
    val name: String,
    @ColumnInfo(name = "print_type") val printType: PrintType,
    @ColumnInfo(name = "color_type") val colorType: ColorType,
)

@Entity(tableName = "cartridges", indices = [Index(value = ["name"], unique = true)])
data class CartridgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "chips", indices = [Index(value = ["name"], unique = true)])
data class ChipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Which cartridges fit which printer model (one cartridge can fit many models). */
@Entity(
    tableName = "model_cartridges",
    primaryKeys = ["model_id", "cartridge_id"],
    foreignKeys = [
        ForeignKey(
            entity = PrinterModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["model_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CartridgeEntity::class,
            parentColumns = ["id"],
            childColumns = ["cartridge_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["cartridge_id"])],
)
data class ModelCartridgeCrossRef(
    @ColumnInfo(name = "model_id") val modelId: Long,
    @ColumnInfo(name = "cartridge_id") val cartridgeId: Long,
)

@Entity(
    tableName = "cartridge_chips",
    primaryKeys = ["cartridge_id", "chip_id"],
    foreignKeys = [
        ForeignKey(
            entity = CartridgeEntity::class,
            parentColumns = ["id"],
            childColumns = ["cartridge_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChipEntity::class,
            parentColumns = ["id"],
            childColumns = ["chip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chip_id"])],
)
data class CartridgeChipCrossRef(
    @ColumnInfo(name = "cartridge_id") val cartridgeId: Long,
    @ColumnInfo(name = "chip_id") val chipId: Long,
)

/** A printer a client owns. */
@Entity(
    tableName = "client_printers",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PrinterModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["model_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["client_id"]), Index(value = ["model_id"])],
)
data class ClientPrinterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id") val clientId: Long,
    @ColumnInfo(name = "model_id") val modelId: Long,
    /** Where the printer stands, e.g. "Accounting room". */
    val location: String = "",
    val note: String = "",
)

/** A cartridge attached to one of the client's printers. */
@Entity(
    tableName = "client_printer_cartridges",
    foreignKeys = [
        ForeignKey(
            entity = ClientPrinterEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_printer_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CartridgeEntity::class,
            parentColumns = ["id"],
            childColumns = ["cartridge_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["client_printer_id"]), Index(value = ["cartridge_id"])],
)
data class ClientPrinterCartridgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_printer_id") val clientPrinterId: Long,
    @ColumnInfo(name = "cartridge_id") val cartridgeId: Long,
    val note: String = "",
)
