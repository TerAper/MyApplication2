package com.teraper.printmaster.core.data.excel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxReaderTest {

    @Test
    fun readsWhatTheWriterWrote() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(out, "S", emptyList(), listOf(listOf(Cell.Text("Ամսաթիվ"), Cell.Empty, Cell.Amount(4000)), emptyList(), listOf(Cell.Text("x & y"))))
        val rows = XlsxReader.read(out.toByteArray().inputStream())
        assertEquals(listOf(listOf("Ամսաթիվ", null, "4000"), emptyList(), listOf("x & y")), rows)
    }

    @Test
    fun readsSharedStringsAndSkippedRows() {
        val bytes = ByteArrayOutputStream().also { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun file(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry()
                }
                file("xl/workbook.xml", """<workbook xmlns:r="r"><sheets><sheet name="A" r:id="rId7"/></sheets></workbook>""")
                file("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId7" Target="worksheets/data.xml"/></Relationships>""")
                file("xl/sharedStrings.xml", """<sst><si><t>Հ/Հ</t></si><si><r><t>Սերիա </t></r><r><t>և համար</t></r></si></sst>""")
                file(
                    "xl/worksheets/data.xml",
                    """<worksheet><sheetData><row r="2"><c r="A2" t="s"><v>0</v></c><c r="C2" t="s"><v>1</v></c><c r="D2"><v>45870.5</v></c></row></sheetData></worksheet>""",
                )
            }
        }.toByteArray()
        assertEquals(listOf(emptyList(), listOf("Հ/Հ", null, "Սերիա և համար", "45870.5")), XlsxReader.read(bytes.inputStream()))
    }

    @Test
    fun columnLetters() {
        assertEquals(listOf(0, 1, 25, 26, 32), listOf("A1", "B12", "Z9", "AA3", "AG368").map(XlsxReader::columnIndex))
    }
}
