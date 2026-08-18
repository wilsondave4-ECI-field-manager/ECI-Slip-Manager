from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Could not find patch target: {label}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# --- Database: allow deletion of incorrect money-received entries ---
db = ROOT / "app/src/main/java/za/co/eci/slipmanager/data/SlipDatabase.kt"
replace_once(
    db,
    '''    fun deleteSlip(id: Long) {
        writableDatabase.delete("slips", "id=?", arrayOf(id.toString()))
    }
''',
    '''    fun deleteSlip(id: Long) {
        writableDatabase.delete("slips", "id=?", arrayOf(id.toString()))
    }

    fun deleteAdvance(id: Long) {
        writableDatabase.delete("advances", "id=?", arrayOf(id.toString()))
    }
''',
    "SlipDatabase.deleteAdvance"
)

repo = ROOT / "app/src/main/java/za/co/eci/slipmanager/data/SlipRepository.kt"
replace_once(
    repo,
    '''    fun saveReturn(item: MoneyReturn): Long = db.upsertReturn(item).also { refresh() }

    fun deleteSlip(item: Slip) {
''',
    '''    fun saveReturn(item: MoneyReturn): Long = db.upsertReturn(item).also { refresh() }

    fun deleteAdvance(item: Advance) {
        db.deleteAdvance(item.id)
        refresh()
    }

    fun deleteSlip(item: Slip) {
''',
    "SlipRepository.deleteAdvance"
)

# --- OCR: safer supplier detection and much stronger SA receipt amount parsing ---
ocr = ROOT / "app/src/main/java/za/co/eci/slipmanager/ocr/ReceiptOcr.kt"
ocr.write_text(r'''package za.co.eci.slipmanager.ocr

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
''', encoding="utf-8")

# --- UI changes ---
ui = ROOT / "app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt"
replace_once(
    ui,
    '''import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
''',
    '''import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
''',
    "LazyRow import"
)
replace_once(
    ui,
    '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
''',
    '''import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
''',
    "LaunchedEffect import"
)
replace_once(
    ui,
    '''                Screen.ADVANCES -> AdvancesScreen(repository, advances, slips, returns)
''',
    '''                Screen.ADVANCES -> AdvancesScreen(repository, advances, slips, returns, onDone = { screen = Screen.HOME })
''',
    "AdvancesScreen navigation"
)

text = ui.read_text(encoding="utf-8")
start = text.index("@Composable\nprivate fun HomeScreen(")
end = text.index("@Composable\nprivate fun MiniTotal", start)
new_home = '''@Composable
private fun HomeScreen(
    repository: SlipRepository,
    advances: List<Advance>,
    slips: List<Slip>,
    onAdvances: () -> Unit,
    onSlip: (Slip) -> Unit,
    onScan: () -> Unit
) {
    var selectedAdvanceId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(advances) {
        val newest = advances.firstOrNull()?.id
        if (selectedAdvanceId == null || advances.none { it.id == selectedAdvanceId }) selectedAdvanceId = newest
    }
    val selectedAdvance = advances.firstOrNull { it.id == selectedAdvanceId }
    val rec = selectedAdvanceId?.let { repository.reconciliation(it) } ?: repository.reconciliation()
    val visibleSlips = if (selectedAdvanceId == null) slips else slips.filter { it.advanceId == selectedAdvanceId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("ADVANCE BALANCE", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    Text(money(rec.outstandingCents), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    selectedAdvance?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${it.project.ifBlank { "Advance #${it.id}" }} • ${dateText(it.dateEpochDay)}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onAdvances, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("MONEY RECEIVED / ADVANCES", fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTotal("Received", rec.receivedCents, Modifier.weight(1f))
                MiniTotal("Slips", rec.slipsCents, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTotal("Returned", rec.returnedCents, Modifier.weight(1f))
                MiniTotal("Slips", visibleSlips.size.toLong(), Modifier.weight(1f), asCount = true)
            }
        }
        item {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(10.dp))
                Text("SCAN SLIP", fontWeight = FontWeight.Bold)
            }
        }
        if (advances.isEmpty()) item { InfoCard("Start here", "Add the money paid into your account, then scan slips against it.") }

        val incomplete = visibleSlips.filterNot { it.isComplete }.take(5)
        if (incomplete.isNotEmpty()) {
            item { SectionTitle("Needs attention") }
            items(incomplete, key = { it.id }) { slip -> SlipCard(slip, onSlip) }
        }

        if (advances.isNotEmpty()) {
            item { SectionTitle("Loaded advances") }
            item {
                AdvanceSelectorRow(
                    advances = advances,
                    selectedId = selectedAdvanceId,
                    onSelected = { selectedAdvanceId = it }
                )
            }
        }

        item { SectionTitle("Recent slips") }
        if (visibleSlips.isEmpty()) {
            item { InfoCard("No slips for this advance", "Scan a slip and it will be allocated to the newest advance by default.") }
        } else {
            items(visibleSlips.take(8), key = { it.id }) { slip -> SlipCard(slip, onSlip) }
        }
    }
}

@Composable
private fun AdvanceSelectorRow(advances: List<Advance>, selectedId: Long?, onSelected: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(advances, key = { it.id }) { advance ->
            val selected = advance.id == selectedId
            val label = advance.project.ifBlank { "Advance #${advance.id}" }
            if (selected) {
                Button(onClick = { onSelected(advance.id) }) {
                    Column {
                        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${dateText(advance.dateEpochDay)} • ${money(advance.amountCents)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                OutlinedButton(onClick = { onSelected(advance.id) }) {
                    Column {
                        Text(label, maxLines = 1)
                        Text("${dateText(advance.dateEpochDay)} • ${money(advance.amountCents)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

'''
text = text[:start] + new_home + text[end:]
ui.write_text(text, encoding="utf-8")

text = ui.read_text(encoding="utf-8")
start = text.index("@Composable\nprivate fun AdvancesScreen(")
end = text.index("@Composable\nprivate fun ReturnMoneyDialog", start)
new_advances = '''@Composable
private fun AdvancesScreen(
    repository: SlipRepository,
    advances: List<Advance>,
    slips: List<Slip>,
    returns: List<MoneyReturn>,
    onDone: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var reference by remember { mutableStateOf("") }
    var project by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var editingAdvance by remember { mutableStateOf<Advance?>(null) }
    var deleteAdvance by remember { mutableStateOf<Advance?>(null) }
    var returnAdvance by remember { mutableStateOf<Advance?>(null) }
    val context = LocalContext.current

    fun clearForm() {
        editingAdvance = null
        amount = ""
        date = LocalDate.now().toString()
        reference = ""
        project = ""
        notes = ""
    }

    LaunchedEffect(editingAdvance?.id) {
        editingAdvance?.let { item ->
            amount = plainMoney(item.amountCents)
            date = LocalDate.ofEpochDay(item.dateEpochDay).toString()
            reference = item.reference
            project = item.project
            notes = item.notes
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionTitle(if (editingAdvance == null) "Add money received" else "Correct money received") }
        item { MoneyField("Amount received", amount, { amount = it }) }
        item { DateField(date, { date = it }) }
        item { TextFieldSimple("Bank / payment reference", reference, { reference = it }) }
        item { TextFieldSimple("Project / site", project, { project = it }) }
        item { TextFieldSimple("Notes", notes, { notes = it }) }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val cents = parseMoney(amount)
                    val epoch = parseDate(date)
                    if (cents == null || cents <= 0 || epoch == null) {
                        Toast.makeText(context, "Enter a valid amount and date.", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.saveAdvance(
                            Advance(
                                id = editingAdvance?.id ?: 0L,
                                dateEpochDay = epoch,
                                amountCents = cents,
                                reference = reference.trim(),
                                project = project.trim(),
                                notes = notes.trim()
                            )
                        )
                        clearForm()
                        onDone()
                    }
                }
            ) { Text(if (editingAdvance == null) "Save money received" else "Save correction") }
        }
        if (editingAdvance != null) {
            item { OutlinedButton(onClick = { clearForm() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel correction") } }
        }
        if (advances.isNotEmpty()) item { SectionTitle("Advances") }
        items(advances, key = { it.id }) { advance ->
            val spent = slips.filter { it.advanceId == advance.id }.sumOf { it.totalCents }
            val returned = returns.filter { it.advanceId == advance.id }.sumOf { it.amountCents }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(advance.project.ifBlank { "Advance #${advance.id}" }, fontWeight = FontWeight.Bold)
                    Text("${dateText(advance.dateEpochDay)} • ${advance.reference.ifBlank { "No reference" }}")
                    Spacer(Modifier.height(6.dp))
                    Text("Received ${money(advance.amountCents)}")
                    Text("Slips ${money(spent)} • Returned ${money(returned)}")
                    Text("Balance ${money(advance.amountCents - spent - returned)}", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { editingAdvance = advance }) { Text("Edit") }
                        TextButton(onClick = { deleteAdvance = advance }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { returnAdvance = advance }) { Text("Record money returned") }
                }
            }
        }
    }

    returnAdvance?.let { advance ->
        ReturnMoneyDialog(advance, onDismiss = { returnAdvance = null }) { item ->
            repository.saveReturn(item)
            returnAdvance = null
        }
    }

    deleteAdvance?.let { advance ->
        AlertDialog(
            onDismissRequest = { deleteAdvance = null },
            title = { Text("Delete money received?") },
            text = {
                Text("This deletes the selected money-received entry. Slips already allocated to it will remain in the app but become unallocated. Any money-return records linked to this advance will also be removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.deleteAdvance(advance)
                    deleteAdvance = null
                    clearForm()
                    onDone()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteAdvance = null }) { Text("Cancel") } }
        )
    }
}

'''
text = text[:start] + new_advances + text[end:]
ui.write_text(text, encoding="utf-8")

replace_once(
    ui,
    '''    var selectedAdvanceId by remember(original.id, original.imagePath) { mutableStateOf(original.advanceId) }
''',
    '''    var selectedAdvanceId by remember(original.id, original.imagePath) {
        mutableStateOf(original.advanceId ?: if (original.id == 0L) advances.firstOrNull()?.id else null)
    }
''',
    "default newest advance for new slip"
)

# Version bump
build = ROOT / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
text = text.replace("versionCode = 1", "versionCode = 2")
text = text.replace('versionName = "0.1.0"', 'versionName = "0.2.0"')
build.write_text(text, encoding="utf-8")

print("ECI Slip Manager v0.2 source changes applied")
