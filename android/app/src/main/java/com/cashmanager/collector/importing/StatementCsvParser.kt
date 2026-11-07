package com.cashmanager.collector.importing

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object StatementCsvParser {
    private val datePatterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.ROOT),
    )

    private val aliases = mapOf(
        "date" to listOf("交易时间", "交易日期", "记账日期", "日期", "入账日期", "发生时间"),
        "amount" to listOf("金额(元)", "金额（元）", "金额", "交易金额", "发生额", "交易金额(元)", "交易金额（元）"),
        "incomeAmount" to listOf("收入金额", "收入额", "贷方金额", "存入金额"),
        "expenseAmount" to listOf("支出金额", "支出额", "借方金额", "取出金额"),
        "direction" to listOf("收/支", "收支", "收入/支出", "借贷标志", "借贷", "交易方向"),
        "status" to listOf("当前状态", "交易状态", "状态", "交易结果"),
        "txnId" to listOf("交易单号", "交易订单号", "交易号", "流水号", "交易流水号", "银行流水号", "订单号"),
        "merchant" to listOf("交易对方", "对方户名", "商户名称", "收款方", "付款方", "对方名称"),
        "item" to listOf("商品", "商品说明", "摘要", "用途", "交易说明", "商品名称"),
        "method" to listOf("支付方式", "收/付款方式", "收付款方式", "付款方式", "交易渠道"),
        "category" to listOf("交易类型", "交易分类", "分类"),
        "note" to listOf("备注", "附言"),
    )

    private val failureStatusWords = listOf("失败", "已关闭", "已撤销", "交易关闭", "已取消")
    private val neutralDirectionWords = listOf(
        "不计收支",
        "不计入收支",
        "仅记录",
        "中性",
        "其他",
        "/",
    )
    private val neutralContextWords = listOf(
        "本人账户",
        "本人名下",
        "同名账户",
        "自己名下",
        "内部转账",
        "账户互转",
        "自转",
        "信用卡还款",
        "自动还款",
        "零钱通转出",
        "零钱通转入",
        "余额宝转出",
        "余额宝转入",
    )

    fun parse(input: String): ParsedStatement {
        val lines = input.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) throw StatementParseException("账单文件为空")

        val headerIndex = lines.indexOfFirst { looksLikeHeader(parseCsvLine(it)) }
        if (headerIndex < 0) {
            throw StatementParseException("未找到可识别的账单表头")
        }
        val header = parseCsvLine(lines[headerIndex]).map(::normalizeHeader)
        val columns = ColumnMap(header)

        val rows = mutableListOf<ParsedStatementRow>()
        var skipped = 0

        fun skip() {
            skipped++
        }

        for (line in lines.drop(headerIndex + 1)) {
            val fields = parseCsvLine(line)
            if (fields.all { it.isBlank() }) continue

            val dateText = columns.value(fields, "date")
            if (dateText.isBlank()) continue
            if (dateText.contains("收入") || dateText.contains("支出") ||
                dateText.contains("合计") || dateText.contains("总计")
            ) {
                continue
            }

            val date = parseDate(dateText)
            if (date == null) {
                skip()
                continue
            }

            val amountCell = columns.value(fields, "amount")
            val incomeCell = columns.value(fields, "incomeAmount")
            val expenseCell = columns.value(fields, "expenseAmount")
            val explicitDirection = columns.value(fields, "direction")
            var amountRaw = amountCell
            var direction = explicitDirection

            if (amountRaw.isBlank() && (incomeCell.isNotBlank() || expenseCell.isNotBlank())) {
                val incomeCents = incomeCell.takeIf { it.isNotBlank() }?.let(::amountTextToCents)
                val expenseCents = expenseCell.takeIf { it.isNotBlank() }?.let(::amountTextToCents)
                when {
                    incomeCents != null && expenseCents != null && incomeCents > 0 && expenseCents > 0 -> {
                        skip()
                        continue
                    }
                    incomeCents != null && incomeCents > 0 -> {
                        amountRaw = incomeCell
                        direction = "收入"
                    }
                    expenseCents != null && expenseCents > 0 -> {
                        amountRaw = expenseCell
                        direction = "支出"
                    }
                    else -> {
                        skip()
                        continue
                    }
                }
            }

            if (amountRaw.isBlank()) {
                skip()
                continue
            }

            val cents = amountTextToCents(amountRaw)
            if (cents == null) {
                skip()
                continue
            }

            val status = columns.value(fields, "status").ifBlank { null }
            if (status != null && failureStatusWords.any { status.contains(it) }) {
                skip()
                continue
            }

            val category = columns.value(fields, "category").ifBlank { null }
            val merchant = columns.value(fields, "merchant").ifBlank { null }
            val item = columns.value(fields, "item").ifBlank { null }
            val note = listOfNotNull(item, columns.value(fields, "note").ifBlank { null })
                .distinct()
                .joinToString(" · ")
                .ifBlank { null }

            val signed = amountRaw.trim()
            if (direction.isBlank()) {
                direction = when {
                    signed.startsWith("-") -> "支出"
                    signed.startsWith("+") -> "收入"
                    else -> ""
                }
            }
            val directionKind = directionToKind(direction)
            val neutralContext = listOfNotNull(category, merchant, note).joinToString(" ")
            val isNeutral = directionKind == null ||
                neutralDirectionWords.any { direction.contains(it) } ||
                neutralContextWords.any { neutralContext.contains(it) }
            val kind = if (isNeutral) "neutral" else directionKind.orEmpty()

            val txnId = columns.value(fields, "txnId").ifBlank { null }

            rows += ParsedStatementRow(
                date = date,
                amountCents = cents,
                amountRaw = amountRaw.trim(),
                kind = kind,
                category = category,
                merchant = merchant,
                note = note,
                paymentMethod = columns.value(fields, "method").ifBlank { null },
                externalTxnId = txnId,
                rawRow = line,
            )
        }

        if (rows.isEmpty()) {
            throw StatementParseException("账单中没有可导入的交易记录")
        }

        return ParsedStatement(
            rows = rows,
            skippedRows = skipped,
        )
    }

    private fun looksLikeHeader(fields: List<String>): Boolean {
        val normalized = fields.map(::normalizeHeader).toSet()
        val dateMatched = aliases.getValue("date").any { normalizeHeader(it) in normalized }
        val amountMatched = aliases.getValue("amount").any { normalizeHeader(it) in normalized } ||
            aliases.getValue("incomeAmount").any { normalizeHeader(it) in normalized } ||
            aliases.getValue("expenseAmount").any { normalizeHeader(it) in normalized }
        return dateMatched && amountMatched
    }

    private fun normalizeHeader(value: String): String =
        value.removePrefix("\uFEFF")
            .replace(" ", "")
            .replace("\t", "")
            .replace("（", "(")
            .replace("）", ")")
            .trim()

    private fun directionToKind(value: String): String? {
        val text = value.trim()
        return when {
            text.contains("收入") || text.contains("收款") || text.contains("转入") ||
                text.contains("贷") -> "income"
            text.contains("支出") || text.contains("付款") || text.contains("转出") ||
                text.contains("借") -> "expense"
            else -> null
        }
    }

    private fun parseDate(value: String): String? {
        val cleaned = value.trim()
        val excelSerial = cleaned.toDoubleOrNull()
        if (excelSerial != null && excelSerial >= 1.0 && excelSerial <= 2_958_465.0) {
            return try {
                LocalDate.of(1899, 12, 30).plusDays(excelSerial.toLong()).toString()
            } catch (_: Exception) {
                null
            }
        }
        for (formatter in datePatterns) {
            try {
                return when {
                    cleaned.length <= 10 -> LocalDate.parse(cleaned, formatter).toString()
                    else -> LocalDateTime.parse(cleaned, formatter).toLocalDate().toString()
                }
            } catch (_: Exception) {
            }
        }
        val prefix = cleaned.take(19)
        for (formatter in datePatterns) {
            try {
                return LocalDateTime.parse(prefix, formatter).toLocalDate().toString()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun amountTextToCents(raw: String): Long? {
        val normalized = raw.trim()
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(",", "")
            .replace(" ", "")
            .removePrefix("+")
            .removePrefix("-")
        if (normalized.isBlank()) return null
        val amount = normalized.toBigDecimalOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null
        return try {
            amount.movePointRight(2)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString().trim()
        return result
    }

    private class ColumnMap(headers: List<String>) {
        private val indices = mutableMapOf<String, Int>()

        init {
            aliases.forEach { (key, names) ->
                val normalizedNames = names.map { normalizeHeader(it) }.toSet()
                val index = headers.indexOfFirst { it in normalizedNames }
                if (index >= 0) indices[key] = index
            }
        }

        fun value(fields: List<String>, key: String): String {
            val index = indices[key] ?: return ""
            return fields.getOrNull(index).orEmpty().trim()
        }
    }
}
