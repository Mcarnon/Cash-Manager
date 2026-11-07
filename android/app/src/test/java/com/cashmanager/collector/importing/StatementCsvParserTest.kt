package com.cashmanager.collector.importing

import org.junit.Assert.assertEquals
import org.junit.Test

class StatementCsvParserTest {
    @Test
    fun parsesOfficialCsvWithoutSummaryTotals() {
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            2025-11-05 12:34:56,商户消费,测试商户,午饭,支出,1.00,零钱,支付成功,420000000001,merchant-1,备注
            2025-11-05 13:00:00,转账,张三,转账,收入,2.50,零钱,已收款,420000000002,merchant-2,
        """.trimIndent()

        val parsed = StatementCsvParser.parse(csv)

        assertEquals(2, parsed.rows.size)
        assertEquals(100L, parsed.rows[0].amountCents)
        assertEquals("expense", parsed.rows[0].kind)
        assertEquals("420000000001", parsed.rows[0].externalTxnId)
        assertEquals(250L, parsed.rows[1].amountCents)
        assertEquals("income", parsed.rows[1].kind)
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun skipsAmbiguousAmountRowsInsteadOfFailingWholeImport() {
        val csv = """
            交易时间,收/支,金额(元),当前状态,交易单号
            2025-11-05 12:34:56,支出,1.00元 2.00,支付成功,420000000001
            2025-11-05 13:00:00,支出,3.00,支付成功,420000000002
        """.trimIndent()

        val parsed = StatementCsvParser.parse(csv)

        assertEquals(1, parsed.rows.size)
        assertEquals(300L, parsed.rows[0].amountCents)
        assertEquals(1, parsed.skippedRows)
    }

    @Test
    fun recordsNeutralSelfTransferRowsWithNeutralKind() {
        val csv = """
            微信支付账单明细
            交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
            46331.38868055555,零钱通转出-到工商银行(5547),/,/,/,1,零钱通,资金已到账,18000108682611050522043077686451,/,/
            46331.43824074074,商户消费,中山职业技术学院,POS机扫微信二维码消费,支出,13,零钱通,支付成功,4200003227202611051883633041,merchant,/
        """.trimIndent()

        val parsed = StatementCsvParser.parse(csv)

        assertEquals(2, parsed.rows.size)
        assertEquals("neutral", parsed.rows[0].kind)
        assertEquals(100L, parsed.rows[0].amountCents)
        assertEquals("expense", parsed.rows[1].kind)
        assertEquals(1300L, parsed.rows[1].amountCents)
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun parsesSeparateIncomeAndExpenseColumns() {
        val csv = """
            交易日期,收入金额,支出金额,交易状态,流水号
            2025-11-05,0.00,1.00,成功,abc123
        """.trimIndent()

        val parsed = StatementCsvParser.parse(csv)

        assertEquals(1, parsed.rows.size)
        assertEquals(100L, parsed.rows[0].amountCents)
        assertEquals("expense", parsed.rows[0].kind)
    }

    @Test
    fun recordsRowsWithUnknownDirectionAsNeutral() {
        val csv = """
            交易日期,收/支,金额(元),交易状态,流水号
            2025-11-05,其他,1.00,成功,abc123
            2025-11-06,支出,2.00,成功,abc124
        """.trimIndent()

        val parsed = StatementCsvParser.parse(csv)

        assertEquals(2, parsed.rows.size)
        assertEquals("neutral", parsed.rows[0].kind)
        assertEquals(100L, parsed.rows[0].amountCents)
        assertEquals("expense", parsed.rows[1].kind)
        assertEquals(0, parsed.skippedRows)
    }

    @Test
    fun marksSelfTransferContextAsNeutral() {
        val csv = """
            交易时间,交易类型,交易对方,商品,收/支,金额(元),当前状态,交易单号
            2025-11-05 12:00:00,本人账户转账,张三,转账,支出,10.00,成功,self-1
            2025-11-05 12:30:00,商户消费,测试商户,午饭,支出,3.00,成功,normal-1
        """.trimIndent()

        val parsed = StatementCsvParser.parse(csv)

        assertEquals(2, parsed.rows.size)
        assertEquals("neutral", parsed.rows[0].kind)
        assertEquals(1000L, parsed.rows[0].amountCents)
        assertEquals("expense", parsed.rows[1].kind)
        assertEquals(0, parsed.skippedRows)
    }
}
