package za.co.eci.slipmanager.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * Converts the scanner/camera JPEG into the monochrome document image stored by
 * ECI Slip Manager. The document scanner performs edge detection, crop and
 * perspective correction first; this pass removes colour and pushes the paper
 * background toward white while retaining faded receipt text.
 */
object ReceiptScanProcessor {
    private const val MAX_LONG_EDGE = 3000
    private const val JPEG_QUALITY = 82
    private const val BLACK_POINT = 55
    private const val WHITE_POINT = 220

    fun saveBlackWhite(context: Context, sourceUri: Uri, targetFile: File): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource(context, sourceUri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not read scanned receipt image" }

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_LONG_EDGE * 2) {
            sample *= 2
        }

        val source = openSource(context, sourceUri).use { input ->
            BitmapFactory.decodeStream(
                input,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        } ?: error("Could not decode scanned receipt image")

        val longEdge = max(source.width, source.height)
        val scale = if (longEdge > MAX_LONG_EDGE) MAX_LONG_EDGE.toFloat() / longEdge.toFloat() else 1f
        val working = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { source.recycle() }
        } else {
            source
        }

        val pixels = IntArray(working.width * working.height)
        working.getPixels(pixels, 0, working.width, 0, 0, working.width, working.height)

        val range = (WHITE_POINT - BLACK_POINT).coerceAtLeast(1)
        for (i in pixels.indices) {
            val color = pixels[i]
            val gray = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            val level = (((gray - BLACK_POINT) * 255) / range).coerceIn(0, 255)
            val cleaned = when {
                level >= 238 -> 255
                level <= 22 -> 0
                else -> level
            }
            pixels[i] = Color.rgb(cleaned, cleaned, cleaned)
        }

        val output = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.RGB_565)
        output.setPixels(pixels, 0, working.width, 0, 0, working.width, working.height)
        working.recycle()

        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) { "Could not save cleaned receipt image" }
        }
        output.recycle()
        return targetFile
    }

    private fun openSource(context: Context, uri: Uri): InputStream {
        return if (uri.scheme.equals("file", ignoreCase = true)) {
            FileInputStream(File(requireNotNull(uri.path)))
        } else {
            context.contentResolver.openInputStream(uri) ?: error("Could not open scanned receipt image")
        }
    }
}
