package com.teraper.printmaster.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.FieldKind
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemTitles

/**
 * The inputs for a type's fields, as the customer set them up: text (with values used before as
 * one-tap choices), number, a choice of the typed options, or yes/no.
 */
@Composable
fun PmItemFields(
    fields: List<ItemField>,
    values: Map<Long, String>,
    onValueChange: (fieldId: Long, value: String) -> Unit,
    modifier: Modifier = Modifier,
    /** Values already used per text field, e.g. brands. */
    suggestions: Map<Long, List<String>> = emptyMap(),
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        fields.forEach { field ->
            val value = values[field.id].orEmpty()
            when (field.kind) {
                FieldKind.TEXT -> {
                    PmTextField(
                        value = value,
                        onValueChange = { onValueChange(field.id, it) },
                        label = field.name,
                        capitalization = KeyboardCapitalization.Words,
                    )
                    val used = suggestions[field.id].orEmpty()
                    if (used.isNotEmpty()) {
                        ChoiceRow(used, selected = value) { onValueChange(field.id, if (CatalogNames.key(it) == CatalogNames.key(value)) "" else it) }
                    }
                }
                FieldKind.NUMBER -> PmTextField(
                    value = value,
                    onValueChange = { text -> onValueChange(field.id, text.filter { it.isDigit() || it == '.' || it == ',' }) },
                    label = field.name,
                    keyboardType = KeyboardType.Decimal,
                )
                FieldKind.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(field.name, style = MaterialTheme.typography.labelMedium, color = PmTheme.colors.inkSecondary)
                    ChoiceRow(field.options, selected = value) { onValueChange(field.id, if (it == value) "" else it) }
                }
                FieldKind.YES_NO -> {
                    val on = value == ItemTitles.YES
                    Row(
                        Modifier.fillMaxWidth().clickable { onValueChange(field.id, if (on) "" else ItemTitles.YES) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(field.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = on,
                            onCheckedChange = { onValueChange(field.id, if (it) ItemTitles.YES else "") },
                            colors = SwitchDefaults.colors(checkedTrackColor = PmTheme.colors.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onClick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            PmFilterChip(text = option, selected = CatalogNames.key(option) == CatalogNames.key(selected), onClick = { onClick(option) })
        }
    }
}

/** Values typed before in each text field of the catalog, most used first: one-tap choices. */
fun usedValues(items: List<com.teraper.printmaster.core.model.CatalogItem>, fields: List<ItemField>): Map<Long, List<String>> =
    fields.filter { it.kind == FieldKind.TEXT }.associate { field ->
        field.id to items.mapNotNull { it.values[field.id]?.takeIf(String::isNotBlank) }
            .groupBy(CatalogNames::key).values.sortedByDescending { it.size }.map { it.first() }.take(MAX_SUGGESTIONS)
    }.filterValues { it.isNotEmpty() }

private const val MAX_SUGGESTIONS = 12
