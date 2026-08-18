package za.co.eci.slipmanager.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import za.co.eci.slipmanager.data.Advance
import za.co.eci.slipmanager.data.MoneyReturn
import za.co.eci.slipmanager.data.PaymentType
import za.co.eci.slipmanager.data.Reimbursement
import za.co.eci.slipmanager.data.Slip
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupManager {
    fun createBackup(
        context: Context,
        advances: List<Advance>,
        slips: List<Slip>,
        returns: List<MoneyReturn>,
        reimbursements: List<Reimbursement> = emptyList()
    ): File {
        val outDir = File(context.cacheDir, "backup").apply { mkdirs() }
        val file = File(outDir, "ECI_Slip_Manager_Backup_${System.currentTimeMillis()}.zip")
        val json = JSONObject().apply {
            put("version", 3)
            put("advances", JSONArray().apply { advances.forEach { put(advanceJson(it)) } })
            put("slips", JSONArray().apply { slips.forEach { put(slipJson(it)) } })
            put("returns", JSONArray().apply { returns.forEach { put(returnJson(it)) } })
            put("reimbursements", JSONArray().apply { reimbursements.forEach { put(reimbursementJson(it)) } })
        }
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.toString(2).toByteArray())
            zip.closeEntry()
            slips.forEach { slip ->
                val src = File(slip.imagePath)
                if (src.exists()) {
                    zip.putNextEntry(ZipEntry("receipts/${src.name}"))
                    src.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return file
    }

    data class Restored(
        val advances: List<Advance>,
        val slips: List<Slip>,
        val returns: List<MoneyReturn>,
        val reimbursements: List<Reimbursement>
    )

    fun restore(context: Context, zipFile: File): Restored {
        val receiptsDir = File(context.filesDir, "receipts").apply { mkdirs() }
        ZipFile(zipFile).use { zip ->
            val dataEntry = zip.getEntry("data.json") ?: error("Backup does not contain data.json")
            val root = JSONObject(zip.getInputStream(dataEntry).bufferedReader().use { it.readText() })
            val advances = root.getJSONArray("advances").mapObjects(::advanceFromJson)
            val originalSlips = root.getJSONArray("slips").mapObjects(::slipFromJson)
            val returns = root.getJSONArray("returns").mapObjects(::returnFromJson)
            val reimbursements = if (root.has("reimbursements")) {
                root.getJSONArray("reimbursements").mapObjects(::reimbursementFromJson)
            } else emptyList()

            val slips = originalSlips.map { slip ->
                val oldName = File(slip.imagePath).name
                val entry = zip.getEntry("receipts/$oldName")
                if (entry != null) {
                    val target = File(receiptsDir, oldName)
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    slip.copy(imagePath = target.absolutePath)
                } else slip
            }
            return Restored(advances, slips, returns, reimbursements)
        }
    }

    private fun advanceJson(a: Advance) = JSONObject().apply {
        put("id", a.id); put("date", a.dateEpochDay); put("amount", a.amountCents)
        put("reference", a.reference); put("project", a.project); put("notes", a.notes)
        put("archived", a.archived)
        if (a.archivedAtMillis == null) put("archivedAt", JSONObject.NULL) else put("archivedAt", a.archivedAtMillis)
    }

    private fun slipJson(s: Slip) = JSONObject().apply {
        put("id", s.id); if (s.advanceId == null) put("advanceId", JSONObject.NULL) else put("advanceId", s.advanceId)
        put("supplier", s.supplier); if (s.dateEpochDay == null) put("date", JSONObject.NULL) else put("date", s.dateEpochDay)
        put("receiptNumber", s.receiptNumber)
        if (s.subtotalCents == null) put("subtotal", JSONObject.NULL) else put("subtotal", s.subtotalCents)
        if (s.vatCents == null) put("vat", JSONObject.NULL) else put("vat", s.vatCents)
        put("total", s.totalCents); put("purpose", s.purpose); put("project", s.project)
        put("paymentReference", s.paymentReference); put("imagePath", s.imagePath); put("ocrText", s.ocrText)
        put("createdAt", s.createdAtMillis)
        put("paymentType", s.paymentType.name)
        put("ownMoney", s.ownMoneyCents)
    }

    private fun returnJson(r: MoneyReturn) = JSONObject().apply {
        put("id", r.id); put("advanceId", r.advanceId); put("date", r.dateEpochDay); put("amount", r.amountCents); put("notes", r.notes)
    }

    private fun reimbursementJson(r: Reimbursement) = JSONObject().apply {
        put("id", r.id); put("date", r.dateEpochDay); put("amount", r.amountCents); put("reference", r.reference); put("notes", r.notes)
    }

    private fun advanceFromJson(o: JSONObject) = Advance(
        id = o.getLong("id"),
        dateEpochDay = o.getLong("date"),
        amountCents = o.getLong("amount"),
        reference = o.optString("reference"),
        project = o.optString("project"),
        notes = o.optString("notes"),
        archived = o.optBoolean("archived", false),
        archivedAtMillis = if (!o.has("archivedAt") || o.isNull("archivedAt")) null else o.getLong("archivedAt")
    )

    private fun slipFromJson(o: JSONObject) = Slip(
        id = o.getLong("id"),
        advanceId = if (o.isNull("advanceId")) null else o.getLong("advanceId"),
        supplier = o.optString("supplier"),
        dateEpochDay = if (o.isNull("date")) null else o.getLong("date"),
        receiptNumber = o.optString("receiptNumber"),
        subtotalCents = if (o.isNull("subtotal")) null else o.getLong("subtotal"),
        vatCents = if (o.isNull("vat")) null else o.getLong("vat"),
        totalCents = o.getLong("total"),
        purpose = o.optString("purpose"),
        project = o.optString("project"),
        paymentReference = o.optString("paymentReference"),
        imagePath = o.optString("imagePath"),
        ocrText = o.optString("ocrText"),
        createdAtMillis = o.optLong("createdAt"),
        paymentType = runCatching { PaymentType.valueOf(o.optString("paymentType", "ADVANCE")) }.getOrDefault(PaymentType.ADVANCE),
        ownMoneyCents = o.optLong("ownMoney", 0L)
    )

    private fun returnFromJson(o: JSONObject) = MoneyReturn(
        o.getLong("id"), o.getLong("advanceId"), o.getLong("date"), o.getLong("amount"), o.optString("notes")
    )

    private fun reimbursementFromJson(o: JSONObject) = Reimbursement(
        id = o.getLong("id"),
        dateEpochDay = o.getLong("date"),
        amountCents = o.getLong("amount"),
        reference = o.optString("reference"),
        notes = o.optString("notes")
    )

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> = buildList {
        for (i in 0 until length()) add(mapper(getJSONObject(i)))
    }
}
