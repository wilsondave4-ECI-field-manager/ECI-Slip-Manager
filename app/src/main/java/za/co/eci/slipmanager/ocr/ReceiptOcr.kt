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

    private val knownSuppliers = listOf(
        "Builders" to Regex("\\bbuilders(?: warehouse| express)?\\b", RegexOption.IGNORE_CASE),
        "Cashbuild" to Regex("\\bcashbuild\\b", RegexOption.IGNORE_CASE),
        "BUCO" to Regex("\\bbuco\\b", RegexOption.IGNORE_CASE),
        "Midas" to Regex("\\bmidas\\b", RegexOption.IGNORE_CASE),
        "AutoZone" to Regex("\\bauto\\s*zone\\b", RegexOption.IGNORE_CASE),
        "Goldwagen" to Regex("\\bgoldwagen\\b", RegexOption.IGNORE_CASE),
        "Adendorff" to Regex("\\badendorff\\b", RegexOption.IGNORE_CASE),
        "Leroy Merlin" to Regex("\\bleroy\\s+merlin\\b", RegexOption.IGNORE_CASE),
        "Makro" to Regex("\\bmakro\\b", RegexOption.IGNORE_CASE),
        "Game" to Regex("\\bgame\\b", RegexOption.IGNORE_CASE),
        "Checkers" to Regex("\\bcheckers\\b", RegexOption.IGNORE_CASE),
        "Pick n Pay" to Regex("\\bpick\\s*(?:n|and|&)\\s*pay\\b|\\bpnp\\b", RegexOption.IGNORE_CASE),
        "Shoprite" to Regex("\\bshoprite\\b", RegexOption.IGNORE_CASE),
        "SPAR" to Regex("\\bspar\\b", RegexOption.IGNORE_CASE),
        "Woolworths" to Regex("\\bwoolworths\\b", RegexOption.IGNORE_CASE),
        "Clicks" to Regex("\\bclicks\\b", RegexOption.IGNORE_CASE),
        "Dis-Chem" to Regex("\\bdis[- ]?chem\\b", RegexOption.IGNORE_CASE),
        "Engen" to Regex("\\bengen\\b", RegexOption.IGNORE_CASE),
        "Shell" to Regex("\\bshell\\b", RegexOption.IGNORE_CASE),
        "BP" to Regex("\\bbp\\b", RegexOption.IGNORE_CASE),
        "Sasol" to Regex("\\bsasol\\b", RegexOption.IGNORE_CASE),
        "TotalEnergies" to Regex("\\btotal\\s*energies\\b", RegexOption.IGNORE_CASE)
    )

    fun recognize(context: Context, uri: Uri, onResult: (Result<ReceiptGuess>) -> Unit) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrElse {
            onResult(Result.failure(it)); return
        }
        recognizer.process(image)
            .addOnSuccessListener { result -> onResult(Result.success(parse(result.text))) }
            .addOnFailureListener { error -> onResult(Result.failure(error)) }
    }

    fun parse(text: String): ReceiptGuess {
        val lines = text.lines().map(::cleanLine).filter { it.isNotBlank() }
        val supplier = findSupplier(lines)
        val date = lines.asSequence().mapNotNull(::parseDateFromLine).firstOrNull()
        val receipt = findReceiptNumber(lines)
        val total = findTotal(lines)
        val vat = findLabeledAmount(
            lines,
            listOf("vat amount", "vat amt", "tax amount", "vat", "tax"),
            reject = listOf("vat no", "vat number", "vat reg", "registration"),
            preferLast = true
        )
        var subtotal = findLabeledAmount(
            lines,
            listOf("subtotal", "sub total", "total excl", "excl vat", "exclusive vat", "net amount", "amount excl"),
            reject = emptyList(),
            preferLast = false
        )
        if (subtotal == null && total != null && vat != null && total >= vat) subtotal = total - vat

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

    private fun cleanLine(value: String): String = value
        .replace('’', '\'')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun findSupplier(lines: List<String>): String {
        val joinedTop = lines.take(16).joinToString(" ")
        knownSuppliers.firstOrNull { (_, pattern) -> pattern.containsMatchIn(joinedTop) }?.let { return it.first }

        val rejects = listOf(
            "tax invoice", "invoice", "receipt", "till", "cashier", "vat", "reg no", "registration",
            "telephone", "tel:", "www.", "http", "thank you", "customer", "date", "time", "branch",
            "transaction", "duplicate", "copy", "subtotal", "total", "amount due", "change", "cash",
            "street", " road", " rd", " avenue", " ave", " corner", "cnr", "po box"
        )

        return lines.take(12).mapIndexedNotNull { index, raw ->
            val line = raw.trim(' ', '-', '_', '*', ':', '.', ',')
            val lower = line.lowercase(Locale.ROOT)
            val letters = line.count(Char::isLetter)
            val digits = line.count(Char::isDigit)
            if (line.length !in 3..60 || letters < 3 || digits > 4 || rejects.any(lower::contains)) return@mapIndexedNotNull null
            if (line.contains('@') || line.count { it == '/' } > 1) return@mapIndexedNotNull null
            val upperLetters = line.count { it.isLetter() && it.isUpperCase() }
            val upperRatio = if (letters == 0) 0.0 else upperLetters.toDouble() / letters
            var score = 12.0 - index * 0.7
            if (upperRatio > 0.65) score += 2.5
            if (Regex("\\b(pty|ltd|cc|trading|hardware|motors|engineering|supplies|electrical)\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)) score += 2.0
            if (line.length in 4..35) score += 1.0
            line to score
        }.maxByOrNull { it.second }?.takeIf { it.second >= 6.0 }?.first.orEmpty().take(80)
    }

    private fun findReceiptNumber(lines: List<String>): String {
        val labels = listOf("invoice no", "invoice #", "receipt no", "receipt #", "slip no", "document no", "trans no", "transaction no", "reference", "ref")
        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase(Locale.ROOT)
            if (labels.none(lower::contains)) continue
            val m = Regex("(?:no\\.?|number|#|ref\\.?|invoice|receipt|slip|document|trans(?:action)?)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9/-]{2,})", RegexOption.IGNORE_CASE).find(line)
            if (m != null) return m.groupValues[1].take(40)
            if (i + 1 < lines.size) {
                val next = Regex("^[A-Z0-9][A-Z0-9/-]{2,}$", RegexOption.IGNORE_CASE).find(lines[i + 1])
                if (next != null) return next.value.take(40)
            }
        }
        return ""
    }

    private fun findTotal(lines: List<String>): Long? {
        val labels = listOf("grand total", "amount due", "balance due", "total due", "invoice total", "nett total", "net total", "total")
        val reject = listOf("subtotal", "sub total", "total savings", "total discount", "total items", "vat total", "total vat")
        for (label in labels) {
            for (i in lines.indices) {
                val lower = lines[i].lowercase(Locale.ROOT)
                if (!lower.contains(label) || reject.any(lower::contains)) continue
                amountAfterLabel(lines, i, label, preferLast = false)?.let { if (it > 0) return it }
            }
        }

        val start = (lines.size / 3).coerceAtLeast(0)
        val fallbackReject = listOf("change", "cash", "tender", "card", "eft", "saving", "discount", "%", "qty", "quantity")
        return lines.drop(start)
            .filterNot { line -> fallbackReject.any(line.lowercase(Locale.ROOT)::contains) }
            .flatMap(::parseAmounts)
            .filter { it in 1..100_000_000L }
            .maxOrNull()
    }

    private fun findLabeledAmount(
        lines: List<String>,
        labels: List<String>,
        reject: List<String>,
        preferLast: Boolean
    ): Long? {
        for (label in labels) {
            for (i in lines.indices) {
                val lower = lines[i].lowercase(Locale.ROOT)
                if (!lower.contains(label) || reject.any(lower::contains)) continue
                amountAfterLabel(lines, i, label, preferLast)?.let { if (it >= 0) return it }
            }
        }
        return null
    }

    private fun amountAfterLabel(lines: List<String>, index: Int, label: String, preferLast: Boolean): Long? {
        val line = lines[index]
        val lower = line.lowercase(Locale.ROOT)
        val pos = lower.indexOf(label)
        if (pos >= 0) {
            val tail = line.substring((pos + label.length).coerceAtMost(line.length))
            val same = parseAmounts(tail)
            if (same.isNotEmpty()) return if (preferLast) same.last() else same.first()
        }
        for (offset in 1..2) {
            val next = lines.getOrNull(index + offset) ?: break
            val values = parseAmounts(next)
            if (values.isNotEmpty()) return if (preferLast) values.last() else values.first()
        }
        return null
    }

    private fun parseAmounts(source: String): List<Long> {
        val line = source
            .replace(Regex("(?<=\\d)[Oo](?=\\d|[.,])"), "0")
            .replace(Regex("(?<=[.,])[Oo](?=\\d)"), "0")
            .replace(Regex("(?<=\\d)\\s*([.,])\\s*(?=\\d{2}\\b)"), "$1")

        val regex = Regex("(?:ZAR\\s*|R\\s*)?(-?\\d{1,3}(?:[ '\\u00A0,.]\\d{3})*(?:[.,]\\d{2})|-?\\d+(?:[.,]\\d{2}))", RegexOption.IGNORE_CASE)
        return regex.findAll(line).mapNotNull { match ->
            val after = line.substring((match.range.last + 1).coerceAtMost(line.length)).trimStart()
            if (after.startsWith("%")) return@mapNotNull null
            val raw = match.groupValues[1].replace(" ", "").replace("'", "").replace("\u00A0", "")
            val normalized = when {
                raw.contains(',') && raw.contains('.') -> {
                    if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) raw.replace(".", "").replace(',', '.')
                    else raw.replace(",", "")
                }
                raw.count { it == ',' } == 1 && raw.substringAfterLast(',').length == 2 -> raw.replace(',', '.')
                else -> raw.replace(",", "")
            }
            normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
        }.filter { it in -100_000_000L..100_000_000L }.toList()
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
