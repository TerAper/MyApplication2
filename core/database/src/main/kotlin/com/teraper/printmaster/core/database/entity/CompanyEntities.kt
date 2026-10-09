package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.AccountMode

/** A company money goes through. Bank accounts are stored one per line. */
@Entity(tableName = "companies", indices = [Index(value = ["tax_id"], unique = true)])
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "tax_id") val taxId: String?,
    @ColumnInfo(name = "bank_accounts") val bankAccounts: String = "",
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A person who does the work. */
@Entity(tableName = "masters")
data class MasterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Google account (email) the master joined the company with; null = not joined. */
    @ColumnInfo(name = "member_email") val memberEmail: String? = null,
    /** The master's account id in the shared space; orders sent to this master carry it. */
    @ColumnInfo(name = "member_uid") val memberUid: String? = null,
)

/** Registration result; a single row with [id] = [SINGLE_ID]. No row = not registered yet. */
@Entity(
    tableName = "app_profile",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["default_company_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["default_company_id"])],
)
data class AppProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val mode: AccountMode,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "default_company_id") val defaultCompanyId: Long,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}
