package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.CompanyDao
import com.teraper.printmaster.core.database.entity.AppProfileEntity
import com.teraper.printmaster.core.database.entity.CompanyEntity
import com.teraper.printmaster.core.database.entity.MasterEntity
import com.teraper.printmaster.core.model.AccountMode
import com.teraper.printmaster.core.model.AppProfile
import com.teraper.printmaster.core.model.Company
import com.teraper.printmaster.core.model.CompanyDraft
import com.teraper.printmaster.core.model.Master
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Singleton so every screen sees the same active company. */
@Singleton
internal class OfflineCompaniesRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val dao: CompanyDao,
    private val clock: Clock,
) : CompaniesRepository {

    /** Picked by the user this session; null = use the default company. Not saved on purpose. */
    private val selectedId = MutableStateFlow<Long?>(null)

    override fun observeProfile(): Flow<AppProfile?> = dao.observeProfile().map { row ->
        row?.let { AppProfile(it.mode, it.ownerName, it.defaultCompanyId) }
    }

    override fun observeCompanies(): Flow<List<Company>> = dao.observeCompanies().map { rows -> rows.map { it.toModel() } }

    override fun observeMasters(): Flow<List<Master>> =
        dao.observeMasters().map { rows -> rows.map { Master(it.id, it.name, it.phone) } }

    override fun observeActiveCompany(): Flow<Company?> =
        combine(dao.observeProfile(), observeCompanies(), selectedId) { profile, companies, selected ->
            if (profile == null) return@combine null
            companies.firstOrNull { it.id == selected } ?: companies.firstOrNull { it.id == profile.defaultCompanyId }
        }.distinctUntilChanged()

    override fun selectCompany(id: Long) {
        selectedId.value = id
    }

    override suspend fun register(mode: AccountMode, ownerName: String, company: CompanyDraft): SaveCompanyResult {
        val errors = company.validate()
        if (errors.isNotEmpty()) return SaveCompanyResult.Invalid(errors)
        return db.withTransaction {
            val companyId = dao.insertCompany(company.toEntity(createdAt = clock.millis()))
            val name = ownerName.trim()
            if (mode == AccountMode.MASTER && name.isNotEmpty()) {
                dao.insertMaster(MasterEntity(name = name, createdAt = clock.millis()))
            }
            dao.upsertProfile(AppProfileEntity(mode = mode, ownerName = name, defaultCompanyId = companyId))
            SaveCompanyResult.Saved(companyId)
        }
    }

    override suspend fun saveCompany(draft: CompanyDraft): SaveCompanyResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SaveCompanyResult.Invalid(errors)
        return db.withTransaction {
            draft.normalizedTaxId()?.let { taxId ->
                dao.findOtherCompanyWithTaxId(taxId, excludeId = draft.id)?.let {
                    return@withTransaction SaveCompanyResult.TaxIdTaken(it.name)
                }
            }
            if (draft.isNew) {
                SaveCompanyResult.Saved(dao.insertCompany(draft.toEntity(createdAt = clock.millis())))
            } else {
                val existing = dao.getCompany(draft.id) ?: return@withTransaction SaveCompanyResult.Invalid(emptySet())
                dao.updateCompany(draft.toEntity(createdAt = existing.createdAt).copy(id = existing.id))
                SaveCompanyResult.Saved(existing.id)
            }
        }
    }

    override suspend fun setDefaultCompany(id: Long) {
        db.withTransaction {
            val profile = dao.getProfile() ?: return@withTransaction
            if (dao.getCompany(id) != null) dao.upsertProfile(profile.copy(defaultCompanyId = id))
        }
    }

    override suspend fun deleteCompany(id: Long): DeleteCompanyResult = db.withTransaction {
        when {
            dao.getCompany(id) == null -> DeleteCompanyResult.NOT_FOUND
            dao.getProfile()?.defaultCompanyId == id -> DeleteCompanyResult.IS_DEFAULT
            dao.countCompanyRecords(id) > 0 -> DeleteCompanyResult.HAS_RECORDS
            else -> {
                dao.deleteCompany(id)
                if (selectedId.value == id) selectedId.value = null
                DeleteCompanyResult.DELETED
            }
        }
    }

    override suspend fun saveMaster(id: Long, name: String, phone: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return false
        if (id == 0L) {
            dao.insertMaster(MasterEntity(name = cleanName, phone = phone.trim(), createdAt = clock.millis()))
        } else {
            dao.updateMaster(MasterEntity(id = id, name = cleanName, phone = phone.trim(), createdAt = clock.millis()))
        }
        return true
    }

    override suspend fun deleteMaster(id: Long): Boolean = dao.deleteMaster(id) > 0
}

private fun CompanyEntity.toModel() = Company(
    id = id,
    name = name,
    taxId = taxId,
    bankAccounts = bankAccounts.lines().filter { it.isNotBlank() },
    colorIndex = colorIndex,
)

private fun CompanyDraft.toEntity(createdAt: Long) = CompanyEntity(
    name = name.trim().replace(Regex("""\s+"""), " "),
    taxId = normalizedTaxId(),
    bankAccounts = accountList().joinToString("\n"),
    colorIndex = colorIndex,
    createdAt = createdAt,
)
