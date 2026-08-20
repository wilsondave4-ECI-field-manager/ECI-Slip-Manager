package za.co.eci.slipmanager.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import za.co.eci.slipmanager.data.Advance
import za.co.eci.slipmanager.data.MoneyReturn
import za.co.eci.slipmanager.data.PaymentType
import za.co.eci.slipmanager.data.Reimbursement
import za.co.eci.slipmanager.data.Slip
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val PDF_IMAGE_MAX_W = 1000
    private const val PDF_IMAGE_MAX_H = 1400
    private val za = Locale("en", "ZA")
    private val money = NumberFormat.getCurrencyInstance(za)
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", za)

    fun officePack(
        context: Context,
        title: String,
        advances: List<Advance>,
        slips: List<Slip>,
        returns: List<MoneyReturn>,
        reimbursements: List<Reimbursement> = emptyList()
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ECI_Office_Pack_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()

        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
        var y = 48f
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bodySmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }

        fun newTextPage() {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
            y = 45f
        }

        canvasOf(page).drawText("ECI Slip Manager", 36f, y, heading); y += 28f
        canvasOf(page).drawText(title, 36f, y, sub); y += 26f

        val advanceIds = advances.map { it.id }.toSet()
        val received = advances.sumOf { it.amountCents }
        val companySlipTotal = slips.filter { it.advanceId != null && it.advanceId in advanceIds }.sumOf { it.companyPaidCents }
        val returned = returns.sumOf { it.amountCents }
        val outstanding = received - companySlipTotal - returned
        val personalUsed = slips.sumOf { it.ownMoneyCents }
        val reimbursed = reimbursements.sumOf { it.amountCents }
        val personalOutstanding = personalUsed - reimbursed
        val receiptTotal = slips.sumOf { it.totalCents }
        val vatCaptured = slips.sumOf { it.vatCents ?: 0L }
        val exVatFromCaptured = (receiptTotal - vatCaptured).coerceAtLeast(0L)
        val missingVatCount = slips.count { it.vatCents == null }

        listOf(
            "Money received: ${fmt(received)}",
            "Company-funded slip value: ${fmt(companySlipTotal)}",
            "Money returned: ${fmt(returned)}",
            "Outstanding advance balance: ${fmt(outstanding)}",
            "Number of slips: ${slips.size}"
        ).forEach {
            canvasOf(page).drawText(it, 36f, y, body)
            y += 18f
        }

        y += 4f
        canvasOf(page).drawText("VAT summary", 36f, y, sub); y += 18f
        canvasOf(page).drawText("Receipt value incl. VAT where applicable: ${fmt(receiptTotal)}", 42f, y, body); y += 15f
        canvasOf(page).drawText("VAT captured: ${fmt(vatCaptured)}", 42f, y, body); y += 15f
        canvasOf(page).drawText("Receipt value less captured VAT: ${fmt(exVatFromCaptured)}", 42f, y, body); y += 15f
        if (missingVatCount > 0) {
            canvasOf(page).drawText("VAT NOT CAPTURED on $missingVatCount slip${if (missingVatCount == 1) "" else "s"}", 42f, y, sub)
            y += 18f
        }

        if (returns.isNotEmpty()) {
            y += 4f
            canvasOf(page).drawText("Money returned details", 36f, y, sub)
            y += 18f
            returns.sortedByDescending { it.dateEpochDay }.forEach { returnedMoney ->
                if (y > 755f) newTextPage()
                val date = LocalDate.ofEpochDay(returnedMoney.dateEpochDay).format(dateFmt)
                val advance = advances.firstOrNull { it.id == returnedMoney.advanceId }
                val advanceLabel = advance?.project?.ifBlank { "Advance #${advance.id}" } ?: "Advance #${returnedMoney.advanceId}"
                canvasOf(page).drawText("$date  |  ${fmt(returnedMoney.amountCents)}  |  $advanceLabel", 42f, y, body)
                y += 14f
                val reason = returnedMoney.notes.trim().ifBlank { "No reason entered" }
                wrapText("Reason: $reason", 82).forEach { reasonLine ->
                    if (y > 770f) newTextPage()
                    canvasOf(page).drawText(reasonLine, 54f, y, bodySmall)
                    y += 13f
                }
                y += 5f
            }
        }

        if (personalUsed > 0L || reimbursements.isNotEmpty()) {
            if (y > 705f) newTextPage()
            y += 8f
            canvasOf(page).drawLine(36f, y, PAGE_W - 36f, y, line); y += 20f
            canvasOf(page).drawText("Personal funds used", 36f, y, sub); y += 18f
            canvasOf(page).drawText("Used from own money: ${fmt(personalUsed)}", 42f, y, body); y += 15f
            canvasOf(page).drawText("Reimbursed: ${fmt(reimbursed)}", 42f, y, body); y += 15f
            canvasOf(page).drawText("Outstanding to employee: ${fmt(personalOutstanding)}", 42f, y, body); y += 20f

            slips.filter { it.ownMoneyCents > 0 }.sortedByDescending { it.dateEpochDay ?: Long.MIN_VALUE }.forEach { slip ->
                if (y > 770f) newTextPage()
                val date = slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).format(dateFmt) } ?: "Date missing"
                val supplier = slip.supplier.ifBlank { "Supplier missing" }
                canvasOf(page).drawText("$date  |  $supplier  |  Own money ${fmt(slip.ownMoneyCents)}", 42f, y, bodySmall)
                y += 14f
            }

            if (reimbursements.isNotEmpty()) {
                y += 4f
                canvasOf(page).drawText("Reimbursements", 42f, y, body); y += 15f
                reimbursements.sortedByDescending { it.dateEpochDay }.forEach { item ->
                    if (y > 770f) newTextPage()
                    val date = LocalDate.ofEpochDay(item.dateEpochDay).format(dateFmt)
                    val ref = item.reference.ifBlank { "No reference" }
                    canvasOf(page).drawText("$date  |  ${fmt(item.amountCents)}  |  $ref", 48f, y, bodySmall)
                    y += 13f
                }
            }
        }

        if (y > 765f) newTextPage()
        y += 10f
        canvasOf(page).drawLine(36f, y, PAGE_W - 36f, y, line); y += 22f
        canvasOf(page).drawText("Slip register", 36f, y, sub); y += 20f

        slips.sortedByDescending { it.dateEpochDay ?: Long.MIN_VALUE }.forEachIndexed { index, slip ->
            if (y > 750f) newTextPage()
            val date = slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).format(dateFmt) } ?: "Date missing"
            val supplier = slip.supplier.ifBlank { "Supplier missing" }
            val purpose = slip.purpose.ifBlank { "Purpose missing" }
            val vatText = slip.vatCents?.let(::fmt) ?: "NOT CAPTURED"
            val exVatText = slip.subtotalCents?.let(::fmt)
                ?: slip.vatCents?.let { fmt((slip.totalCents - it).coerceAtLeast(0L)) }
                ?: "NOT CAPTURED"
            canvasOf(page).drawText("${index + 1}. $date  |  $supplier  |  Receipt ${fmt(slip.totalCents)}", 36f, y, body)
            y += 14f
            canvasOf(page).drawText("    Ex VAT: $exVatText  |  VAT: $vatText  |  Total: ${fmt(slip.totalCents)}", 36f, y, bodySmall)
            y += 13f
            val funding = when (slip.paymentType) {
                PaymentType.ADVANCE -> "Company advance ${fmt(slip.companyPaidCents)}"
                PaymentType.OWN -> "Own money ${fmt(slip.ownMoneyCents)}"
                PaymentType.SPLIT -> "Company ${fmt(slip.companyPaidCents)} + Own ${fmt(slip.ownMoneyCents)}"
            }
            canvasOf(page).drawText("    Paid: $funding", 36f, y, bodySmall); y += 13f
            wrapText("    $purpose", 88).forEach { purposeLine ->
                if (y > 790f) newTextPage()
                canvasOf(page).drawText(purposeLine, 36f, y, body)
                y += 14f
            }
            y += 4f
        }
        doc.finishPage(page)

        slips.forEach { slip ->
            val bitmap = loadReceiptForPdf(slip.imagePath) ?: return@forEach
            val receiptPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
            drawReceiptPage(receiptPage.canvas, bitmap, slip)
            doc.finishPage(receiptPage)
            bitmap.recycle()
        }

        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun combinedDext(context: Context, slips: List<Slip>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ECI_Dext_Combined_${System.currentTimeMillis()}.pdf")
        val doc = PdfDocument()
        slips.forEachIndexed { i, slip ->
            val bitmap = loadReceiptForPdf(slip.imagePath) ?: return@forEachIndexed
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, i + 1).create())
            drawImageOnly(page.canvas, bitmap)
            doc.finishPage(page)
            bitmap.recycle()
        }
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun individualDext(context: Context, slips: List<Slip>): List<File> {
        val dir = File(context.cacheDir, "exports/dext_${System.currentTimeMillis()}").apply { mkdirs() }
        return slips.mapIndexedNotNull { index, slip ->
            val bitmap = loadReceiptForPdf(slip.imagePath) ?: return@mapIndexedNotNull null
            val safeSupplier = slip.supplier.ifBlank { "Unknown" }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(35)
            val date = slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "NoDate"
            val file = File(dir, "${date}_${safeSupplier}_${centsFile(slip.totalCents)}_${index + 1}.pdf")
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
            drawImageOnly(page.canvas, bitmap)
            doc.finishPage(page)
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            bitmap.recycle()
            file
        }
    }

    private fun loadReceiptForPdf(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > PDF_IMAGE_MAX_W * 2 || bounds.outHeight / sample > PDF_IMAGE_MAX_H * 2) sample *= 2
        val source = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return null
        val scale = minOf(1f, PDF_IMAGE_MAX_W.toFloat() / source.width.toFloat(), PDF_IMAGE_MAX_H.toFloat() / source.height.toFloat())
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)
        val prepared = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
        val preparedCanvas = Canvas(prepared)
        preparedCanvas.drawColor(Color.WHITE)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(grayscale) }
        preparedCanvas.drawBitmap(source, null, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), paint)
        source.recycle()
        return prepared
    }

    private fun drawReceiptPage(canvas: Canvas, bitmap: Bitmap, slip: Slip) {
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
        val vatText = slip.vatCents?.let(::fmt) ?: "NOT CAPTURED"
        val exVatText = slip.subtotalCents?.let(::fmt)
            ?: slip.vatCents?.let { fmt((slip.totalCents - it).coerceAtLeast(0L)) }
            ?: "NOT CAPTURED"
        canvas.drawText(slip.supplier.ifBlank { "Receipt" }, 30f, 30f, head)
        canvas.drawText("Ex VAT $exVatText   |   VAT $vatText   |   Total ${fmt(slip.totalCents)}", 30f, 46f, body)
        drawImageOnly(canvas, bitmap, top = 62f)
    }

    private fun drawImageOnly(canvas: Canvas, bitmap: Bitmap, top: Float = 24f) {
        val maxW = PAGE_W - 48f
        val maxH = PAGE_H - top - 24f
        val scale = minOf(maxW / bitmap.width.toFloat(), maxH / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (PAGE_W - w) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) current.append(word)
            else if (current.length + 1 + word.length <= maxChars) current.append(' ').append(word)
            else { lines += current.toString(); current = StringBuilder(word) }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines.ifEmpty { listOf(text) }
    }

    private fun canvasOf(page: PdfDocument.Page): Canvas = page.canvas
    private fun fmt(cents: Long): String = money.format(cents / 100.0)
    private fun centsFile(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0).replace('.', '_')
}
