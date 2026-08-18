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
import za.co.eci.slipmanager.data.Advance
import za.co.eci.slipmanager.data.MoneyReturn
import za.co.eci.slipmanager.data.Slip
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfExporter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MAX_IMAGE_W = 1200
    private const val MAX_IMAGE_H = 1700
    private const val TARGET_JPEG_BYTES = 700_000
    private val za = Locale("en", "ZA")
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", za)

    private data class CompressedImage(val bytes: ByteArray, val width: Int, val height: Int)
    private data class TextDraw(val text: String, val x: Float, val y: Float, val size: Float, val bold: Boolean = false)
    private sealed interface PageSpec
    private data class TextPage(val lines: List<TextDraw>) : PageSpec
    private data class ImagePage(val image: CompressedImage, val supplier: String? = null, val totalCents: Long? = null) : PageSpec

    fun officePack(
        context: Context,
        title: String,
        advances: List<Advance>,
        slips: List<Slip>,
        returns: List<MoneyReturn>
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ECI_Office_Pack_${System.currentTimeMillis()}.pdf")
        val pages = mutableListOf<PageSpec>()

        val received = advances.sumOf { it.amountCents }
        val slipTotal = slips.sumOf { it.totalCents }
        val returned = returns.sumOf { it.amountCents }
        val outstanding = received - slipTotal - returned

        var lines = mutableListOf<TextDraw>()
        var y = 795f
        lines += TextDraw("ECI Slip Manager", 36f, y, 22f, true); y -= 30f
        lines += TextDraw(title, 36f, y, 12f, true); y -= 28f
        listOf(
            "Money received: ${fmt(received)}",
            "Receipts captured: ${fmt(slipTotal)}",
            "Money returned: ${fmt(returned)}",
            "Outstanding balance: ${fmt(outstanding)}",
            "Number of slips: ${slips.size}"
        ).forEach {
            lines += TextDraw(it, 36f, y, 10f)
            y -= 18f
        }
        y -= 12f
        lines += TextDraw("Slip register", 36f, y, 12f, true); y -= 22f

        slips.sortedByDescending { it.dateEpochDay ?: Long.MIN_VALUE }.forEachIndexed { index, slip ->
            if (y < 72f) {
                pages += TextPage(lines)
                lines = mutableListOf()
                y = 795f
                lines += TextDraw("Slip register continued", 36f, y, 12f, true); y -= 24f
            }
            val date = slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).format(dateFmt) } ?: "Date missing"
            val supplier = slip.supplier.ifBlank { "Supplier missing" }
            val purpose = slip.purpose.ifBlank { "Purpose missing" }
            lines += TextDraw("${index + 1}. $date | $supplier | ${fmt(slip.totalCents)}", 36f, y, 9.5f); y -= 15f
            lines += TextDraw("    $purpose", 36f, y, 9f); y -= 19f
        }
        pages += TextPage(lines)

        slips.forEach { slip ->
            compressReceiptForPdf(slip.imagePath)?.let { image ->
                pages += ImagePage(image, slip.supplier.ifBlank { "Receipt" }, slip.totalCents)
            }
        }

        writePdf(file, pages)
        return file
    }

    fun combinedDext(context: Context, slips: List<Slip>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "ECI_Dext_Combined_${System.currentTimeMillis()}.pdf")
        val pages = slips.mapNotNull { slip -> compressReceiptForPdf(slip.imagePath)?.let { ImagePage(it) } }
        writePdf(file, pages)
        return file
    }

    fun individualDext(context: Context, slips: List<Slip>): List<File> {
        val dir = File(context.cacheDir, "exports/dext_${System.currentTimeMillis()}").apply { mkdirs() }
        return slips.mapIndexedNotNull { index, slip ->
            val image = compressReceiptForPdf(slip.imagePath) ?: return@mapIndexedNotNull null
            val safeSupplier = slip.supplier.ifBlank { "Unknown" }.replace(Regex("[^A-Za-z0-9_-]"), "_").take(35)
            val date = slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "NoDate"
            val file = File(dir, "${date}_${safeSupplier}_${centsFile(slip.totalCents)}_${index + 1}.pdf")
            writePdf(file, listOf(ImagePage(image)))
            file
        }
    }

    private fun compressReceiptForPdf(path: String): CompressedImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_W * 2 || bounds.outHeight / sample > MAX_IMAGE_H * 2) {
            sample *= 2
        }

        val source = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        ) ?: return null

        val scale = minOf(
            1f,
            MAX_IMAGE_W.toFloat() / source.width.toFloat(),
            MAX_IMAGE_H.toFloat() / source.height.toFloat()
        )
        val targetW = (source.width * scale).toInt().coerceAtLeast(1)
        val targetH = (source.height * scale).toInt().coerceAtLeast(1)

        val prepared = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
        val canvas = Canvas(prepared)
        canvas.drawColor(Color.WHITE)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, null, RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), paint)
        source.recycle()

        var bytes = ByteArray(0)
        for (quality in intArrayOf(72, 64, 56, 48)) {
            val out = ByteArrayOutputStream()
            prepared.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            if (bytes.size <= TARGET_JPEG_BYTES) break
        }

        var finalBitmap = prepared
        if (bytes.size > TARGET_JPEG_BYTES && prepared.width > 850) {
            val smallerScale = minOf(850f / prepared.width.toFloat(), 1250f / prepared.height.toFloat(), 1f)
            val smaller = Bitmap.createScaledBitmap(
                prepared,
                (prepared.width * smallerScale).toInt().coerceAtLeast(1),
                (prepared.height * smallerScale).toInt().coerceAtLeast(1),
                true
            )
            val out = ByteArrayOutputStream()
            smaller.compress(Bitmap.CompressFormat.JPEG, 52, out)
            bytes = out.toByteArray()
            finalBitmap = smaller
            prepared.recycle()
        }

        val result = CompressedImage(bytes, finalBitmap.width, finalBitmap.height)
        finalBitmap.recycle()
        return result
    }

    private fun writePdf(file: File, pages: List<PageSpec>) {
        val pdf = RawPdfWriter(file)
        val catalogId = pdf.reserve()
        val pagesId = pdf.reserve()
        val regularFontId = pdf.reserve()
        val boldFontId = pdf.reserve()
        pdf.set(regularFontId, ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"))
        pdf.set(boldFontId, ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>"))

        val pageIds = mutableListOf<Int>()
        pages.forEach { spec ->
            val pageId = pdf.reserve()
            val contentId = pdf.reserve()
            pageIds += pageId

            when (spec) {
                is TextPage -> {
                    val content = buildString {
                        spec.lines.forEach { line ->
                            val font = if (line.bold) "F2" else "F1"
                            append("BT /$font ${num(line.size)} Tf ${num(line.x)} ${num(line.y)} Td (${escapePdf(line.text)}) Tj ET\n")
                        }
                    }
                    pdf.set(contentId, streamObject("", ascii(content)))
                    pdf.set(
                        pageId,
                        ascii("<< /Type /Page /Parent $pagesId 0 R /MediaBox [0 0 $PAGE_W $PAGE_H] /Resources << /Font << /F1 $regularFontId 0 R /F2 $boldFontId 0 R >> >> /Contents $contentId 0 R >>")
                    )
                }

                is ImagePage -> {
                    val imageId = pdf.reserve()
                    val imageDict = "/Type /XObject /Subtype /Image /Width ${spec.image.width} /Height ${spec.image.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Interpolate true"
                    pdf.set(imageId, streamObject(imageDict, spec.image.bytes))

                    val top = if (spec.supplier != null) 62f else 24f
                    val maxW = PAGE_W - 48f
                    val maxH = PAGE_H - top - 24f
                    val drawScale = minOf(maxW / spec.image.width.toFloat(), maxH / spec.image.height.toFloat())
                    val drawW = spec.image.width * drawScale
                    val drawH = spec.image.height * drawScale
                    val left = (PAGE_W - drawW) / 2f
                    val bottom = PAGE_H - top - drawH

                    val content = buildString {
                        if (spec.supplier != null) {
                            append("BT /F2 13 Tf 30 812 Td (${escapePdf(spec.supplier)}) Tj ET\n")
                            spec.totalCents?.let {
                                append("BT /F1 9 Tf 30 796 Td (${escapePdf("Total ${fmt(it)}")}) Tj ET\n")
                            }
                        }
                        append("q ${num(drawW)} 0 0 ${num(drawH)} ${num(left)} ${num(bottom)} cm /Im1 Do Q\n")
                    }
                    pdf.set(contentId, streamObject("", ascii(content)))
                    pdf.set(
                        pageId,
                        ascii("<< /Type /Page /Parent $pagesId 0 R /MediaBox [0 0 $PAGE_W $PAGE_H] /Resources << /Font << /F1 $regularFontId 0 R /F2 $boldFontId 0 R >> /XObject << /Im1 $imageId 0 R >> >> /Contents $contentId 0 R >>")
                    )
                }
            }
        }

        val kids = pageIds.joinToString(" ") { "$it 0 R" }
        pdf.set(pagesId, ascii("<< /Type /Pages /Count ${pageIds.size} /Kids [$kids] >>"))
        pdf.set(catalogId, ascii("<< /Type /Catalog /Pages $pagesId 0 R >>"))
        pdf.write(catalogId)
    }

    private class RawPdfWriter(private val file: File) {
        private val objects = mutableListOf<ByteArray?>()

        fun reserve(): Int {
            objects += null
            return objects.size
        }

        fun set(id: Int, bytes: ByteArray) {
            objects[id - 1] = bytes
        }

        fun write(rootId: Int) {
            BufferedOutputStream(FileOutputStream(file)).use { out ->
                var offset = 0L
                fun write(bytes: ByteArray) {
                    out.write(bytes)
                    offset += bytes.size
                }

                write(ascii("%PDF-1.4\n%ECI\n"))
                val offsets = LongArray(objects.size + 1)
                objects.forEachIndexed { index, bytes ->
                    val id = index + 1
                    offsets[id] = offset
                    write(ascii("$id 0 obj\n"))
                    write(bytes ?: error("Missing PDF object $id"))
                    write(ascii("\nendobj\n"))
                }

                val xrefOffset = offset
                write(ascii("xref\n0 ${objects.size + 1}\n"))
                write(ascii("0000000000 65535 f \n"))
                for (id in 1..objects.size) {
                    write(ascii(String.format(Locale.US, "%010d 00000 n \n", offsets[id])))
                }
                write(ascii("trailer\n<< /Size ${objects.size + 1} /Root $rootId 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"))
            }
        }
    }

    private fun streamObject(dict: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ascii("<< ${dict.trim()} /Length ${data.size} >>\nstream\n"))
        out.write(data)
        out.write(ascii("\nendstream"))
        return out.toByteArray()
    }

    private fun ascii(text: String): ByteArray = text.toByteArray(Charsets.ISO_8859_1)
    private fun escapePdf(text: String): String = text
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace('\u00A0', ' ')
        .replace(Regex("[^\\x20-\\x7E]"), " ")

    private fun num(value: Float): String = String.format(Locale.US, "%.2f", value)
    private fun fmt(cents: Long): String = "R " + String.format(Locale.US, "%,.2f", cents / 100.0)
    private fun centsFile(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0).replace('.', '_')
}
