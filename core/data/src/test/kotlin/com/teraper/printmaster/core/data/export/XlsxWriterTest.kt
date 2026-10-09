package com.teraper.printmaster.core.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun files(rows: List<List<Cell>>): Map<String, String> {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(out, "Պարտքեր", listOf(30, 12), rows)
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    @Test
    fun writesAllPartsExcelNeeds() {
        val files = files(listOf(listOf(Cell.Text("A"))))
        assertEquals(
            setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml"),
            files.keys,
        )
        assertTrue(files.getValue("xl/workbook.xml").contains("name=\"Պարտքեր\""))
    }

    @Test
    fun cellsAreTypedAndEscaped() {
        val sheet = files(
            listOf(
                listOf(Cell.Text("«Ալֆա» & <Co>", bold = true), Cell.Empty, Cell.Amount(12_500)),
                listOf(Cell.Amount(-300, bold = true)),
            ),
        ).getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("""<c r="A1" t="inlineStr" s="1"><is><t xml:space="preserve">«Ալֆա» &amp; &lt;Co&gt;</t></is></c>"""))
        assertTrue(sheet.contains("""<c r="C1" s="2"><v>12500</v></c>"""))
        assertTrue(sheet.contains("""<c r="A2" s="3"><v>-300</v></c>"""))
        assertTrue(!sheet.contains("r=\"B1\""))
    }

    @Test
    fun columnNames() {
        assertEquals(listOf("A", "Z", "AA", "AB", "ZZ", "AAA"), listOf(0, 25, 26, 27, 701, 702).map(XlsxWriter::columnName))
    }
}
