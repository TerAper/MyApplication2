package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.teraper.printmaster.core.database.entity.PhotoEntity
import com.teraper.printmaster.core.model.PhotoOwner
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE owner_type = :owner AND owner_id IN (:ownerIds) ORDER BY id")
    fun observePhotos(owner: PhotoOwner, ownerIds: List<Long>): Flow<List<PhotoEntity>>

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: Long): PhotoEntity?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT file_name FROM photos")
    suspend fun getFileNames(): List<String>

    /** Rows whose printer, cartridge or order no longer exists; their files go too. */
    @Query(
        """
        SELECT * FROM photos WHERE
          (owner_type = 'PRINTER' AND owner_id NOT IN (SELECT id FROM client_printers)) OR
          (owner_type = 'CARTRIDGE' AND owner_id NOT IN (SELECT id FROM client_printer_cartridges)) OR
          (owner_type = 'ORDER' AND owner_id NOT IN (SELECT id FROM orders))
        """,
    )
    suspend fun getOrphans(): List<PhotoEntity>
}
