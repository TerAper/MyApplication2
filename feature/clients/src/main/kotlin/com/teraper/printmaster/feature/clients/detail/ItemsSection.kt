package com.teraper.printmaster.feature.clients.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.teraper.printmaster.core.designsystem.component.PhotoStripLabels
import com.teraper.printmaster.core.designsystem.component.PmCard
import com.teraper.printmaster.core.designsystem.component.PmPhotoStrip
import com.teraper.printmaster.core.designsystem.component.PmSecondaryButton
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.ClientItem
import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import com.teraper.printmaster.feature.clients.R

/** Client card → Items tab: what the client owns and which parts each item uses. */
@Composable
internal fun ItemsSection(
    items: List<ClientItem>,
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    itemPhotos: Map<Long, List<Photo>> = emptyMap(),
    partPhotos: Map<Long, List<Photo>> = emptyMap(),
    photoActions: PhotoActions = PhotoActions(),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.feature_clients_no_items),
                modifier = Modifier.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = PmTheme.colors.inkMuted,
            )
        }
        val labels = photoLabels()
        items.forEach { item ->
            ItemCard(item, onClick = { onItemClick(item.id) }) {
                PmPhotoStrip(
                    photos = itemPhotos[item.id].orEmpty(),
                    labels = labels,
                    onAdd = { photoActions.onAdd(PhotoOwner.CLIENT_ITEM, item.id, it) },
                    onDelete = photoActions.onDelete,
                )
                // Each part of this item has its own photos (label, damage…).
                item.parts.forEach { part ->
                    Text(part.item.title, style = MaterialTheme.typography.labelMedium, color = PmTheme.colors.inkSecondary)
                    PmPhotoStrip(
                        photos = partPhotos[part.id].orEmpty(),
                        labels = labels,
                        onAdd = { photoActions.onAdd(PhotoOwner.CLIENT_ITEM_PART, part.id, it) },
                        onDelete = photoActions.onDelete,
                        size = 52.dp,
                    )
                }
            }
        }
        PmSecondaryButton(
            text = stringResource(R.string.feature_clients_add_item),
            onClick = onAddItem,
            icon = PmIcons.Add,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ItemCard(item: ClientItem, onClick: () -> Unit, photos: @Composable () -> Unit = {}) {
    PmCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PmIcons.Item, contentDescription = null, tint = PmTheme.colors.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.item.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOf(item.item.type.name, item.item.details, item.place).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = PmTheme.colors.inkMuted,
                )
                // "Cartridge: CF283A (chip X)": parts by type, each with what goes into it.
                item.parts.groupBy { it.item.type.name }.forEach { (typeName, parts) ->
                    val names = parts.joinToString(", ") { part ->
                        val inner = part.item.parts.joinToString(", ") { it.title }
                        if (inner.isEmpty()) part.item.title else "${part.item.title} ($inner)"
                    }
                    Text(
                        stringResource(R.string.feature_clients_item_parts_value, typeName, names),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PmTheme.colors.ink,
                    )
                }
                if (item.note.isNotBlank()) {
                    Text(item.note, style = MaterialTheme.typography.bodySmall, color = PmTheme.colors.inkMuted)
                }
            }
            Icon(PmIcons.Chevron, contentDescription = null, tint = PmTheme.colors.outlineStrong, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { photos() }
    }
}

@Composable
internal fun photoLabels() = PhotoStripLabels(
    add = stringResource(R.string.feature_clients_photo_add),
    camera = stringResource(R.string.feature_clients_photo_camera),
    gallery = stringResource(R.string.feature_clients_photo_gallery),
    delete = stringResource(R.string.feature_clients_photo_delete),
    close = stringResource(R.string.feature_clients_photo_close),
)
