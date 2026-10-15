package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.ai.ItemReader
import com.teraper.printmaster.core.data.ai.ReadResult
import com.teraper.printmaster.core.model.ItemType

/** Answers [result] to every photo; [asked] keeps the barcodes it was given. */
class FakeItemReader(var result: ReadResult = ReadResult.Unavailable) : ItemReader {
    val asked = mutableListOf<String>()

    override suspend fun read(photo: String, barcode: String, types: List<ItemType>, language: String): ReadResult {
        asked += barcode
        return result
    }
}
