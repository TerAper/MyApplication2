package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.PhotoOwner

/**
 * A photo of a client's printer, one of its cartridges, or an order. The picture itself is a
 * file in the app's photos folder ([fileName] + "_t" for the small copy shown in lists).
 * Photos whose owner was deleted are removed at startup (see PhotoRepository.cleanUp).
 */
@Entity(tableName = "photos", indices = [Index(value = ["owner_type", "owner_id"])])
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "owner_type") val ownerType: PhotoOwner,
    @ColumnInfo(name = "owner_id") val ownerId: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
