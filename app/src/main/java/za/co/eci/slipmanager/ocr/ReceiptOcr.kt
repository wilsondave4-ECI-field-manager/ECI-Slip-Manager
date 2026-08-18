package za.co.eci.slipmanager.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale


data class ReceiptGuess(
    val supplier: String = "",
    val dateEpochDay: Long? = null,
    val receiptNumber: String = "",
    val subtotalCents: Long? = null,
    val vatCents: Long? = null,
    val totalCents: Long? = null,
    val rawText: String = ""
)

object ReceiptOcr {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun recognize(context: Context, uri: Uri, onResult: (Result<ReceiptGuess>) -> Unit) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            onResult(Result.failure(it)); return
        }
        recognizer.process(image)
            .addOnSuccessListener { result -> onResult(Result.success(parse(result.text))) }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
    }

    fun parse(text: String): ReceiptGuess {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val supplier = lines.firstOrNull { line ->
            line.any(Char::isLetter) && !line.matches(Regex(".*\\d{4,}.*"))
        }.orEmpty().take(80)

        val date = lines.asSequence().mapNotNull(::parseDateFromLine).firstOrNull()
        val receipt = findReceiptNumber(lines)
        val total = findAmount(lines, listOf("grand total", "amount due", "total due", "total"), preferLast = true)
        val vat = findAmount(lines, listOf("vat", "tax"), preferLast = true)
        val subtotal = findAmount(lines, listOf("subtotal", "sub total", "excl", "exclusive"), preferLast = true)

        return ReceiptGuess(
            supplier = supplier,
            dateEpochDay = date?.toEpochDay(),
            receiptNumber = receipt,
            subtotalCents = subtotal,
            vatCents = vat,
            totalCents = total,
            rawText = text
        )
    }

    private fun findReceiptNumber(lines: List<String>): String {
        val keywords = listOf("invoice", "receipt", "slip", "tax invoice", "ref")
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (keywords.any(lower::contains)) {
                val m = Regex("(?:no\\.?|number|#|ref\\.?|invoice|receipt)\\s*[:#-]?\\s*([A-Z0-9/-]{3,})", RegexOption.IGNORE_CASE).find(line)
                if (m != null) return m.groupValues[1].take(40)
            }
        }
        return ""
    }

    private fun findAmount(lines: List<String>, keywords: List<String>, preferLast: Boolean): Long? {
        val matches = mutableListOf<Long>()
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (keywords.any(lower::contains)) {
                parseAmounts(line).forEach(matches::add)
            }
        }
        return if (matches.isEmpty()) null else if (preferLast) matches.last() else matches.first()
    }

    private fun parseAmounts(line: String): List<Long> {
        val regex = Regex("(?:R\\s*)?(-?\\d{1,3}(?:[ ,.]\\d{3})*(?:[.,]\\d{2})|-?\\d+[.,]\\d{2})")
        return regex.findAll(line).mapNotNull { match ->
            val raw = match.groupValues[1].replace(" ", "")
            val normalized = when {
                raw.count { it == ',' } == 1 && raw.substringAfterLast(',').length == 2 -> raw.replace(".", "").replace(',', '.')
                else -> raw.replace(",", "")
            }
            normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
        }.toList()
    }

    private fun parseDateFromLine(line: String): LocalDate? {
        val patterns = listOf(
            Regex("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b"),
            Regex("\\b(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})\\b")
        )
        patterns[0].find(line)?.let { m ->
            val d = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            var y = m.groupValues[3].toIntOrNull() ?: return@let
            if (y < 100) y += 2000
            return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
        }
        patterns[1].find(line)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@let
            val mo = m.groupValues[2].toIntOrNull() ?: return@let
            val d = m.groupValues[3].toIntOrNull() ?: return@let
            return runCatching { LocalDate.of(y, mo, d) }.getOrNull()
        }
        val words = Regex("\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b").find(line)
        if (words != null) {
            val candidates = listOf("d MMM yyyy", "d MMMM yyyy")
            for (pattern in candidates) {
                try {
                    return LocalDate.parse(words.value, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH))
                } catch (_: DateTimeParseException) { }
            }
        }
        return null
    }
}
