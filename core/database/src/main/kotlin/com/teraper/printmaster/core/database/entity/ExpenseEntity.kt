package com.teraper.printmaster.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.teraper.printmaster.core.model.ExpenseCategory

/** Money the company spent, typed by hand. A company with expenses can't be deleted. */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(entity = CompanyEntity::class, parentColumns = ["id"], childColumns = ["company_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["company_id", "date_epoch_day"])],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "company_id") val companyId: Long,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val category: ExpenseCategory,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
