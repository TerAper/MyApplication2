package com.teraper.printmaster.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.designsystem.R
import com.teraper.printmaster.core.model.ColorType
import com.teraper.printmaster.core.model.PrintType
import com.teraper.printmaster.core.model.PrinterModel
import com.teraper.printmaster.core.model.RepairCategory

// Printer words are shown by the client card and the catalog, so they live here once.

@Composable
fun PrintType.label(): String = stringResource(
    when (this) {
        PrintType.LASER -> R.string.core_designsystem_print_laser
        PrintType.INK -> R.string.core_designsystem_print_ink
    },
)

@Composable
fun ColorType.label(): String = stringResource(
    when (this) {
        ColorType.MONO -> R.string.core_designsystem_color_mono
        ColorType.COLOR -> R.string.core_designsystem_color_color
    },
)

/** "Laser · Black/white" */
@Composable
fun PrinterModel.typeLabel(): String = "${printType.label()} · ${colorType.label()}"

/** Price-list group: Cartridge / Printer / Other. */
@Composable
fun RepairCategory.label(): String = stringResource(
    when (this) {
        RepairCategory.CARTRIDGE -> R.string.core_designsystem_category_cartridge
        RepairCategory.PRINTER -> R.string.core_designsystem_category_printer
        RepairCategory.OTHER -> R.string.core_designsystem_category_other
    },
)
