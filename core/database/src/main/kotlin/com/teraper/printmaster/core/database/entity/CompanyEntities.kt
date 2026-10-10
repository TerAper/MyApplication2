package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.CompanyKind

/** A company money goes through. Bank accounts are stored one per line. */
@Entity(tableName = "companies", indices = [Index(value = ["tax_id"], unique = true)])
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "tax_id") val taxId: String?,
    @ColumnInfo(name = "bank_accounts") val bankAccounts: String = "",
    @ColumnInfo(name = "color_index") val colorIndex: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** OWN = the user's company; ATTACHED = another owner's company that gives the user orders. */
    @ColumnInfo(defaultValue = "OWN") val kind: CompanyKind = CompanyKind.OWN,
    /** Its shared space in Firebase: OWN once a master was invited, ATTACHED always (null = left it). */
    @ColumnInfo(name = "space_id") val spaceId: String? = null,
    /** OWN: the code masters enter to attach. */
    @ColumnInfo(name = "join_code") val joinCode: String? = null,
    /** ATTACHED: the name of the owner who gave the code. */
    @ColumnInfo(name = "owner_name", defaultValue = "") val ownerName: String = "",
)

/** An attached master (one who joined with a code) may get orders of this own company. */
@Entity(
    tableName = "company_members",
    primaryKeys = ["company_id", "master_id"],
    foreignKeys = [
        ForeignKey(entity = CompanyEntity::class, parentColumns = ["id"], childColumns = ["company_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MasterEntity::class, parentColumns = ["id"], childColumns = ["master_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["master_id"])],
)
data class CompanyMemberEntity(
    @ColumnInfo(name = "company_id") val companyId: Long,
    @ColumnInfo(name = "master_id") val masterId: Long,
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
