package com.teraper.printmaster.feature.catalog.types

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.component.PmTopBar
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ItemField
import com.teraper.printmaster.core.model.ItemTemplate
import com.teraper.printmaster.core.model.ItemType
import com.teraper.printmaster.core.model.TypeInTree
import com.teraper.printmaster.feature.catalog.R

@Composable
internal fun ItemTypesRoute(
    onBack: () -> Unit,
    onTypeClick: (Long) -> Unit,
    onAddType: () -> Unit,
    viewModel: ItemTypesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val addedText = state.added?.let { stringResource(R.string.feature_catalog_template_added, it) }
    LaunchedEffect(addedText) {
        if (addedText != null) {
            Toast.makeText(context, addedText, Toast.LENGTH_SHORT).show()
            viewModel.onAddedShown()
        }
    }
    ItemTypesScreen(
        state = state,
        onBack = onBack,
        onTypeClick = onTypeClick,
        onAddType = onAddType,
        onShowTemplates = viewModel::onShowTemplates,
    )
    if (state.showTemplates) {
        TemplatesSheet(itemTemplates(), onPick = viewModel::onTemplatePicked, onDismiss = viewModel::onDismissTemplates)
    }
}

@Composable
internal fun ItemTypesScreen(
    state: ItemTypesUiState,
    onBack: () -> Unit,
    onTypeClick: (Long) -> Unit,
    onAddType: () -> Unit,
    onShowTemplates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(PmTheme.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            PmTopBar(
                title = stringResource(R.string.feature_catalog_types_title),
                navigationLabel = stringResource(R.string.feature_catalog_back),
                onNavigate = onBack,
            )
            if (state.isLoading) return@Column
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(
                        stringResource(if (state.types.isEmpty()) R.string.feature_catalog_types_empty else R.string.feature_catalog_types_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.inkMuted,
                    )
                }
                if (state.types.isNotEmpty()) {
                    item {
                        PmCard(Modifier.fillMaxWidth()) {
                            state.types.forEachIndexed { index, node ->
                                if (index > 0) HorizontalDivider(color = PmTheme.colors.surfaceMuted)
                                TypeRow(node, parent = state.types.firstOrNull { it.type.id == node.type.parentId }?.type, onClick = { onTypeClick(node.type.id) })
                            }
                        }
                    }
                }
                item {
                    PmSecondaryButton(
                        text = stringResource(R.string.feature_catalog_from_template),
                        onClick = onShowTemplates,
                        icon = PmIcons.ItemTypes,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onAddType,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = PmTheme.colors.primary,
            contentColor = PmTheme.colors.onPrimary,
            icon = { Icon(PmIcons.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.feature_catalog_new_type)) },
        )
    }
}

@Composable
private fun TypeRow(node: TypeInTree, parent: ItemType?, onClick: () -> Unit) {
    val type = node.type
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp + 20.dp * node.depth, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (node.depth == 0) PmIcons.Catalog else PmIcons.ItemTypes, contentDescription = null, tint = PmTheme.colors.primary)
        Column(Modifier.weight(1f)) {
            Text(type.name, style = MaterialTheme.typography.titleSmall)
            Text(
                buildList {
                    parent?.let { add(stringResource(R.string.feature_catalog_part_of, it.name)) }
                    if (type.fields.isNotEmpty()) add(type.fields.joinToString(", ") { it.name })
                    else add(pluralStringResource(R.plurals.feature_catalog_field_count, 0, 0))
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = PmTheme.colors.inkMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatesSheet(templates: List<ItemTemplate>, onPick: (ItemTemplate) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmTheme.colors.background,
    ) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(stringResource(R.string.feature_catalog_templates_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.feature_catalog_templates_help),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
            }
            items(templates, key = { it.key }) { template ->
                PmCard(Modifier.fillMaxWidth(), onClick = { onPick(template) }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(template.name, style = MaterialTheme.typography.titleSmall)
                        template.types.forEach { type ->
                            val fields = type.fields.joinToString(", ") { it.name }
                            val depth = generateSequence(type) { t -> template.types.firstOrNull { it.name == t.parentName } }.count() - 1
                            Text(
                                (if (depth > 0) "   ".repeat(depth) + "└ " else "") + type.name + if (fields.isEmpty()) "" else " — $fields",
                                style = MaterialTheme.typography.bodySmall,
                                color = PmTheme.colors.inkMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 700)
@Composable
private fun ItemTypesScreenPreview() {
    val printer = ItemType(1, "Տպիչ", fields = listOf(ItemField(1, 1, "Բրենդ", inTitle = true), ItemField(2, 1, "Տպման տեսակ")))
    PmTheme {
        ItemTypesScreen(
            state = ItemTypesUiState(
                isLoading = false,
                types = listOf(TypeInTree(printer, 0), TypeInTree(ItemType(2, "Քարտրիջ", 1), 1), TypeInTree(ItemType(3, "Չիպ", 2), 2)),
            ),
            onBack = {},
            onTypeClick = {},
            onAddType = {},
            onShowTemplates = {},
        )
    }
}
