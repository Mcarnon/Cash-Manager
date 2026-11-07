package com.cashmanager.collector.importing

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cashmanager.collector.R
import com.cashmanager.collector.TransactionDatabase
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class StatementImporter(private val database: TransactionDatabase) {

    fun import(context: Context, sourceId: Long, uri: Uri): ImportReport {
        val source = database.findSourceProfile(sourceId)
            ?: return ImportReport(false, context.getString(R.string.import_source_missing))

        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    database.markImportFailed(source.id)
                    return ImportReport(false, context.getString(R.string.import_open_failed))
                }
        } catch (error: Exception) {
            database.markImportFailed(source.id)
            return ImportReport(false, context.getString(R.string.import_file_unreadable))
        }

        val fileName = queryDisplayName(context, uri)
        val statementText = try {
            when {
                XlsxReader.isOldXls(bytes) ->
                    throw StatementParseException(context.getString(R.string.import_xls_unsupported))
                XlsxReader.isXlsx(bytes) || fileName.endsWith(".xlsx", ignoreCase = true) ||
                    fileName.endsWith(".xlsm", ignoreCase = true) -> XlsxReader.toCsv(bytes)
                else -> decodeStatement(bytes)
            }
        } catch (error: Exception) {
            database.markImportFailed(source.id)
            val message = if (error is StatementParseException && error.message != null) {
                error.message!!
            } else {
                context.getString(R.string.import_file_unreadable)
            }
            return ImportReport(false, message)
        }

        val parsed = try {
            StatementCsvParser.parse(statementText)
        } catch (_: StatementParseException) {
            database.markImportFailed(source.id)
            return ImportReport(false, context.getString(R.string.import_parse_failed))
        }

        return database.saveImportedStatement(
            sourceId = source.id,
            fileName = fileName,
            statement = parsed,
        )
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: context.getString(R.string.import_default_file_name)
        } catch (_: Exception) {
            context.getString(R.string.import_default_file_name)
        }
    }

    private fun decodeStatement(bytes: ByteArray): String {
        if (bytes.isEmpty()) throw IllegalArgumentException("empty")
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
                .removePrefix("\uFEFF")
        } catch (_: CharacterCodingException) {
            String(bytes, charset("GBK")).removePrefix("\uFEFF")
        }
    }
}
