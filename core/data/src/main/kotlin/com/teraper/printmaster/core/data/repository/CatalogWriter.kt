package com.teraper.printmaster.core.data.repository

import com.teraper.printmaster.core.database.dao.CatalogDao
import com.teraper.printmaster.core.database.entity.BrandEntity
import com.teraper.printmaster.core.database.entity.CartridgeChipCrossRef
import com.teraper.printmaster.core.database.entity.CartridgeEntity
import com.teraper.printmaster.core.database.entity.ChipEntity
import com.teraper.printmaster.core.database.entity.ModelCartridgeCrossRef
import com.teraper.printmaster.core.database.entity.PrinterModelEntity
import com.teraper.printmaster.core.model.CartridgeDraft
import com.teraper.printmaster.core.model.CatalogNames
import com.teraper.printmaster.core.model.PrinterModelDraft
import javax.inject.Inject

/**
 * Find-or-create for catalog names, shared by the catalog and client-printer forms.
 * Names are matched ignoring case and spacing, so typing "hp" reuses "HP".
 * Call inside a transaction.
 */
internal class CatalogWriter @Inject constructor(private val dao: CatalogDao) {

    suspend fun brandId(name: String): Long {
        val clean = CatalogNames.clean(name)
        return dao.getBrands().firstOrNull { CatalogNames.key(it.name) == CatalogNames.key(clean) }?.id
            ?: dao.insertBrand(BrandEntity(name = clean))
    }

    /** Model with the same brand and name, other than [excludeId]. */
    suspend fun findModel(brandId: Long, name: String, excludeId: Long = 0): PrinterModelEntity? =
        dao.getModelEntities().firstOrNull {
            it.id != excludeId && it.brandId == brandId && CatalogNames.key(it.name) == CatalogNames.key(name)
        }

    /** Creates the model, or returns the existing one with the same brand and name. */
    suspend fun findOrCreateModel(draft: PrinterModelDraft): Long {
        val brandId = brandId(draft.brand)
        return findModel(brandId, draft.name)?.id ?: dao.insertModel(
            PrinterModelEntity(brandId = brandId, name = draft.name, printType = draft.printType, colorType = draft.colorType),
        )
    }

    /**
     * Links [cartridges] to the model and returns their ids by [CartridgeDraft.key].
     * With [exact], cartridges and chips missing from the list are unlinked; otherwise only added.
     */
    suspend fun linkCartridges(modelId: Long, cartridges: List<CartridgeDraft>, exact: Boolean): Map<String, Long> {
        val existing = dao.getCartridges().associateByTo(mutableMapOf()) { CatalogNames.codeKey(it.name) }
        val ids = cartridges.associate { draft ->
            val found = existing[draft.key]
            // The catalog editor may fix the spelling, e.g. "cf283a" → "CF283A".
            if (found != null && exact && found.name != draft.name) dao.renameCartridge(found.id, draft.name)
            val id = found?.id ?: dao.insertCartridge(CartridgeEntity(name = draft.name)).also {
                existing[draft.key] = CartridgeEntity(it, draft.name)
            }
            linkChips(id, draft.chipNames(), exact)
            draft.key to id
        }
        dao.linkModelCartridges(ids.values.map { ModelCartridgeCrossRef(modelId, it) })
        if (exact) dao.unlinkModelCartridgesExcept(modelId, ids.values.toList())
        return ids
    }

    private suspend fun linkChips(cartridgeId: Long, names: List<String>, exact: Boolean) {
        if (names.isEmpty() && !exact) return
        val existing = dao.getChips().associateBy { CatalogNames.codeKey(it.name) }
        val chipIds = names.map { name ->
            existing[CatalogNames.codeKey(name)]?.id ?: dao.insertChip(ChipEntity(name = name))
        }
        dao.linkCartridgeChips(chipIds.map { CartridgeChipCrossRef(cartridgeId, it) })
        if (exact) dao.unlinkCartridgeChipsExcept(cartridgeId, chipIds)
    }

    /** Removes brands, cartridges and chips nothing points to any more. */
    suspend fun deleteUnused() {
        dao.deleteUnusedBrands()
        dao.deleteUnusedCartridges()
        dao.deleteUnusedChips()
    }
}
