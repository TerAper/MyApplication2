package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.PhotoRepository
import com.teraper.printmaster.core.model.Photo
import com.teraper.printmaster.core.model.PhotoOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePhotoRepository : PhotoRepository {
    val photos = MutableStateFlow<List<Photo>>(emptyList())

    override fun observePhotos(owner: PhotoOwner, ownerIds: List<Long>): Flow<List<Photo>> =
        photos.map { list -> list.filter { it.owner == owner && it.ownerId in ownerIds } }

    override suspend fun add(owner: PhotoOwner, ownerId: Long, uri: String): Boolean {
        val id = (photos.value.maxOfOrNull { it.id } ?: 0) + 1
        photos.value = photos.value + Photo(id, owner, ownerId, "$uri-thumb", uri)
        return true
    }

    override suspend fun delete(photoId: Long) {
        photos.value = photos.value.filterNot { it.id == photoId }
    }

    override suspend fun cleanUp() = Unit
}
