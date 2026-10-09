package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A call recording found in the folder the user picked. Only a link: the audio stays
 * in the phone's folder, so a restored backup on another phone may point at missing files.
 */
@Entity(
    tableName = "call_recordings",
    indices = [Index(value = ["uri"], unique = true), Index(value = ["started_at"])],
)
data class CallRecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    /** Contact name or number as written in the file name. */
    val caller: String,
    /** Epoch millis of the call start (from the file name, in the phone's time zone). */
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    /** The user said it belongs to no client; not shown in "Not matched" any more. */
    val ignored: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Which clients a recording belongs to; usually one, two when a phone number is shared. */
@Entity(
    tableName = "call_recording_clients",
    primaryKeys = ["recording_id", "client_id"],
    foreignKeys = [
        ForeignKey(
            entity = CallRecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["client_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["client_id"])],
)
data class CallRecordingClientEntity(
    @ColumnInfo(name = "recording_id") val recordingId: Long,
    @ColumnInfo(name = "client_id") val clientId: Long,
    /** Attached by hand rather than by phone number. */
    val manual: Boolean,
)
