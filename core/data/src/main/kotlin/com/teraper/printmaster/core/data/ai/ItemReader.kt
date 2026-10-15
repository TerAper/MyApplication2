package com.teraper.printmaster.core.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.teraper.printmaster.core.data.analytics.AppAnalytics
import com.teraper.printmaster.core.model.ItemReading
import com.teraper.printmaster.core.model.ItemReadings
import com.teraper.printmaster.core.model.ItemType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a photo of a product's box or label and says what the product is, in the user's own types.
 * Only the photo, the barcode and the type names are sent: never clients, money or anything else.
 * One interface, so the AI behind it can be changed (Gemini free tier now; paid or another later).
 */
interface ItemReader {
    /** [photo] is a content or file uri. */
    suspend fun read(photo: String, barcode: String, types: List<ItemType>, language: String): ReadResult
}

sealed interface ReadResult {
    data class Read(val reading: ItemReading) : ReadResult

    /** The AI isn't set up (Firebase AI Logic off) or the phone is offline. */
    data object Unavailable : ReadResult

    /** It answered nothing usable (blurry photo, not a product). */
    data object NothingFound : ReadResult
}

@Singleton
internal class GeminiItemReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val analytics: AppAnalytics,
) : ItemReader {

    override suspend fun read(photo: String, barcode: String, types: List<ItemType>, language: String): ReadResult {
        if (FirebaseApp.getApps(context).isEmpty()) return ReadResult.Unavailable
        val bitmap = withContext(Dispatchers.IO) { context.loadScaled(Uri.parse(photo)) } ?: return ReadResult.NothingFound
        val prompt = ItemReadings.prompt(types, barcode, language)
        for (model in MODELS) {
            val answer = runCatching {
                withTimeout(TIMEOUT_MS) {
                    Firebase.ai(backend = GenerativeBackend.googleAI())
                        .generativeModel(model, generationConfig = generationConfig { responseMimeType = "application/json" })
                        .generateContent(content { image(bitmap); text(prompt) })
                        .text
                }
            }
            if (answer.isFailure) {
                Log.w(TAG, "$model failed", answer.exceptionOrNull())
                continue
            }
            analytics.log("ai_item_read", mapOf("model" to model))
            val reading = answer.getOrNull()?.let(::parse) ?: return ReadResult.NothingFound
            return if (reading.name.isBlank() && reading.type.isBlank() && reading.fields.isEmpty()) ReadResult.NothingFound else ReadResult.Read(reading)
        }
        return ReadResult.Unavailable
    }

    private fun parse(text: String): ItemReading? = runCatching {
        // Some answers wrap the JSON in a code block.
        val json = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        val fields = json.optJSONObject("fields")
        val fits = json.optJSONArray("fitsInto")
        ItemReading(
            type = json.optString("type"),
            name = json.optString("name"),
            fields = fields?.keys()?.asSequence()?.associateWith { fields.opt(it)?.toString().orEmpty() }?.filterValues { it.isNotBlank() && it != "null" }.orEmpty(),
            fitsInto = (0 until (fits?.length() ?: 0)).mapNotNull { fits?.optString(it)?.takeIf(String::isNotBlank) },
        )
    }.getOrNull()

    /** Big enough to read small print, small enough to send quickly. */
    private fun Context.loadScaled(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()

    private companion object {
        /** The newest fast model first; a fixed one if that name isn't served. */
        val MODELS = listOf("gemini-flash-latest", "gemini-2.5-flash")
        const val TIMEOUT_MS = 45_000L
        const val MAX_SIDE = 1600
        const val TAG = "ItemReader"
    }
}
