package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import kotlinx.coroutines.flow.Flow

/** Photos of printers, cartridges and orders, kept small on the phone. */
interface PhotoRepository {

    fun observePhotos(owner: PhotoOwner, ownerIds: List<Long>): Flow<List<Photo>>

    /**
     * Copies the picture at [uri] (camera or gallery), turned upright and shrunk:
     * longest side at most 1600 px as WebP, plus a 320 px copy for lists. False if it isn't a picture.
     */
    suspend fun add(owner: PhotoOwner, ownerId: Long, uri: String): Boolean

    suspend fun delete(photoId: Long)

    /** Removes photos whose printer, cartridge or order is gone, and files no row points to. */
    suspend fun cleanUp()
}
