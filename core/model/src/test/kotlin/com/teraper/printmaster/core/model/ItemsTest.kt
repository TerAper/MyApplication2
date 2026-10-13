package com.teraper.printmaster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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
    fun pickingAModelListsItsPartsAndKeepsDescribedOnes() {
        val shoes = ClientPartDraft(typeId = 2, values = mapOf(9L to "30"))
        val draft = ClientItemDraft(clientId = 1, parts = listOf(shoes)).withItem(m125)
            .addPart(ClientPartDraft(typeId = 2, title = "CF283X"))
            .addPart(ClientPartDraft(typeId = 2, title = "cf 283x"))

        assertEquals(listOf("", "CF283A", "CF283X"), draft.parts.map { it.title })
        assertTrue(draft.parts.first().isSpec)
        // Described parts can repeat (two pairs of shoes); empty ones are not added.
        assertEquals(4, draft.addPart(shoes).parts.size)
        assertEquals(3, draft.addPart(ClientPartDraft(typeId = 2)).parts.size)
        assertEquals(listOf("CF283A", "CF283X"), draft.removePart(0).parts.map { it.title })
    }

    @Test
    fun editingAClientItemKeepsItsPartsAndOneOfAKindStaysOwn() {
        val owned = ClientItem(
            id = 3, clientId = 1, item = m125.copy(parts = emptyList()),
            parts = listOf(ClientItemPart(7, CatalogItem(5, cartridge, "CF283A"))),
        )
        val draft = ClientItemDraft.from(owned)
        assertEquals(listOf(7L to "CF283A"), draft.parts.map { it.id to it.title })
        assertEquals(1L, draft.item.id)

        val child = ItemType(9, "Child", fields = listOf(ItemField(10, 9, "Birth", FieldKind.DATE)), perClient = true)
        val david = ClientItem(4, 1, child, name = "David", values = mapOf(10L to "2019-03-12"))
        assertEquals("David", david.title)
        val childDraft = ClientItemDraft.from(david)
        assertEquals(ItemDraft(typeId = 9, name = "David", values = mapOf(10L to "2019-03-12")), childDraft.item)
    }

    @Test
    fun aDateFieldShowsTheAgeItGivesToday() {
        val child = ItemType(9, "Child", fields = listOf(ItemField(10, 9, "Birth", FieldKind.DATE), ItemField(11, 9, "Sex", FieldKind.CHOICE)))
        val values = mapOf(10L to "2019-03-12", 11L to "Boy")
        assertEquals("6 y · Boy", ItemTitles.details(child, values, LocalDate.of(2026, 3, 11)) { "$it y" })
        assertEquals("7 y · Boy", ItemTitles.details(child, values, LocalDate.of(2026, 3, 12)) { "$it y" })
        assertEquals(null, ItemTitles.age("not a date"))
    }

    @Test
    fun stockFitsWhenValuesAgreeAndMissingOnesMeanAny() {
        val shoe = ItemType(
            20, "Shoe", 9,
            listOf(ItemField(21, 20, "Size", FieldKind.NUMBER), ItemField(22, 20, "For", FieldKind.CHOICE), ItemField(23, 20, "Brand", inTitle = true)),
        )
        val wanted = ClientItemPart(1, shoe, values = mapOf(21L to "30", 22L to "Boy"))
        fun stock(id: Long, vararg values: Pair<Long, String>) = CatalogItem(id, shoe, "Runner", values.toMap())

        assertTrue(StockMatch.fits(wanted, stock(1, 21L to "30", 22L to "boy", 23L to "Nike")))
        assertTrue(StockMatch.fits(wanted, stock(2, 21L to "30.0")))
        assertFalse(StockMatch.fits(wanted, stock(3, 21L to "31", 22L to "Boy")))
        assertFalse(StockMatch.fits(wanted, stock(4, 21L to "30", 22L to "Girl")))
        // A part asking for no "For" takes any.
        assertTrue(StockMatch.fits(wanted.copy(values = mapOf(21L to "30")), stock(5, 21L to "30", 22L to "Girl")))
        // Another type never fits; a catalog part fits only itself.
        assertFalse(StockMatch.fits(wanted, CatalogItem(6, cartridge, "CF283A")))
        val exact = ClientItemPart(2, CatalogItem(1, shoe, "Runner"))
        assertTrue(StockMatch.fits(exact, stock(1)))
        assertFalse(StockMatch.fits(exact, stock(7)))
        assertEquals("Size 30 · Boy", wanted.title)
    }
}
