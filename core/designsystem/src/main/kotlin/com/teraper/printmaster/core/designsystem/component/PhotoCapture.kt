package com.teraper.printmaster.core.designsystem.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** Takes a photo with the camera app, or picks one from the gallery; [onPhoto] gets its uri. */
class PhotoCapture internal constructor(val camera: () -> Unit, val gallery: () -> Unit)

@Composable
fun rememberPhotoCapture(onPhoto: (uri: String) -> Unit): PhotoCapture {
    val context = LocalContext.current
    // Survives the camera app taking over the screen.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending
        pending = null
        if (saved && uri != null) onPhoto(uri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhoto(uri.toString())
    }
    return PhotoCapture(
        camera = {
            val file = File(context.cacheDir, "camera").apply { mkdirs() }.let { File(it, "photo-${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.camera", file)
            pending = uri.toString()
            camera.launch(uri)
        },
        gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
}
