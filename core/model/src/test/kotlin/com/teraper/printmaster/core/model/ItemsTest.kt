package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemsTest {

    private val printer = ItemType(
        1, "Printer",
        fields = listOf(
            ItemField(1, 1, "Brand", inTitle = true),
            ItemField(2, 1, "Print type", FieldKind.CHOICE, listOf("Laser", "Inkjet")),
            ItemField(3, 1, "Wi-Fi", FieldKind.YES_NO),
            ItemField(4, 1, "Speed", FieldKind.NUMBER),
        ),
    )
    private val cartridge = ItemType(2, "Cartridge", parentId = 1)
    private val chip = ItemType(3, "Chip", parentId = 2)
    private val shoes = ItemType(4, "Shoes")

    private val m125 = CatalogItem(
        id = 1, type = printer, name = "LaserJet M125",
        values = mapOf(1L to "HP", 2L to "Laser", 3L to ItemTitles.YES, 4L to "20"),
        parts = listOf(ItemRef(5, 2, "Cartridge", "CF283A")),
    )

    @Test
    fun titleFieldsGoBeforeTheNameAndTheRestAreDetails() {
        assertEquals("HP LaserJet M125", m125.title)
        assertEquals("Laser · Wi-Fi · Speed 20", m125.details)
        assertEquals("LaserJet M125", m125.copy(values = emptyMap()).title)
    }

    @Test
    fun namesIgnoreCaseSpacesAndDashes() {
        assertEquals(CatalogNames.codeKey("CF283A"), CatalogNames.codeKey(" cf 283-a "))
        assertEquals("hp", CatalogNames.key("  HP "))
        assertEquals(listOf("S", "M", "L"), CatalogNames.splitList("S\nM, L; s"))
    }

    @Test
    fun searchFindsItemByTitleFieldTypeOrPart() {
        assertTrue(CatalogSearch.matches(m125, "hp m125"))
        assertTrue(CatalogSearch.matches(m125, "laser"))
        assertTrue(CatalogSearch.matches(m125, "printer"))
        assertTrue(CatalogSearch.matches(m125, "cf283"))
        assertTrue(CatalogSearch.matches(m125, "CF 283A"))
        assertFalse(CatalogSearch.matches(m125, "canon"))
    }

    @Test
    fun typesTreeKeepsPartsUnderTheirParents() {
        val tree = ItemTypes.tree(listOf(chip, shoes, cartridge, printer))
        assertEquals(listOf("Printer" to 0, "Cartridge" to 1, "Chip" to 2, "Shoes" to 0), tree.map { it.type.name to it.depth })
        assertEquals(setOf(1L, 2L, 3L), ItemTypes.selfAndBelow(listOf(printer, cartridge, chip, shoes), 1))
        assertEquals(listOf(cartridge), ItemTypes.partTypes(listOf(printer, cartridge, chip), 1))
    }

    @Test
    fun typeDraftDropsEmptyAndRepeatedFieldsAndNeedsChoices() {
        val draft = ItemTypeDraft(
            name = " Shirt ",
            fields = listOf(FieldDraft(name = "Size", kind = FieldKind.CHOICE, options = "S\nM"), FieldDraft(name = " "), FieldDraft(name = "size")),
        )
        assertEquals(listOf("Size"), draft.normalized().fields.map { it.name })
        assertEquals("Shirt", draft.normalized().name)
        assertEquals(setOf(ItemTypeError.NAME_REQUIRED), ItemTypeDraft().validate())
        assertEquals(setOf(ItemTypeError.CHOICES_REQUIRED), ItemTypeDraft(name = "Shirt", fields = listOf(FieldDraft(name = "Size", kind = FieldKind.CHOICE))).validate())
    }

    @Test
    fun itemDraftNeedsTypeAndName() {
        assertEquals(setOf(ItemDraftError.TYPE_REQUIRED, ItemDraftError.NAME_REQUIRED), ItemDraft().validate())
        assertTrue(ItemDraft(typeId = 1, name = "M125").validate().isEmpty())
        assertEquals("HP M125", ItemDraft(typeId = 1, name = "M125", values = mapOf(1L to "HP")).title(printer))
    }

    @Test
    fun linksAreAddedOnceByTypeAndName() {
        val draft = ItemDraft(typeId = 1, name = "M125")
            .withPart(LinkDraft(typeId = 2, title = "CF283A"))
            .withPart(LinkDraft(typeId = 2, title = "cf 283a"))
            .withPart(LinkDraft(typeId = 2, title = " "))
        assertEquals(listOf("CF283A"), draft.parts.map { it.title })
    }

    @Test
    fun pickingAModelTicksAllItsPartsAndNewOnesAreTickedToo() {
        val draft = ClientItemDraft(clientId = 1).withItem(m125).addPart(LinkDraft(typeId = 2, title = "CF283X"))

        assertEquals(listOf("CF283A", "CF283X"), draft.chosenParts().map { it.title })
        assertEquals(listOf("CF283X"), draft.togglePart(LinkDraft.keyOf(2, "CF283A")).chosenParts().map { it.title })
    }

    @Test
    fun editingAClientItemKeepsPartsRemovedFromTheModel() {
        val owned = ClientItem(
            id = 3, clientId = 1, item = m125.copy(parts = emptyList()),
            parts = listOf(ClientItemPart(7, CatalogItem(5, cartridge, "CF283A"))),
        )
        val draft = ClientItemDraft.from(owned)
        assertEquals(listOf("CF283A"), draft.chosenParts().map { it.title })
    }
}
