package com.teraper.printmaster.core.testing

import com.teraper.printmaster.core.data.repository.CompaniesRepository
import com.teraper.printmaster.core.data.repository.DeleteCompanyResult
import com.teraper.printmaster.core.data.repository.SaveCompanyResult
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.AppProfile
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.Master
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** In-memory companies; registered as a master with [companies], the first one default. */
class FakeCompaniesRepository(
    companies: List<Company> = listOf(Company(1, "Main")),
    mode: AccountMode = AccountMode.MASTER,
    registered: Boolean = true,
) : CompaniesRepository {
    val profile = MutableStateFlow(if (registered) AppProfile(mode, "Master", companies.first().id) else null)
    val companies = MutableStateFlow(companies)
    val masters = MutableStateFlow<List<Master>>(emptyList())
    val selected = MutableStateFlow<Long?>(null)
    val registrations = mutableListOf<Triple<AccountMode, String, CompanyDraft>>()
    val savedCompanies = mutableListOf<CompanyDraft>()

    override fun observeProfile(): Flow<AppProfile?> = profile
    override fun observeCompanies(): Flow<List<Company>> = companies
    override fun observeMasters(): Flow<List<Master>> = masters

    override fun observeActiveCompany(): Flow<Company?> = combine(profile, companies, selected) { profile, list, selected ->
        if (profile == null) null else list.firstOrNull { it.id == selected } ?: list.firstOrNull { it.id == profile.defaultCompanyId }
    }

    override fun selectCompany(id: Long) {
        selected.value = id
    }

    override suspend fun register(mode: AccountMode, ownerName: String, company: CompanyDraft): SaveCompanyResult {
        val errors = company.validate()
        if (errors.isNotEmpty()) return SaveCompanyResult.Invalid(errors)
        registrations += Triple(mode, ownerName, company)
        companies.value = listOf(Company(1, company.name, colorIndex = company.colorIndex))
        profile.value = AppProfile(mode, ownerName, 1)
        return SaveCompanyResult.Saved(1)
    }

    override suspend fun saveCompany(draft: CompanyDraft): SaveCompanyResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveCompanyResult.Invalid(errors)
        companies.value.firstOrNull { it.taxId != null && it.taxId == draft.normalizedTaxId() && it.id != draft.id }
            ?.let { return SaveCompanyResult.TaxIdTaken(it.name) }
        savedCompanies += draft
        val id = if (draft.isNew) (companies.value.maxOfOrNull { it.id } ?: 0) + 1 else draft.id
        val company = Company(id, draft.name.trim(), draft.normalizedTaxId(), draft.accountList(), draft.colorIndex)
        companies.value = companies.value.filterNot { it.id == id } + company
        return SaveCompanyResult.Saved(id)
    }

    override suspend fun setDefaultCompany(id: Long) {
        profile.value = profile.value?.copy(defaultCompanyId = id)
    }

    override suspend fun deleteCompany(id: Long): DeleteCompanyResult = when {
        companies.value.none { it.id == id } -> DeleteCompanyResult.NOT_FOUND
        profile.value?.defaultCompanyId == id -> DeleteCompanyResult.IS_DEFAULT
        else -> {
            companies.value = companies.value.filterNot { it.id == id }
            DeleteCompanyResult.DELETED
        }
    }

    override suspend fun saveMaster(id: Long, name: String, phone: String): Boolean {
        if (name.isBlank()) return false
        val newId = if (id == 0L) (masters.value.maxOfOrNull { it.id } ?: 0) + 1 else id
        masters.value = masters.value.filterNot { it.id == newId } + Master(newId, name.trim(), phone.trim())
        return true
    }

    override suspend fun deleteMaster(id: Long): Boolean {
        val before = masters.value.size
        masters.value = masters.value.filterNot { it.id == id }
        return masters.value.size < before
    }
}
