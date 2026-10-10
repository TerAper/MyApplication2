package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.teraper.printmaster.core.database.entity.AppProfileEntity
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.database.entity.MasterEntity
import kotlinx.coroutines.flow.Flow

/** Registration, the user's companies and masters. */
@Dao
interface CompanyDao {

    @Query("SELECT * FROM app_profile WHERE id = 1")
    fun observeProfile(): Flow<AppProfileEntity?>

    @Query("SELECT * FROM app_profile WHERE id = 1")
    suspend fun getProfile(): AppProfileEntity?

    @Upsert
    suspend fun upsertProfile(profile: AppProfileEntity)

    @Query("SELECT * FROM companies ORDER BY id")
    fun observeCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getCompany(id: Long): CompanyEntity?

    @Query("SELECT * FROM companies WHERE tax_id = :taxId AND id != :excludeId LIMIT 1")
    suspend fun findOtherCompanyWithTaxId(taxId: String, excludeId: Long): CompanyEntity?

    @Query("SELECT COUNT(*) FROM companies")
    suspend fun countCompanies(): Int

    @Insert
    suspend fun insertCompany(company: CompanyEntity): Long

    @Update
    suspend fun updateCompany(company: CompanyEntity)

    @Query("DELETE FROM companies WHERE id = :id")
    suspend fun deleteCompany(id: Long)

    /** Money and orders that keep a company from being deleted. */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM charges WHERE company_id = :id)
             + (SELECT COUNT(*) FROM payments WHERE company_id = :id)
             + (SELECT COUNT(*) FROM import_batches WHERE company_id = :id)
             + (SELECT COUNT(*) FROM orders WHERE company_id = :id)
             + (SELECT COUNT(*) FROM expenses WHERE company_id = :id)
        """,
    )
    suspend fun countCompanyRecords(id: Long): Int

    @Query("SELECT * FROM masters ORDER BY id")
    fun observeMasters(): Flow<List<MasterEntity>>

    @Insert
    suspend fun insertMaster(master: MasterEntity): Long

    @Update
    suspend fun updateMaster(master: MasterEntity)

    @Query("DELETE FROM masters WHERE id = :id")
    suspend fun deleteMaster(id: Long): Int

    @Query("SELECT COUNT(*) FROM masters")
    suspend fun countMasters(): Int
}
