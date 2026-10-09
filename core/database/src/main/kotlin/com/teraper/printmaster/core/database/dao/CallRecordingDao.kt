package com.teraper.printmaster.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.teraper.printmaster.core.database.entity.CallRecordingClientEntity
import com.teraper.printmaster.core.database.entity.CallRecordingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordingDao {

    @Query("SELECT * FROM call_recordings ORDER BY started_at DESC")
    fun observeRecordings(): Flow<List<CallRecordingEntity>>

    @Query(
        """
        SELECT r.* FROM call_recordings r
        JOIN call_recording_clients l ON l.recording_id = r.id
        WHERE l.client_id = :clientId
        ORDER BY r.started_at DESC
        """,
    )
    fun observeClientRecordings(clientId: Long): Flow<List<CallRecordingEntity>>

    @Query(
        """
        SELECT l.recording_id, l.client_id, l.manual, c.name FROM call_recording_clients l
        JOIN clients c ON c.id = l.client_id
        """,
    )
    fun observeLinks(): Flow<List<RecordingLinkRow>>

    @Query("SELECT uri FROM call_recordings")
    suspend fun getUris(): List<String>

    /** Recordings still waiting for a client. */
    @Query(
        """
        SELECT * FROM call_recordings r WHERE ignored = 0
          AND NOT EXISTS (SELECT 1 FROM call_recording_clients l WHERE l.recording_id = r.id)
        """,
    )
    suspend fun getUnmatched(): List<CallRecordingEntity>

    @Query("SELECT client_id, number FROM client_phones")
    suspend fun getClientPhones(): List<ClientPhoneRow>

    @Query("SELECT id AS client_id, name AS number FROM clients")
    suspend fun getClientNames(): List<ClientPhoneRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecording(recording: CallRecordingEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<CallRecordingClientEntity>)

    @Query("DELETE FROM call_recording_clients WHERE recording_id = :recordingId AND client_id = :clientId")
    suspend fun deleteLink(recordingId: Long, clientId: Long)

    @Query("UPDATE call_recordings SET ignored = :ignored WHERE id = :id")
    suspend fun setIgnored(id: Long, ignored: Boolean)

    /** Files removed from the phone. */
    @Query("DELETE FROM call_recordings WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)
}

data class RecordingLinkRow(
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "client_id") val clientId: Long,
    val manual: Boolean,
    val name: String,
)

/** A client's phone number (or, from [CallRecordingDao.getClientNames], its name). */
data class ClientPhoneRow(
    @ColumnInfo(name = "client_id") val clientId: Long,
    val number: String,
)
