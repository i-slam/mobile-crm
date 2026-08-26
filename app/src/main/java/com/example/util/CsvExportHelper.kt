package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.CallRecord
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportHelper {

    private val HEADER = listOf(
        "id", "phone_number", "caller_name", "call_type", "start_time", "end_time",
        "duration_seconds", "notes", "tags", "actions_taken", "sync_status"
    )

    private fun escapeCsv(value: String): String {
        val needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }

    fun exportRecordsToCsv(context: Context, records: List<CallRecord>): Uri? {
        return try {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val file = File(exportsDir, "call_notes_${fileNameFormat.format(Date())}.csv")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            FileWriter(file).use { writer ->
                writer.append(HEADER.joinToString(",") { escapeCsv(it) })
                writer.append("\n")
                records.forEach { record ->
                    val row = listOf(
                        record.id.toString(),
                        record.phoneNumber,
                        record.callerName,
                        record.callType,
                        dateFormat.format(Date(record.startTimeMillis)),
                        dateFormat.format(Date(record.endTimeMillis)),
                        record.durationSeconds.toString(),
                        record.notes,
                        record.tags.joinToString("; "),
                        record.actionsTaken.joinToString("; "),
                        record.syncStatus
                    )
                    writer.append(row.joinToString(",") { escapeCsv(it) })
                    writer.append("\n")
                }
            }

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    fun shareCsv(context: Context, uri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val chooser = Intent.createChooser(shareIntent, "Export call history via...").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share exported file", Toast.LENGTH_SHORT).show()
        }
    }
}
