package com.teraper.printmaster.core.data.excel

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One spreadsheet cell. Amounts are whole dram. */
internal sealed interface Cell {
    data class Text(val value: String, val bold: Boolean = false) : Cell
    data class Amount(val dram: Long, val bold: Boolean = false) : Cell
    data object Empty : Cell
}

/**
 * The smallest .xlsx Excel and Google Sheets open: one sheet, bold and
 * thousands-separated amounts. Enough for reports; no library needed.
 */
internal object XlsxWriter {

    fun write(out: OutputStream, sheetName: String, columnWidths: List<Int>, rows: List<List<Cell>>) {
        ZipOutputStream(out).use { zip ->
            zip.file("[Content_Types].xml", CONTENT_TYPES)
            zip.file("_rels/.rels", ROOT_RELS)
            zip.file("xl/workbook.xml", workbook(sheetName))
            zip.file("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.file("xl/styles.xml", STYLES)
            zip.file("xl/worksheets/sheet1.xml", sheet(columnWidths, rows))
        }
    }

    private fun ZipOutputStream.file(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbook(sheetName: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${sheetName.take(31).escape()}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private fun sheet(columnWidths: List<Int>, rows: List<List<Cell>>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        if (columnWidths.isNotEmpty()) {
            append("<cols>")
            columnWidths.forEachIndexed { i, width -> append("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""") }
            append("</cols>")
        }
        append("<sheetData>")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = columnName(c) + (r + 1)
                when (cell) {
                    is Cell.Text -> append("""<c r="$ref" t="inlineStr"${if (cell.bold) " s=\"1\"" else ""}><is><t xml:space="preserve">${cell.value.escape()}</t></is></c>""")
                    is Cell.Amount -> append("""<c r="$ref" s="${if (cell.bold) 3 else 2}"><v>${cell.dram}</v></c>""")
                    Cell.Empty -> Unit
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    fun columnName(index: Int): String {
        var n = index + 1
        val name = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            name.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return name.toString()
    }

    private fun String.escape(): String = buildString {
        for (ch in this@escape) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                // Control characters are not allowed in XML.
                ch < ' ' && ch != '\t' && ch != '\n' -> Unit
                else -> append(ch)
            }
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

    // Styles: 0 plain, 1 bold text, 2 amount "#,##0", 3 bold amount.
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="3" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
