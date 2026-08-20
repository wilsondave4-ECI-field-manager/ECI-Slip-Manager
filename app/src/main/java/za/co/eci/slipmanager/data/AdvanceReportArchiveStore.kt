package za.co.eci.slipmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A permanently saved Office Pack for one completed advance. */
data class AdvanceReportArchiveEntry(
    val advanceId: Long,
    val closedAtMillis: Long,
    val documentNumber: String,
    val personalTransferredCents: Long,
    val pdfFileName: String
)

class AdvanceReportArchiveStore(context: Context) {
    private val appContext = context.applicationContext
    private val metadataFile = File(appContext.filesDir, METADATA_FILE_NAME)
    private val reportsDir = File(appContext.filesDir, REPORTS_DIR_NAME).apply { mkdirs() }

    fun entries(): List<AdvanceReportArchiveEntry> {
        if (!metadataFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(metadataFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        AdvanceReportArchiveEntry(
                            advanceId = o.getLong("advanceId"),
                            closedAtMillis = o.getLong("closedAtMillis"),
                            documentNumber = o.optString("documentNumber"),
                            personalTransferredCents = o.optLong("personalTransferredCents", 0L),
                            pdfFileName = o.getString("pdfFileName")
                        )
                    )
                }
            }.sortedByDescending { it.closedAtMillis }
        }.getOrDefault(emptyList())
    }

    fun forAdvance(advanceId: Long): AdvanceReportArchiveEntry? = entries().firstOrNull { it.advanceId == advanceId }

    fun archiveSettled(
        advance: Advance,
        slips: List<Slip>,
        returns: List<MoneyReturn>,
        documentNumber: String,
        createReport: (List<Slip>, List<MoneyReturn>, String) -> File
    ): AdvanceReportArchiveEntry? {
        forAdvance(advance.id)?.let { return it }
        val advanceSlips = slips.filter { it.advanceId == advance.id }
        val advanceReturns = returns.filter { it.advanceId == advance.id }
        val hasActivity = advanceSlips.isNotEmpty() || advanceReturns.isNotEmpty()
        val balance = advance.amountCents - advanceSlips.sumOf { it.companyPaidCents } - advanceReturns.sumOf { it.amountCents }
        if (!hasActivity || balance != 0L) return null

        val now = System.currentTimeMillis()
        val personalTransferred = advanceSlips.sumOf { it.ownMoneyCents }
        val tempPdf = createReport(advanceSlips, advanceReturns, documentNumber)
        val safeNumber = documentNumber.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val fileName = "${safeNumber}_Advance.pdf"
        val permanentPdf = File(reportsDir, fileName)
        tempPdf.copyTo(permanentPdf, overwrite = true)

        val entry = AdvanceReportArchiveEntry(
            advanceId = advance.id,
            closedAtMillis = now,
            documentNumber = documentNumber,
            personalTransferredCents = personalTransferred,
            pdfFileName = fileName
        )
        save(entries() + entry)
        return entry
    }

    fun reportFile(entry: AdvanceReportArchiveEntry): File = File(reportsDir, entry.pdfFileName)

    fun clear() {
        metadataFile.delete()
        reportsDir.deleteRecursively()
        reportsDir.mkdirs()
    }

    private fun save(items: List<AdvanceReportArchiveEntry>) {
        val array = JSONArray()
        items.distinctBy { it.advanceId }.sortedBy { it.closedAtMillis }.forEach { item ->
            array.put(JSONObject().apply {
                put("advanceId", item.advanceId)
                put("closedAtMillis", item.closedAtMillis)
                put("documentNumber", item.documentNumber)
                put("personalTransferredCents", item.personalTransferredCents)
                put("pdfFileName", item.pdfFileName)
            })
        }
        metadataFile.writeText(array.toString(2))
    }

    companion object {
        const val METADATA_FILE_NAME = "advance_report_archive.json"
        const val REPORTS_DIR_NAME = "advance_report_archive"
    }
}
