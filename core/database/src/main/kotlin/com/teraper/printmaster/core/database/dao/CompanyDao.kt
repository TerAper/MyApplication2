package com.teraper.printmaster.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.teraper.printmaster.core.database.entity.AppProfileEntity
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.database.entity.CompanyMemberEntity
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

    /** The user's own companies (money, switching, imports). */
    @Query("SELECT * FROM companies WHERE kind = 'OWN' ORDER BY id")
    fun observeCompanies(): Flow<List<CompanyEntity>>

    /** Other owners' companies that give the user orders. */
    @Query("SELECT * FROM companies WHERE kind = 'ATTACHED' ORDER BY id")
    fun observeAttachedCompanies(): Flow<List<CompanyEntity>>

    /** Companies with a shared space to sync. */
    @Query("SELECT * FROM companies WHERE space_id IS NOT NULL ORDER BY id")
    suspend fun getSharedCompanies(): List<CompanyEntity>

    @Query("SELECT * FROM companies WHERE space_id IS NOT NULL ORDER BY id")
    fun observeSharedCompanies(): Flow<List<CompanyEntity>>

    @Query("SELECT * FROM companies WHERE space_id = :spaceId LIMIT 1")
    suspend fun getCompanyBySpace(spaceId: String): CompanyEntity?

    @Query("UPDATE companies SET space_id = :spaceId, join_code = :joinCode WHERE id = :id")
    suspend fun setSpace(id: Long, spaceId: String?, joinCode: String?)

    // Attached masters of own companies

    @Query("SELECT * FROM company_members")
    fun observeMemberships(): Flow<List<CompanyMemberEntity>>

    @Query("SELECT * FROM company_members WHERE company_id = :companyId")
    suspend fun getMemberships(companyId: Long): List<CompanyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembership(member: CompanyMemberEntity)

    @Query("DELETE FROM company_members WHERE company_id = :companyId AND master_id = :masterId")
    suspend fun deleteMembership(companyId: Long, masterId: Long)

    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getCompany(id: Long): CompanyEntity?

    @Query("SELECT * FROM companies WHERE tax_id = :taxId AND id != :excludeId LIMIT 1")
    suspend fun findOtherCompanyWithTaxId(taxId: String, excludeId: Long): CompanyEntity?

    @Query("SELECT COUNT(*) FROM companies WHERE kind = 'OWN'")
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
