package com.teraper.printmaster.feature.catalog.types

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemTemplate
import com.teraper.printmaster.core.model.TypeTemplate
import com.teraper.printmaster.feature.catalog.R

/*
 * Ready sets of types for common businesses, in the app's language. Parents come before their parts.
 * Types are matched by name when a template is added again or sync makes them on another phone,
 * so parts of different templates have their own names ("Car part", "Printer part").
 */

@Composable
private fun text(key: Int) = stringResource(key)

@Composable
private fun field(name: Int, kind: FieldKind = FieldKind.TEXT, options: Int? = null, inTitle: Boolean = false) =
    FieldDraft(name = text(name), kind = kind, options = options?.let { text(it) }.orEmpty(), inTitle = inTitle)

@Composable
internal fun itemTemplates(): List<ItemTemplate> = listOf(
    ItemTemplate(
        key = "printers",
        name = text(R.string.feature_catalog_tpl_printers),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_printer),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_print_type, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_print_type),
                    field(R.string.feature_catalog_tpl_f_color_mode, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_color_mode),
                    field(R.string.feature_catalog_tpl_f_format, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_format),
                    field(R.string.feature_catalog_tpl_f_functions, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_functions),
                    field(R.string.feature_catalog_tpl_f_wifi, FieldKind.YES_NO),
                    field(R.string.feature_catalog_tpl_f_network, FieldKind.YES_NO),
                    field(R.string.feature_catalog_tpl_f_duplex, FieldKind.YES_NO),
                ),
                hasSerial = true,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_cartridge),
                parentName = text(R.string.feature_catalog_tpl_t_printer),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_ink_color, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_ink_color),
                    field(R.string.feature_catalog_tpl_f_yield, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                    field(R.string.feature_catalog_tpl_f_refill, FieldKind.YES_NO),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_chip),
                parentName = text(R.string.feature_catalog_tpl_t_cartridge),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_yield, FieldKind.NUMBER),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = false,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_toner),
                parentName = text(R.string.feature_catalog_tpl_t_cartridge),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_ink_color, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_ink_color),
                    field(R.string.feature_catalog_tpl_f_weight_g, FieldKind.NUMBER),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = false,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_drum),
                parentName = text(R.string.feature_catalog_tpl_t_printer),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_yield, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_printer_part),
                parentName = text(R.string.feature_catalog_tpl_t_printer),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_part_no),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
        ),
    ),
    ItemTemplate(
        key = "clothes",
        name = text(R.string.feature_catalog_tpl_clothes),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_clothing),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_category, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_clothing_cat),
                    field(R.string.feature_catalog_tpl_f_size, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_size),
                    field(R.string.feature_catalog_tpl_f_color),
                    field(R.string.feature_catalog_tpl_f_material),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                    field(R.string.feature_catalog_tpl_f_for_adult, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_for_adult),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
        ),
    ),
    ItemTemplate(
        key = "kids",
        name = text(R.string.feature_catalog_tpl_kids),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_child),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_birth, FieldKind.DATE),
                    field(R.string.feature_catalog_tpl_f_sex, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_sex),
                    field(R.string.feature_catalog_tpl_f_height, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_weight, FieldKind.NUMBER),
                ),
                hasSerial = false,
                perClient = true,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_kid_shoe),
                parentName = text(R.string.feature_catalog_tpl_t_child),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_size_num, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_for_kid, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_for_kid),
                    field(R.string.feature_catalog_tpl_f_shoe_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_shoe_kind),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_kid_pants),
                parentName = text(R.string.feature_catalog_tpl_t_child),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_height_size, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_for_kid, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_for_kid),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_kid_top),
                parentName = text(R.string.feature_catalog_tpl_t_child),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_height_size, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_for_kid, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_for_kid),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_kid_jacket),
                parentName = text(R.string.feature_catalog_tpl_t_child),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_height_size, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_for_kid, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_for_kid),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
        ),
    ),
    ItemTemplate(
        key = "shoes",
        name = text(R.string.feature_catalog_tpl_shoes),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_shoes),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_shoe_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_shoe_kind),
                    field(R.string.feature_catalog_tpl_f_size_num, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_for_adult, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_for_adult),
                    field(R.string.feature_catalog_tpl_f_color),
                    field(R.string.feature_catalog_tpl_f_material),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
        ),
    ),
    ItemTemplate(
        key = "electronics",
        name = text(R.string.feature_catalog_tpl_electronics),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_device),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_device_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_device_kind),
                    field(R.string.feature_catalog_tpl_f_model_code),
                    field(R.string.feature_catalog_tpl_f_color),
                    field(R.string.feature_catalog_tpl_f_storage, FieldKind.NUMBER),
                ),
                hasSerial = true,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_screen),
                parentName = text(R.string.feature_catalog_tpl_t_device),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_quality, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_quality),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_battery),
                parentName = text(R.string.feature_catalog_tpl_t_device),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_capacity, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_port),
                parentName = text(R.string.feature_catalog_tpl_t_device),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_back),
                parentName = text(R.string.feature_catalog_tpl_t_device),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_camera),
                parentName = text(R.string.feature_catalog_tpl_t_device),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
        ),
    ),
    ItemTemplate(
        key = "cars",
        name = text(R.string.feature_catalog_tpl_cars),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_car),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_year, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_plate),
                    field(R.string.feature_catalog_tpl_f_fuel, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_fuel),
                    field(R.string.feature_catalog_tpl_f_engine, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_mileage, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_color),
                ),
                hasSerial = true,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_car_part),
                parentName = text(R.string.feature_catalog_tpl_t_car),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_part_no),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_tire),
                parentName = text(R.string.feature_catalog_tpl_t_car),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_tire_size),
                    field(R.string.feature_catalog_tpl_f_season, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_season),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_oil),
                parentName = text(R.string.feature_catalog_tpl_t_car),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_viscosity),
                    field(R.string.feature_catalog_tpl_f_volume_l, FieldKind.NUMBER),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = false,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_filter),
                parentName = text(R.string.feature_catalog_tpl_t_car),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_filter_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_filter_kind),
                    field(R.string.feature_catalog_tpl_f_part_no),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = false,
            ),
        ),
    ),
    ItemTemplate(
        key = "computers",
        name = text(R.string.feature_catalog_tpl_computers),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_computer),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_pc_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_pc_kind),
                    field(R.string.feature_catalog_tpl_f_cpu),
                    field(R.string.feature_catalog_tpl_f_ram_gb, FieldKind.NUMBER),
                    field(R.string.feature_catalog_tpl_f_storage, FieldKind.NUMBER),
                ),
                hasSerial = true,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_ram),
                parentName = text(R.string.feature_catalog_tpl_t_computer),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_ram_gb, FieldKind.NUMBER),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = false,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_disk),
                parentName = text(R.string.feature_catalog_tpl_t_computer),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_disk_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_disk_kind),
                    field(R.string.feature_catalog_tpl_f_disk_gb, FieldKind.NUMBER),
                ),
                hasSerial = true,
                perClient = false,
                hasPhotos = false,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_psu),
                parentName = text(R.string.feature_catalog_tpl_t_computer),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_watts, FieldKind.NUMBER),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = false,
            ),
        ),
    ),
    ItemTemplate(
        key = "appliances",
        name = text(R.string.feature_catalog_tpl_appliances),
        types = listOf(
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_appliance),
                parentName = null,
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_appliance_kind, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_appliance_kind),
                    field(R.string.feature_catalog_tpl_f_model_code),
                ),
                hasSerial = true,
                perClient = false,
                hasPhotos = true,
            ),
            TypeTemplate(
                name = text(R.string.feature_catalog_tpl_t_appliance_part),
                parentName = text(R.string.feature_catalog_tpl_t_appliance),
                fields = listOf(
                    field(R.string.feature_catalog_tpl_f_brand, inTitle = true),
                    field(R.string.feature_catalog_tpl_f_part_no),
                    field(R.string.feature_catalog_tpl_f_origin, FieldKind.CHOICE, options = R.string.feature_catalog_tpl_o_origin),
                ),
                hasSerial = false,
                perClient = false,
                hasPhotos = true,
            ),
        ),
    ),
)
