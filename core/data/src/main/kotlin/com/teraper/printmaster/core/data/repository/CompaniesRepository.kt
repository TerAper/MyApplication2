package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.AppProfile
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.CompanyDraftError
import com.teraper.printmaster.core.model.Master
import kotlinx.coroutines.flow.Flow

/** Registration, the user's companies and masters, and which company is being viewed. */
interface CompaniesRepository {
    /** Null until the first-launch registration is done. */
    fun observeProfile(): Flow<AppProfile?>

    /** The user's own companies. */
    fun observeCompanies(): Flow<List<Company>>

    /** Other owners' companies the user joined with a code: they give the user orders. */
    fun observeAttachedCompanies(): Flow<List<Company>>

    fun observeMasters(): Flow<List<Master>>

    /**
     * The company whose money is shown everywhere. Starts as the default company on every
     * app start; [selectCompany] switches it. Null before registration.
     */
    fun observeActiveCompany(): Flow<Company?>

    fun selectCompany(id: Long)

    /** First launch. In MASTER mode [ownerName] also becomes the first master. */
    suspend fun register(mode: AccountMode, ownerName: String, company: CompanyDraft): SaveCompanyResult

    suspend fun saveCompany(draft: CompanyDraft): SaveCompanyResult

    suspend fun setDefaultCompany(id: Long)

    suspend fun deleteCompany(id: Long): DeleteCompanyResult

    /** [id] 0 = new. Blank names are ignored (returns false). */
    suspend fun saveMaster(id: Long, name: String, phone: String): Boolean

    suspend fun deleteMaster(id: Long): Boolean
}

sealed interface SaveCompanyResult {
    data class Saved(val companyId: Long) : SaveCompanyResult
    data class Invalid(val errors: Set<CompanyDraftError>) : SaveCompanyResult
    data class TaxIdTaken(val otherCompanyName: String) : SaveCompanyResult
}

enum class DeleteCompanyResult {
    DELETED,
    NOT_FOUND,

    /** The default company can't be deleted; make another one default first. */
    IS_DEFAULT,

    /** It has invoices, payments, imports or orders. */
    HAS_RECORDS,
}
