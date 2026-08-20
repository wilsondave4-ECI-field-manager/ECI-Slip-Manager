package za.co.eci.slipmanager.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Allocates stable accounting document numbers such as ECI-SM-20260820-1.
 * Counters are persisted in private app storage and are shared by all Office
 * Pack report types so a number is never reused for a different report.
 */
class DocumentNumberStore(context: Context) {
    private val appContext = context.applicationContext
    private val counterFile = File(appContext.filesDir, FILE_NAME)

    fun nextNumber(prefixOverride: String? = null): String = synchronized(lock) {
        val prefs = appContext.getSharedPreferences("settings", 0)
        val prefix = normalizePrefix(
            prefixOverride ?: prefs.getString("document_prefix", DEFAULT_PREFIX).orEmpty()
        )
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        val key = "$prefix|$date"
        val root = readCounters()
        val next = root.optInt(key, 0) + 1
        root.put(key, next)
        writeCounters(root)
        "$prefix-$date-$next"
    }

    private fun readCounters(): JSONObject = runCatching {
        if (counterFile.exists()) JSONObject(counterFile.readText()) else JSONObject()
    }.getOrElse { JSONObject() }

    private fun writeCounters(root: JSONObject) {
        val temp = File(counterFile.parentFile, "${counterFile.name}.tmp")
        temp.writeText(root.toString(2))
        if (!temp.renameTo(counterFile)) {
            counterFile.writeText(root.toString(2))
            temp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "document_number_counters.json"
        const val DEFAULT_PREFIX = "ECI-SM"
        private val lock = Any()

        fun normalizePrefix(raw: String): String {
            val cleaned = raw.trim()
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]+"), "-")
                .trim('-')
            return cleaned.ifBlank { DEFAULT_PREFIX }
        }
    }
}
