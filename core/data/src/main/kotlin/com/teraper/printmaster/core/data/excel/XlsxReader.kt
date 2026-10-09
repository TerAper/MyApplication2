package com.teraper.printmaster.core.data.excel

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Reads the first sheet of an .xlsx file as rows of text. Numbers come as written in the file
 * ("4000", "45870.67"); dates stored as numbers come as their serial day ("45870"), and the
 * parsers that know which columns are dates turn them back. Empty cells are null.
 */
internal object XlsxReader {

    fun read(input: InputStream): List<List<String?>> {
        val parts = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                if (name.endsWith(".xml") || name.endsWith(".rels")) parts[name] = zip.readBytes()
            }
        }
        require("xl/workbook.xml" in parts) { "Not an Excel workbook" }
        val shared = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val sheet = parts[firstSheetPath(parts)] ?: parts.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()?.let { parts[it] }
            ?: error("No sheet in the workbook")
        return rows(sheet, shared)
    }

    private fun parse(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        factory.newSAXParser().parse(bytes.inputStream(), handler)
    }

    private fun firstSheetPath(parts: Map<String, ByteArray>): String? {
        var relId: String? = null
        parse(
            parts.getValue("xl/workbook.xml"),
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    if (relId == null && qName.endsWith("sheet")) relId = attributes.getValue("r:id")
                }
            },
        )
        val rels = parts["xl/_rels/workbook.xml.rels"] ?: return null
        var target: String? = null
        parse(
            rels,
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    if (qName.endsWith("Relationship") && attributes.getValue("Id") == relId) target = attributes.getValue("Target")
                }
            },
        )
        return target?.let { if (it.startsWith("/")) it.removePrefix("/") else "xl/" + it.removePrefix("./") }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val strings = ArrayList<String>()
        val current = StringBuilder()
        var inText = false
        parse(
            bytes,
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    when (qName.substringAfter(':')) {
                        "si" -> current.setLength(0)
                        "t" -> inText = true
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName.substringAfter(':')) {
                        "si" -> strings += current.toString()
                        "t" -> inText = false
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inText) current.appendRange(ch, start, start + length)
                }
            },
        )
        return strings
    }

    private fun rows(bytes: ByteArray, shared: List<String>): List<List<String?>> {
        val rows = ArrayList<List<String?>>()
        var row: Array<String?> = emptyArray()
        var rowIndex = 0
        var column = 0
        var type: String? = null
        var capture = false
        val value = StringBuilder()
        parse(
            bytes,
            object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    when (qName.substringAfter(':')) {
                        "row" -> {
                            val r = attributes.getValue("r")?.toIntOrNull()
                            // Rows missing from the file are empty rows.
                            if (r != null) while (rows.size < r - 1) rows.add(emptyList())
                            row = arrayOfNulls(16)
                            rowIndex = 0
                        }
                        "c" -> {
                            column = attributes.getValue("r")?.let(::columnIndex) ?: rowIndex
                            rowIndex = column + 1
                            type = attributes.getValue("t")
                            value.setLength(0)
                        }
                        "v", "t" -> capture = true
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName.substringAfter(':')) {
                        "v", "t" -> capture = false
                        "c" -> {
                            val raw = value.toString()
                            val text = when (type) {
                                "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            if (!text.isNullOrEmpty()) {
                                if (column >= row.size) row = row.copyOf(maxOf(column + 1, row.size * 2))
                                row[column] = text
                            }
                        }
                        "row" -> rows.add(row.toList().dropLastWhile { it == null })
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (capture) value.appendRange(ch, start, start + length)
                }
            },
        )
        return rows
    }

    /** "B12" → 1, "AA3" → 26. */
    fun columnIndex(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }
}
