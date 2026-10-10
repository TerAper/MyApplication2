package com.teraper.printmaster.core.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.teraper.printmaster.core.database.dao.PhotoDao
import com.teraper.printmaster.core.database.entity.PhotoEntity
import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
internal class OfflinePhotoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PhotoDao,
    private val clock: Clock,
) : PhotoRepository {

    override fun observePhotos(owner: PhotoOwner, ownerIds: List<Long>): Flow<List<Photo>> {
        if (ownerIds.isEmpty()) return flowOf(emptyList())
        return dao.observePhotos(owner, ownerIds).map { rows -> rows.map { it.toModel() } }
    }

    override suspend fun add(owner: PhotoOwner, ownerId: Long, uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val name = UUID.randomUUID().toString()
            val upright = decodeUpright(Uri.parse(uri), FULL_SIZE) ?: return@withContext false
            write(upright, full(name), FULL_QUALITY)
            write(scaleDown(upright, THUMB_SIZE), thumb(name), THUMB_QUALITY)
            upright.recycle()
            dao.insert(PhotoEntity(ownerType = owner, ownerId = ownerId, fileName = name, createdAt = clock.millis()))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(photoId: Long) = withContext(Dispatchers.IO) {
        val photo = dao.get(photoId) ?: return@withContext
        dao.delete(photoId)
        full(photo.fileName).delete()
        thumb(photo.fileName).delete()
    }

    override suspend fun cleanUp() = withContext(Dispatchers.IO) {
        dao.getOrphans().forEach { delete(it.id) }
        val used = dao.getFileNames().toSet()
        folder().listFiles().orEmpty().forEach { file ->
            if (file.name.removeSuffix(".webp").removeSuffix("_t") !in used) file.delete()
        }
    }

    /** Reads only as many pixels as needed (sample size) and applies the camera's rotation. */
    private fun decodeUpright(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Measuring only: decodeStream returns null here by design, the size lands in [bounds].
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val degrees = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scaled = scaleDown(bitmap, maxSide)
        if (scaled !== bitmap) bitmap.recycle()
        if (degrees == 0) return scaled
        val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        if (rotated !== scaled) scaled.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt(), (bitmap.height * ratio).roundToInt(), true)
    }

    private fun write(bitmap: Bitmap, file: File, quality: Int) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, it) }
    }

    private fun folder() = File(context.filesDir, PhotoFiles.FOLDER).apply { mkdirs() }

    private fun full(name: String) = File(folder(), "$name.webp")

    private fun thumb(name: String) = File(folder(), "${name}_t.webp")

    private fun PhotoEntity.toModel() = Photo(id, ownerType, ownerId, thumb(fileName).path, full(fileName).path)

    private companion object {
        const val FULL_SIZE = 1600
        const val THUMB_SIZE = 320
        const val FULL_QUALITY = 80
        const val THUMB_QUALITY = 70
    }
}

/** Where photo files live, for the backup too. */
object PhotoFiles {
    const val FOLDER = "photos"
}
