package za.co.eci.slipmanager.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.UUID

/** Keep the original PDF in private offline storage; render only temporary previews. */
object ReceiptDocument {
    fun isPdf(path: String) = path.endsWith(".pdf", ignoreCase = true)

    fun importPdf(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "receipt_${UUID.randomUUID()}.pdf")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open this document" }
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 12 * 1024 * 1024) { "Choose a PDF up to 12 MB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            withRenderer(file) { require(it.pageCount > 0) { "PDF has no pages" } }
            return file
        } catch (error: Exception) {
            file.delete()
            throw IllegalArgumentException("Cannot read PDF. Use an unlocked PDF up to 12 MB. ${error.message}", error)
        }
    }

    private fun <T> withRenderer(file: File, action: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(action)
        }

    private fun render(renderer: PdfRenderer, index: Int): Bitmap = renderer.openPage(index).use { page ->
        val scale = minOf(2f, 1800f / maxOf(page.width, page.height))
        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    fun preview(path: String): Bitmap = withRenderer(File(path)) { render(it, 0) }

    fun forEachPage(path: String, action: (Bitmap) -> Unit) = withRenderer(File(path)) { renderer ->
        for (index in 0 until renderer.pageCount) {
            val bitmap = render(renderer, index)
            try { action(bitmap) } finally { bitmap.recycle() }
        }
    }
}
