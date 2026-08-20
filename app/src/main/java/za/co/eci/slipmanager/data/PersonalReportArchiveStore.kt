package za.co.eci.slipmanager.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Keeps completed own-money settlements separate from the next live report.
 * The final Office Pack PDF is copied into private app storage so Archive can
 * share the exact report that was closed, even after new slips are captured.
 */
data class PersonalReportArchiveEntry(
    val id: Long,
    val closedAtMillis: Long,
    val fromSlipIdExclusive: Long,
    val toSlipIdInclusive: Long,
    val fromReimbursementIdExclusive: Long,
    val toReimbursementIdInclusive: Long,
    val usedCents: Long,
    val reimbursedCents: Long,
    val pdfFileName: String
)

class PersonalReportArchiveStore(context: Context) {
    private val appContext = context.applicationContext
    private val metadataFile = File(appContext.filesDir, METADATA_FILE_NAME)
    private val reportsDir = File(appContext.filesDir, REPORTS_DIR_NAME).apply { mkdirs() }

    fun entries(): List<PersonalReportArchiveEntry> {
        if (!metadataFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(metadataFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        PersonalReportArchiveEntry(
                            id = o.getLong("id"),
                            closedAtMillis = o.getLong("closedAtMillis"),
                            fromSlipIdExclusive = o.optLong("fromSlipIdExclusive", 0L),
                            toSlipIdInclusive = o.optLong("toSlipIdInclusive", 0L),
                            fromReimbursementIdExclusive = o.optLong("fromReimbursementIdExclusive", 0L),
                            toReimbursementIdInclusive = o.optLong("toReimbursementIdInclusive", 0L),
                            usedCents = o.getLong("usedCents"),
                            reimbursedCents = o.getLong("reimbursedCents"),
                            pdfFileName = o.getString("pdfFileName")
                        )
                    )
                }
            }.sortedByDescending { it.closedAtMillis }
        }.getOrDefault(emptyList())
    }

    fun latestSlipId(): Long = entries().maxOfOrNull { it.toSlipIdInclusive } ?: 0L

    fun latestReimbursementId(): Long = entries().maxOfOrNull { it.toReimbursementIdInclusive } ?: 0L

    fun currentSummary(slips: List<Slip>, reimbursements: List<Reimbursement>): PersonalFundsSummary {
        val slipFloor = latestSlipId()
        val reimbursementFloor = latestReimbursementId()
        return PersonalFundsSummary(
            usedCents = slips.filter { it.id > slipFloor }.sumOf { it.ownMoneyCents },
            reimbursedCents = reimbursements.filter { it.id > reimbursementFloor }.sumOf { it.amountCents }
        )
    }

    fun currentSlips(slips: List<Slip>): List<Slip> {
        val floor = latestSlipId()
        return slips.filter { it.id > floor && it.ownMoneyCents > 0L }
    }

    fun currentReimbursements(reimbursements: List<Reimbursement>): List<Reimbursement> {
        val floor = latestReimbursementId()
        return reimbursements.filter { it.id > floor }
    }

    fun slipsFor(entry: PersonalReportArchiveEntry, slips: List<Slip>): List<Slip> = slips.filter {
        it.id > entry.fromSlipIdExclusive && it.id <= entry.toSlipIdInclusive && it.ownMoneyCents > 0L
    }

    fun reimbursementsFor(entry: PersonalReportArchiveEntry, reimbursements: List<Reimbursement>): List<Reimbursement> = reimbursements.filter {
        it.id > entry.fromReimbursementIdExclusive && it.id <= entry.toReimbursementIdInclusive
    }

    /**
     * Closes only when this live own-money batch has been fully reimbursed.
     * Returns null while money is still owed or when there is nothing to close.
     */
    fun closeIfSettled(
        slips: List<Slip>,
        reimbursements: List<Reimbursement>,
        createReport: (List<Slip>, List<Reimbursement>) -> File
    ): PersonalReportArchiveEntry? {
        val existing = entries()
        val fromSlip = existing.maxOfOrNull { it.toSlipIdInclusive } ?: 0L
        val fromReimbursement = existing.maxOfOrNull { it.toReimbursementIdInclusive } ?: 0L
        val settlementSlips = slips.filter { it.id > fromSlip && it.ownMoneyCents > 0L }
        val settlementReimbursements = reimbursements.filter { it.id > fromReimbursement }
        val used = settlementSlips.sumOf { it.ownMoneyCents }
        val reimbursed = settlementReimbursements.sumOf { it.amountCents }

        if (used <= 0L || reimbursed != used) return null

        val now = System.currentTimeMillis()
        val tempPdf = createReport(settlementSlips, settlementReimbursements)
        val fileName = "ECI_Own_Money_Settlement_${now}.pdf"
        val permanentPdf = File(reportsDir, fileName)
        tempPdf.copyTo(permanentPdf, overwrite = true)

        val entry = PersonalReportArchiveEntry(
            id = now,
            closedAtMillis = now,
            fromSlipIdExclusive = fromSlip,
            toSlipIdInclusive = settlementSlips.maxOfOrNull { it.id } ?: fromSlip,
            fromReimbursementIdExclusive = fromReimbursement,
            toReimbursementIdInclusive = settlementReimbursements.maxOfOrNull { it.id } ?: fromReimbursement,
            usedCents = used,
            reimbursedCents = reimbursed,
            pdfFileName = fileName
        )
        save(existing + entry)
        return entry
    }

    fun reportFile(entry: PersonalReportArchiveEntry): File = File(reportsDir, entry.pdfFileName)

    fun clear() {
        metadataFile.delete()
        reportsDir.deleteRecursively()
        reportsDir.mkdirs()
    }

    private fun save(items: List<PersonalReportArchiveEntry>) {
        val array = JSONArray()
        items.sortedBy { it.closedAtMillis }.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("closedAtMillis", item.closedAtMillis)
                put("fromSlipIdExclusive", item.fromSlipIdExclusive)
                put("toSlipIdInclusive", item.toSlipIdInclusive)
                put("fromReimbursementIdExclusive", item.fromReimbursementIdExclusive)
                put("toReimbursementIdInclusive", item.toReimbursementIdInclusive)
                put("usedCents", item.usedCents)
                put("reimbursedCents", item.reimbursedCents)
                put("pdfFileName", item.pdfFileName)
            })
        }
        metadataFile.writeText(array.toString(2))
    }

    companion object {
        const val METADATA_FILE_NAME = "personal_report_archive.json"
        const val REPORTS_DIR_NAME = "personal_report_archive"
    }
}
