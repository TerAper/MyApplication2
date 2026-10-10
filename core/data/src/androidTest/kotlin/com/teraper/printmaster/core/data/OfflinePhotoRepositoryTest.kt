package com.teraper.printmaster.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.teraper.printmaster.core.data.repository.SaveClientResult
import com.teraper.printmaster.core.data.repository.SaveOrderResult
import com.teraper.printmaster.core.model.ClientDraft
import com.teraper.printmaster.core.model.OrderDraft
import com.teraper.printmaster.core.model.PhotoOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OfflinePhotoRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repos = TestRepos()
    private val photos = repos.photos

    @After
    fun tearDown() {
        repos.db.close()
        File(context.filesDir, "photos").deleteRecursively()
    }

    /** A big camera-like picture: 4000×3000 JPEG. */
    private fun bigPicture(): Uri {
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 120, 200)) }
        val file = File(context.cacheDir, "big.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return Uri.fromFile(file)
    }

    private suspend fun order(): Long {
        repos.register()
        val client = (repos.clients.saveClient(ClientDraft(name = "Firm")) as SaveClientResult.Saved).clientId
        return (repos.orders.saveOrder(OrderDraft(clientId = client, date = LocalDate.of(2026, 10, 7), description = "Refill")) as SaveOrderResult.Saved).orderId
    }

    @Test
    fun aPhotoIsShrunkAndHasASmallCopy() = runTest {
        val orderId = order()
        assertTrue(photos.add(PhotoOwner.ORDER, orderId, bigPicture().toString()))
        val photo = photos.observePhotos(PhotoOwner.ORDER, listOf(orderId)).first().single()

        val full = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(photo.fullPath, it) }
        assertEquals(1600, maxOf(full.outWidth, full.outHeight))
        val thumb = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(photo.thumbPath, it) }
        assertEquals(320, maxOf(thumb.outWidth, thumb.outHeight))

        assertFalse(photos.add(PhotoOwner.ORDER, orderId, Uri.fromFile(File(context.cacheDir, "missing.jpg")).toString()))
    }

    @Test
    fun photosOfADeletedOrderAreCleanedUp() = runTest {
        val orderId = order()
        photos.add(PhotoOwner.ORDER, orderId, bigPicture().toString())
        val photo = photos.observePhotos(PhotoOwner.ORDER, listOf(orderId)).first().single()
        repos.orders.deleteOrder(orderId)

        photos.cleanUp()
        assertTrue(photos.observePhotos(PhotoOwner.ORDER, listOf(orderId)).first().isEmpty())
        assertFalse(File(photo.fullPath).exists())
        assertFalse(File(photo.thumbPath).exists())
    }
}
