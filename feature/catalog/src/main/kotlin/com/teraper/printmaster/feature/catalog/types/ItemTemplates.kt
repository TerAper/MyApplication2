package com.teraper.printmaster.feature.catalog.types

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.teraper.printmaster.core.model.FieldDraft
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemTemplate
import com.teraper.printmaster.core.model.TypeTemplate
import com.teraper.printmaster.feature.catalog.R

/**
 * Children's things: each child is filled in on the client (birth date gives the age, sex), and their
 * shoes, pants and shirts by size, so an order for a child can offer what fits.
 */
@Composable
private fun kidsTemplate(brand: FieldDraft, color: FieldDraft): ItemTemplate {
    val child = stringResource(R.string.feature_catalog_tpl_child)
    val forWhom = FieldDraft(name = stringResource(R.string.feature_catalog_tpl_for), kind = FieldKind.CHOICE, options = stringResource(R.string.feature_catalog_tpl_sexes))
    val size = FieldDraft(name = stringResource(R.string.feature_catalog_tpl_size), kind = FieldKind.NUMBER)
    fun clothing(name: String) = TypeTemplate(name, parentName = child, fields = listOf(brand, size, forWhom, color), hasSerial = false)
    return ItemTemplate(
        key = "kids",
        name = stringResource(R.string.feature_catalog_tpl_kids),
        types = listOf(
            TypeTemplate(
                child,
                fields = listOf(
                    FieldDraft(name = stringResource(R.string.feature_catalog_tpl_birth), kind = FieldKind.DATE),
                    FieldDraft(name = stringResource(R.string.feature_catalog_tpl_sex), kind = FieldKind.CHOICE, options = stringResource(R.string.feature_catalog_tpl_sexes)),
                ),
                hasSerial = false,
                perClient = true,
            ),
            clothing(stringResource(R.string.feature_catalog_tpl_shoe)),
            clothing(stringResource(R.string.feature_catalog_tpl_pants)),
            clothing(stringResource(R.string.feature_catalog_tpl_shirt)),
        ),
    )
}

/** Ready sets of types for common businesses, in the app's language. Parents come before their parts. */
@Composable
internal fun itemTemplates(): List<ItemTemplate> {
    val brand = FieldDraft(name = stringResource(R.string.feature_catalog_tpl_brand), inTitle = true)
    val color = FieldDraft(name = stringResource(R.string.feature_catalog_tpl_color))
    fun choice(name: String, options: String) = FieldDraft(name = name, kind = FieldKind.CHOICE, options = options)

    val printer = stringResource(R.string.feature_catalog_tpl_printer)
    val cartridge = stringResource(R.string.feature_catalog_tpl_cartridge)
    val device = stringResource(R.string.feature_catalog_tpl_device)
    val car = stringResource(R.string.feature_catalog_tpl_car)
    return listOf(
        ItemTemplate(
            key = "printers",
            name = stringResource(R.string.feature_catalog_tpl_printers),
            types = listOf(
                TypeTemplate(
                    printer,
                    fields = listOf(
                        brand,
                        choice(stringResource(R.string.feature_catalog_tpl_print_type), stringResource(R.string.feature_catalog_tpl_print_types)),
                        choice(stringResource(R.string.feature_catalog_tpl_color), stringResource(R.string.feature_catalog_tpl_colors_bw)),
                    ),
                ),
                TypeTemplate(cartridge, parentName = printer),
                TypeTemplate(stringResource(R.string.feature_catalog_tpl_chip), parentName = cartridge),
            ),
        ),
        ItemTemplate(
            key = "clothes",
            name = stringResource(R.string.feature_catalog_tpl_clothes),
            types = listOf(
                TypeTemplate(
                    stringResource(R.string.feature_catalog_tpl_clothing),
                    fields = listOf(
                        brand,
                        choice(stringResource(R.string.feature_catalog_tpl_size), stringResource(R.string.feature_catalog_tpl_sizes)),
                        color,
                        FieldDraft(name = stringResource(R.string.feature_catalog_tpl_material)),
                    ),
                ),
            ),
        ),
        kidsTemplate(brand, color),
        ItemTemplate(
            key = "shoes",
            name = stringResource(R.string.feature_catalog_tpl_shoes),
            types = listOf(
                TypeTemplate(
                    stringResource(R.string.feature_catalog_tpl_shoes),
                    fields = listOf(brand, FieldDraft(name = stringResource(R.string.feature_catalog_tpl_size), kind = FieldKind.NUMBER), color),
                ),
            ),
        ),
        ItemTemplate(
            key = "electronics",
            name = stringResource(R.string.feature_catalog_tpl_electronics),
            types = listOf(
                TypeTemplate(
                    device,
                    fields = listOf(brand, choice(stringResource(R.string.feature_catalog_tpl_device_kind), stringResource(R.string.feature_catalog_tpl_device_kinds))),
                ),
                TypeTemplate(stringResource(R.string.feature_catalog_tpl_battery), parentName = device),
                TypeTemplate(stringResource(R.string.feature_catalog_tpl_screen), parentName = device),
            ),
        ),
        ItemTemplate(
            key = "cars",
            name = stringResource(R.string.feature_catalog_tpl_cars),
            types = listOf(
                TypeTemplate(
                    car,
                    fields = listOf(
                        brand,
                        FieldDraft(name = stringResource(R.string.feature_catalog_tpl_year), kind = FieldKind.NUMBER),
                        FieldDraft(name = stringResource(R.string.feature_catalog_tpl_plate)),
                    ),
                ),
                TypeTemplate(stringResource(R.string.feature_catalog_tpl_car_part), parentName = car, fields = listOf(brand)),
            ),
        ),
    )
}
