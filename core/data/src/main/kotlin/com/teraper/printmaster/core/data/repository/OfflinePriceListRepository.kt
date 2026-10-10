package com.teraper.printmaster.core.data.repository

import androidx.room.withTransaction
import com.teraper.printmaster.core.database.PrintMasterDatabase
import com.teraper.printmaster.core.database.dao.PriceListDao
import com.teraper.printmaster.core.database.entity.RepairPartEntity
import com.teraper.printmaster.core.model.Money
import com.teraper.printmaster.core.model.PriceItem
import com.teraper.printmaster.core.model.PriceItemDraft
import com.teraper.printmaster.core.model.PriceListSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class OfflinePriceListRepository @Inject constructor(
    private val db: PrintMasterDatabase,
    private val dao: PriceListDao,
) : PriceListRepository {

    override fun observeItems(): Flow<List<PriceItem>> = dao.observeParts().map { rows ->
        rows.map { it.toModel() }.sortedWith(compareBy({ it.category.ordinal }, { PriceListSearch.key(it.name) }))
    }

    override fun observeItemsFor(companyId: Long): Flow<List<PriceItem>> = dao.observePartsFor(companyId).map { rows ->
        rows.map { it.toModel() }.sortedWith(compareBy({ it.category.ordinal }, { PriceListSearch.key(it.name) }))
    }

    override fun observeItem(id: Long): Flow<PriceItem?> = dao.observePart(id).map { it?.toModel() }

    override suspend fun saveItem(draft: PriceItemDraft): SavePriceItemResult {
        val errors = draft.validate()
        if (errors.isNotEmpty()) return SavePriceItemResult.Invalid(errors)
        val name = draft.name.trim().replace(Regex("""\s+"""), " ")
        return db.withTransaction {
            val key = PriceListSearch.key(name)
            if (dao.getPartsIn(draft.category).any { it.id != draft.id && PriceListSearch.key(it.name) == key }) {
                return@withTransaction SavePriceItemResult.NameTaken
            }
            val entity = RepairPartEntity(
                id = draft.id,
                category = draft.category,
                name = name,
                description = draft.description.trim(),
                priceMinor = draft.price.minor,
                costMinor = draft.cost.minor,
            )
            if (draft.isNew) {
                SavePriceItemResult.Saved(dao.insert(entity))
            } else {
                val existing = dao.getPart(draft.id) ?: return@withTransaction SavePriceItemResult.Invalid(emptySet())
                // Keep what the form doesn't edit (the shared id).
                dao.update(entity.copy(syncId = existing.syncId, archived = existing.archived))
                SavePriceItemResult.Saved(draft.id)
            }
        }
    }

    override suspend fun deleteItem(id: Long): Boolean = dao.delete(id) > 0
}

private fun RepairPartEntity.toModel() = PriceItem(
    id = id,
    category = category,
    name = name,
    description = description,
    price = Money(priceMinor),
    cost = Money(costMinor),
)
