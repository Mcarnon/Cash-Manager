package com.cashmanager.collector.importing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxReaderTest {
    @Test
    fun readsFirstWorksheetAndParsesIncomeExpenseRows() {
        val sheet = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>微信支付账单明细</t></is></c></row>
                <row r="2">
                  <c r="A2" t="inlineStr"><is><t>交易时间</t></is></c>
                  <c r="B2" t="inlineStr"><is><t>交易类型</t></is></c>
                  <c r="C2" t="inlineStr"><is><t>收/支</t></is></c>
                  <c r="D2" t="inlineStr"><is><t>金额(元)</t></is></c>
                  <c r="E2" t="inlineStr"><is><t>当前状态</t></is></c>
                  <c r="F2" t="inlineStr"><is><t>交易单号</t></is></c>
                </row>
                <row r="3">
                  <c r="A3" t="inlineStr"><is><t>2025-11-05 12:34:56</t></is></c>
                  <c r="B3" t="inlineStr"><is><t>商户消费</t></is></c>
                  <c r="C3" t="inlineStr"><is><t>支出</t></is></c>
                  <c r="D3" t="inlineStr"><is><t>1.00</t></is></c>
                  <c r="E3" t="inlineStr"><is><t>支付成功</t></is></c>
                  <c r="F3" t="inlineStr"><is><t>420000000001</t></is></c>
                </row>
              </sheetData>
            </worksheet>
        """.trimIndent()
        val xlsx = buildXlsx(sheet)
        val csv = XlsxReader.toCsv(xlsx)
        val parsed = StatementCsvParser.parse(csv)

        assertEquals(1, parsed.rows.size)
        assertEquals(100L, parsed.rows[0].amountCents)
        assertEquals("expense", parsed.rows[0].kind)
        assertEquals("420000000001", parsed.rows[0].externalTxnId)
    }

    private fun buildXlsx(sheetXml: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheetXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
