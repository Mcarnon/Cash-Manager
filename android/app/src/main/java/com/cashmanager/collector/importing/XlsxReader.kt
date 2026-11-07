package com.cashmanager.collector.importing

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XlsxReader {
    private val sheetNamePattern = Regex("""xl/worksheets/sheet(\d+)\.xml""")

    fun toCsv(bytes: ByteArray): String {
        val entries = readZipEntries(bytes)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::readSharedStrings).orEmpty()
        val sheetName = entries.keys
            .filter { sheetNamePattern.matches(it) }
            .sortedBy { name -> sheetNamePattern.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            .firstOrNull()
            ?: throw StatementParseException("Excel 文件中未找到工作表")

        val rows = readSheet(entries.getValue(sheetName), sharedStrings)
        if (rows.isEmpty()) throw StatementParseException("Excel 工作表为空")
        return rowsToCsv(rows)
    }

    fun isXlsx(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    fun isOldXls(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 0xD0.toByte() &&
            bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() &&
            bytes[3] == 0xE0.toByte()
    }

    private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun readSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        val nodes = document.getElementsByTagName("si")
        val strings = mutableListOf<String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as? Element ?: continue
            val textNodes = element.getElementsByTagName("t")
            val text = buildString {
                for (textIndex in 0 until textNodes.length) {
                    append(textNodes.item(textIndex).textContent.orEmpty())
                }
            }
            strings += text
        }
        return strings
    }

    private fun readSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val document = parseXml(bytes)
        val rowNodes = document.getElementsByTagName("row")
        val rows = mutableListOf<List<String>>()
        for (rowIndex in 0 until rowNodes.length) {
            val rowElement = rowNodes.item(rowIndex) as? Element ?: continue
            val cellNodes = rowElement.getElementsByTagName("c")
            val cells = sortedMapOf<Int, String>()
            for (cellIndex in 0 until cellNodes.length) {
                val cell = cellNodes.item(cellIndex) as? Element ?: continue
                val column = columnIndex(cell.getAttribute("r"))
                if (column < 0) continue
                val value = cellValue(cell, sharedStrings)
                if (value.isNotEmpty()) cells[column] = value
            }
            if (cells.isEmpty()) continue
            val maxColumn = cells.keys.maxOrNull() ?: continue
            val row = MutableList(maxColumn + 1) { "" }
            cells.forEach { (column, value) -> row[column] = value }
            rows += row
        }
        return rows
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        return when (type) {
            "s" -> {
                val index = firstTagText(cell, "v")?.toIntOrNull()
                index?.let { sharedStrings.getOrNull(it) }.orEmpty()
            }
            "inlineStr" -> firstTagText(cell, "t").orEmpty()
            "str" -> firstTagText(cell, "v").orEmpty()
            "b" -> if (firstTagText(cell, "v") == "1") "TRUE" else "FALSE"
            else -> firstTagText(cell, "v").orEmpty()
        }
    }

    private fun firstTagText(element: Element, tag: String): String? {
        val nodes = element.getElementsByTagName(tag)
        return if (nodes.length > 0) nodes.item(0).textContent else null
    }

    private fun columnIndex(reference: String): Int {
        if (reference.isBlank()) return -1
        var result = 0
        var foundLetter = false
        for (char in reference) {
            if (char in 'A'..'Z') {
                foundLetter = true
                result = result * 26 + (char - 'A' + 1)
            } else if (char in 'a'..'z') {
                foundLetter = true
                result = result * 26 + (char - 'a' + 1)
            } else {
                break
            }
        }
        return if (foundLetter) result - 1 else -1
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: Exception) {
        }
    }

    private fun rowsToCsv(rows: List<List<String>>): String {
        return rows.joinToString("\n") { row ->
            row.joinToString(",") { cell ->
                val value = cell.replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
                if (value.contains(',') || value.contains('"') || value.contains('\n')) {
                    "\"" + value.replace("\"", "\"\"") + "\""
                } else {
                    value
                }
            }
        }
    }
}
