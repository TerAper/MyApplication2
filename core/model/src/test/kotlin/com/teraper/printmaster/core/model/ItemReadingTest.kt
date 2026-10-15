package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemReadingTest {

    private val brand = ItemField(1, 1, "Brand", inTitle = true)
    private val printer = ItemType(1, "Printer", fields = listOf(brand))
    private val color = ItemField(2, 2, "Color", FieldKind.CHOICE, listOf("Black", "Cyan"))
    private val pages = ItemField(3, 2, "Pages", FieldKind.NUMBER)
    private val chip = ItemField(4, 2, "Chip", FieldKind.YES_NO)
    private val cartridge = ItemType(2, "Cartridge", parentId = 1, fields = listOf(color, pages, chip))
    private val child = ItemType(3, "Child", perClient = true)
    private val types = listOf(printer, cartridge, child)

    private val reading = ItemReading(
        type = "cartridge",
        name = "CF283A",
        fields = mapOf("color" to "black", "Pages" to "1 500", "chip" to "yes", "Weight" to "1kg"),
        fitsInto = listOf("LaserJet M125", "LaserJet M201"),
    )

    @Test
    fun aReadingFillsTypeNameFieldsAndModels() {
        val known = listOf(ItemRef(7, 1, "Printer", "HP LaserJet M125"))
        val draft = ItemReadings.apply(ItemDraft(barcode = "123"), reading, types, known)
        assertEquals(2L, draft.typeId)
        assertEquals("CF283A", draft.name)
        // Choices only from the options; "1 500" isn't a number; unknown fields are dropped.
        assertEquals(mapOf(2L to "Black", 4L to ItemTitles.YES), draft.values)
        // A known printer is linked; an unknown one is added by name.
        assertEquals(listOf(7L to "HP LaserJet M125", 0L to "LaserJet M201"), draft.fitsInto.map { it.id to it.title })
        assertEquals("123", draft.barcode)
    }

    @Test
    fun whatTheUserTypedStays() {
        val typed = ItemDraft(typeId = 1, name = "My name", values = mapOf(1L to "HP"))
        val draft = ItemReadings.apply(typed, reading.copy(fields = mapOf("Brand" to "Canon")), types)
        assertEquals(typed, draft)
    }

    @Test
    fun anUnknownTypeOnlyGivesAName() {
        val draft = ItemReadings.apply(ItemDraft(), ItemReading(type = "Child", name = "Shoe"), types)
        assertEquals(0L, draft.typeId)
        assertEquals("Shoe", draft.name)
    }

    @Test
    fun thePromptListsTheUsersTypesButNotOneOfAKindOnes() {
        val prompt = ItemReadings.prompt(types, "4960999", "hy")
        assertTrue(prompt.contains("\"Cartridge\" (fits into \"Printer\")"))
        assertTrue(prompt.contains("\"Color\" one of \"Black\", \"Cyan\""))
        assertTrue(prompt.contains("4960999"))
        assertTrue(!prompt.contains("Child"))
    }
}
