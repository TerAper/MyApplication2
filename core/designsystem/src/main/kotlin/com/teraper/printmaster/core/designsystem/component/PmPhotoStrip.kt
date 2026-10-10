package com.teraper.printmaster.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.teraper.printmaster.core.designsystem.icon.PmIcons
import com.teraper.printmaster.core.designsystem.theme.PmTheme
import com.teraper.printmaster.core.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Texts of the strip, given by the screen in its own language. */
data class PhotoStripLabels(
    val add: String,
    val camera: String,
    val gallery: String,
    val delete: String,
    val close: String,
)

/**
 * A row of small photos with a "+" tile (camera or gallery). Tapping a photo opens it full
 * screen with pinch-to-zoom. [onAdd] gets the picture's URI; the app shrinks and keeps it.
 */
@Composable
fun PmPhotoStrip(
    photos: List<Photo>,
    labels: PhotoStripLabels,
    onAdd: (uri: String) -> Unit,
    onDelete: (Photo) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Photo?>(null) }
    // Survives the camera app taking over the screen.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCapture
        pendingCapture = null
        if (saved && uri != null) onAdd(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAdd(uri.toString())
    }

    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.id }) { photo ->
            Thumbnail(photo.thumbPath, size, Modifier.clickable { open = photo })
        }
        item {
            Box {
                Box(
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, PmTheme.colors.outlineStrong, RoundedCornerShape(10.dp))
                        .background(PmTheme.colors.surface)
                        .clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PmIcons.AddPhoto, contentDescription = labels.add, tint = PmTheme.colors.primary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(labels.camera) },
                        leadingIcon = { Icon(PmIcons.Camera, contentDescription = null) },
                        onClick = {
                            menu = false
                            val file = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "photo-${System.currentTimeMillis()}.jpg") }
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
                            pendingCapture = uri.toString()
                            camera.launch(uri)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(labels.gallery) },
                        leadingIcon = { Icon(PmIcons.Gallery, contentDescription = null) },
                        onClick = {
                            menu = false
                            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
        }
    }

    open?.let { photo ->
        PhotoViewer(
            photo = photo,
            labels = labels,
            onDelete = {
                open = null
                onDelete(photo)
            },
            onDismiss = { open = null },
        )
    }
}

@Composable
private fun Thumbnail(path: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = rememberBitmap(path)
    Box(modifier.size(size).clip(RoundedCornerShape(10.dp)).background(PmTheme.colors.surfaceMuted)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** Full screen, pinch to zoom and drag; the full-size copy is decoded only now. */
@Composable
private fun PhotoViewer(photo: Photo, labels: PhotoStripLabels, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val zoom = rememberTransformableState { zoomChange, pan, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    val bitmap = rememberBitmap(photo.fullPath)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            bitmap?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(zoom)
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
                    contentScale = ContentScale.Fit,
                )
            }
            // Dark backing so the buttons show on light photos too.
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))) {
                IconButton(onClick = onDelete) { Icon(PmIcons.Delete, contentDescription = labels.delete, tint = Color.White) }
                IconButton(onClick = onDismiss) { Icon(PmIcons.Close, contentDescription = labels.close, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun rememberBitmap(path: String): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
    }
    return bitmap
}
