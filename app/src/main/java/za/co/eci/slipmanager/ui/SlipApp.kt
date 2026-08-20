package za.co.eci.slipmanager.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.android.gms.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.android.gms.mlkit.vision.documentscanner.GmsDocumentScanningResult
import za.co.eci.slipmanager.backup.BackupManager
import za.co.eci.slipmanager.data.Advance
import za.co.eci.slipmanager.data.MoneyReturn
import za.co.eci.slipmanager.data.PaymentType
import za.co.eci.slipmanager.data.Reimbursement
import za.co.eci.slipmanager.data.Slip
import za.co.eci.slipmanager.data.SlipRepository
import za.co.eci.slipmanager.ocr.ReceiptGuess
import za.co.eci.slipmanager.ocr.ReceiptOcr
import za.co.eci.slipmanager.ocr.ReceiptScanProcessor
import za.co.eci.slipmanager.pdf.PdfExporter
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class Screen { HOME, ADVANCES, SLIPS, REPORTS, ARCHIVE, SETTINGS, EDIT_SLIP }

@Composable
fun SlipApp(repository: SlipRepository) {
    val context = LocalContext.current
    val activity = context as Activity
    val advances by repository.advances.collectAsState()
    val slips by repository.slips.collectAsState()
    val returns by repository.returns.collectAsState()
    val reimbursements by repository.reimbursements.collectAsState()

    var screen by remember { mutableStateOf(Screen.HOME) }
    var editingSlip by remember { mutableStateOf<Slip?>(null) }
    var pendingImageFile by remember { mutableStateOf<File?>(null) }
    var processingOcr by remember { mutableStateOf(false) }
    var lastBackMillis by remember { mutableLongStateOf(0L) }
    val crashFile = remember { File(context.filesDir, "last_crash.txt") }
    var showPreviousCrash by remember { mutableStateOf(crashFile.exists()) }

    fun makeReceiptFile(): File {
        val dir = File(context.filesDir, "receipts").apply { mkdirs() }
        return File(dir, "receipt_${System.currentTimeMillis()}.jpg")
    }

    fun openReceiptReview(file: File) {
        processingOcr = true
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        ReceiptOcr.recognize(context, uri) { result ->
            processingOcr = false
            val guess = result.getOrElse {
                Toast.makeText(context, "OCR could not read the slip. Enter the details manually.", Toast.LENGTH_LONG).show()
                ReceiptGuess()
            }
            editingSlip = Slip(
                supplier = guess.supplier,
                dateEpochDay = guess.dateEpochDay,
                receiptNumber = guess.receiptNumber,
                subtotalCents = guess.subtotalCents,
                vatCents = guess.vatCents,
                totalCents = guess.totalCents ?: 0L,
                imagePath = file.absolutePath,
                ocrText = guess.rawText
            )
            screen = Screen.EDIT_SLIP
        }
    }

    fun prepareBlackWhiteReceipt(sourceUri: Uri, originalCameraFile: File? = null) {
        processingOcr = true
        val cleanedFile = makeReceiptFile()
        Thread {
            val prepared = runCatching {
                ReceiptScanProcessor.saveBlackWhite(context, sourceUri, cleanedFile)
            }
            activity.runOnUiThread {
                prepared.onSuccess { file ->
                    if (originalCameraFile != null && originalCameraFile.absolutePath != file.absolutePath) {
                        originalCameraFile.delete()
                    }
                    openReceiptReview(file)
                }.onFailure { error ->
                    processingOcr = false
                    cleanedFile.delete()
                    originalCameraFile?.delete()
                    Toast.makeText(context, "Could not clean the receipt scan: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingImageFile
        pendingImageFile = null
        if (!success || file == null) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        prepareBlackWhiteReceipt(uri, originalCameraFile = file)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val file = makeReceiptFile()
            pendingImageFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission is needed for the fallback camera.", Toast.LENGTH_LONG).show()
        }
    }

    fun startFallbackCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val file = makeReceiptFile()
            pendingImageFile = file
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val documentScanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val documentScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
            val imageUri = scanResult?.pages?.firstOrNull()?.imageUri
            if (imageUri != null) {
                prepareBlackWhiteReceipt(imageUri)
            } else {
                Toast.makeText(context, "The scanner did not return a receipt image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startScan() {
        documentScanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Toast.makeText(context, "Document scanner unavailable. Opening the normal camera instead.", Toast.LENGTH_LONG).show()
                startFallbackCamera()
            }
    }

    BackHandler {
        if (screen != Screen.HOME) {
            editingSlip = null
            screen = Screen.HOME
        } else {
            val now = System.currentTimeMillis()
            if (now - lastBackMillis < 1800) {
                activity.finish()
            } else {
                lastBackMillis = now
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            AppTopBar(
                title = when (screen) {
                    Screen.HOME -> "ECI Slip Manager"
                    Screen.ADVANCES -> "Money Received"
                    Screen.SLIPS -> "Slips"
                    Screen.REPORTS -> "Reports & Export"
                    Screen.ARCHIVE -> "Archive"
                    Screen.SETTINGS -> "Settings"
                    Screen.EDIT_SLIP -> if ((editingSlip?.id ?: 0L) == 0L) "Review Slip" else "Edit Slip"
                },
                showSettings = screen == Screen.HOME,
                onSettings = { screen = Screen.SETTINGS }
            )
        },
        bottomBar = {
            if (screen in setOf(Screen.HOME, Screen.SLIPS, Screen.REPORTS, Screen.ARCHIVE)) {
                BottomNav(screen, onNavigate = { screen = it }, onScan = ::startScan)
            }
        },
        floatingActionButton = {
            if (screen == Screen.HOME) {
                FloatingActionButton(onClick = ::startScan) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan slip")
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    repository = repository,
                    advances = advances,
                    slips = slips,
                    onAdvances = { screen = Screen.ADVANCES },
                    onSlip = { editingSlip = it; screen = Screen.EDIT_SLIP },
                    onScan = ::startScan
                )
                Screen.ADVANCES -> AdvancesScreen(repository, advances, slips, returns, onDone = { screen = Screen.HOME })
                Screen.SLIPS -> SlipsScreen(
                    slips = slips,
                    onOpen = { editingSlip = it; screen = Screen.EDIT_SLIP }
                )
                Screen.REPORTS -> ReportsScreen(repository, advances, slips, returns, reimbursements)
                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns)
                Screen.SETTINGS -> SettingsScreen(repository, advances, slips, returns, reimbursements)
                Screen.EDIT_SLIP -> editingSlip?.let { original ->
                    EditSlipScreen(
                        original = original,
                        advances = advances.filter { !it.archived || repository.reconciliation(it.id).outstandingCents != 0L },
                        onSave = {
                            repository.saveSlip(it)
                            editingSlip = null
                            screen = Screen.SLIPS
                        },
                        onDelete = if (original.id > 0) {
                            {
                                repository.deleteSlip(original)
                                editingSlip = null
                                screen = Screen.SLIPS
                            }
                        } else null,
                        onCancel = {
                            if (original.id == 0L) File(original.imagePath).delete()
                            editingSlip = null
                            screen = Screen.HOME
                        }
                    )
                }
            }
            if (processingOcr) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Reading slip…")
                    }
                }
            }
        }
    }

    if (showPreviousCrash && crashFile.exists()) {
        AlertDialog(
            onDismissRequest = { showPreviousCrash = false },
            title = { Text("Previous crash detected") },
            text = { Text("ECI Slip Manager saved the Android crash details. Share the log so the exact cause can be fixed, or dismiss it to continue using the app.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { shareFile(context, crashFile, "text/plain", "ECI Slip Manager Crash Log") }
                        .onFailure { Toast.makeText(context, "Could not share crash log: ${it.message}", Toast.LENGTH_LONG).show() }
                }) { Text("Share crash log") }
            },
            dismissButton = {
                TextButton(onClick = {
                    crashFile.delete()
                    showPreviousCrash = false
                }) { Text("Dismiss") }
            }
        )
    }

}

@Composable
private fun AppTopBar(title: String, showSettings: Boolean, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (title == "ECI Slip Manager") Text("Local • Private • Offline", style = MaterialTheme.typography.labelSmall)
        }
        if (showSettings) IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
    }
}

@Composable
private fun BottomNav(screen: Screen, onNavigate: (Screen) -> Unit, onScan: () -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = screen == Screen.HOME,
            onClick = { onNavigate(Screen.HOME) },
            icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onScan,
            icon = { Icon(Icons.Default.CameraAlt, null) }, label = { Text("Scan") }
        )
        NavigationBarItem(
            selected = screen == Screen.SLIPS,
            onClick = { onNavigate(Screen.SLIPS) },
            icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Slips") }
        )
        NavigationBarItem(
            selected = screen == Screen.REPORTS,
            onClick = { onNavigate(Screen.REPORTS) },
            icon = { Icon(Icons.Default.Description, null) }, label = { Text("Reports") }
        )
        NavigationBarItem(
            selected = screen == Screen.ARCHIVE,
            onClick = { onNavigate(Screen.ARCHIVE) },
            icon = { Icon(Icons.Default.Archive, null) }, label = { Text("Archive") }
        )
    }
}

@Composable
private fun HomeScreen(
    repository: SlipRepository,
    advances: List<Advance>,
    slips: List<Slip>,
    onAdvances: () -> Unit,
    onSlip: (Slip) -> Unit,
    onScan: () -> Unit
) {
    val dashboardAdvances = advances.filter { advance ->
        !advance.archived || repository.reconciliation(advance.id).outstandingCents != 0L
    }
    var selectedAdvanceId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(dashboardAdvances) {
        val newest = dashboardAdvances.firstOrNull { !it.archived }?.id ?: dashboardAdvances.firstOrNull()?.id
        if (selectedAdvanceId == null || dashboardAdvances.none { it.id == selectedAdvanceId }) selectedAdvanceId = newest
    }
    val selectedAdvance = dashboardAdvances.firstOrNull { it.id == selectedAdvanceId }
    val rec = selectedAdvanceId?.let { repository.reconciliation(it) } ?: repository.activeReconciliation()
    val activeRec = repository.activeReconciliation()
    val personal = repository.personalFundsSummary()
    var showReimbursement by remember { mutableStateOf(false) }
    val activeAdvanceIds = advances.asSequence().filterNot { it.archived }.map { it.id }.toSet()
    val visibleSlips = when {
        selectedAdvanceId == null -> slips.filter { it.advanceId != null && it.advanceId in activeAdvanceIds }
        selectedAdvance?.archived == true -> emptyList()
        else -> slips.filter { it.advanceId == selectedAdvanceId }
    }

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
                            "${it.project.ifBlank { "Advance #${it.id}" }} • ${dateText(it.dateEpochDay)}${if (it.archived) " • ARCHIVED" else ""}",
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
                MiniTotal("Received", activeRec.receivedCents, Modifier.weight(1f))
                MiniTotal("Slips", activeRec.slipsCents, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniTotal("Returned", activeRec.returnedCents, Modifier.weight(1f))
                MiniTotal("Slips", visibleSlips.size.toLong(), Modifier.weight(1f), asCount = true)
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("MY OWN MONEY", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Used ${money(personal.usedCents)}")
                    Text("Reimbursed ${money(personal.reimbursedCents)}")
                    Text("ECI owes me ${money(personal.outstandingCents)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (personal.outstandingCents > 0L) {
                        OutlinedButton(onClick = { showReimbursement = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("RECORD REIMBURSEMENT")
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(10.dp))
                Text("SCAN SLIP", fontWeight = FontWeight.Bold)
            }
        }
        if (dashboardAdvances.isEmpty()) item { InfoCard("Start here", "Add the money paid into your account, then scan slips against it.") }

        val incomplete = visibleSlips.filterNot { it.isComplete }.take(5)
        if (incomplete.isNotEmpty()) {
            item { SectionTitle("Needs attention") }
            items(incomplete, key = { it.id }) { slip -> SlipCard(slip, onSlip) }
        }

        if (dashboardAdvances.isNotEmpty()) {
            item { SectionTitle("Loaded advances") }
            item {
                AdvanceSelectorRow(
                    advances = dashboardAdvances,
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

    if (showReimbursement) {
        ReimbursementDialog(
            maxOutstandingCents = personal.outstandingCents,
            onDismiss = { showReimbursement = false }
        ) { item ->
            repository.saveReimbursement(item)
            showReimbursement = false
        }
    }
}

@Composable
private fun AdvanceSelectorRow(advances: List<Advance>, selectedId: Long?, onSelected: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(advances, key = { it.id }) { advance ->
            val selected = advance.id == selectedId
            val label = advance.project.ifBlank { "Advance #${advance.id}" }
            val archivedMark = if (advance.archived) " • Archived" else ""
            if (selected) {
                Button(onClick = { onSelected(advance.id) }) {
                    Column {
                        Text(label, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${dateText(advance.dateEpochDay)} • ${money(advance.amountCents)}$archivedMark", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                OutlinedButton(onClick = { onSelected(advance.id) }) {
                    Column {
                        Text(label, maxLines = 1)
                        Text("${dateText(advance.dateEpochDay)} • ${money(advance.amountCents)}$archivedMark", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTotal(label: String, centsOrCount: Long, modifier: Modifier = Modifier, asCount: Boolean = false) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(if (asCount) centsOrCount.toString() else money(centsOrCount), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card { Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(body) } }
}

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

@Composable
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
    var archiveAdvance by remember { mutableStateOf<Advance?>(null) }
    var returnAdvance by remember { mutableStateOf<Advance?>(null) }
    val activeAdvances = advances.filterNot { it.archived }
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
                                notes = notes.trim(),
                                archived = editingAdvance?.archived ?: false,
                                archivedAtMillis = editingAdvance?.archivedAtMillis
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
        if (activeAdvances.isNotEmpty()) item { SectionTitle("Advances") }
        items(activeAdvances, key = { it.id }) { advance ->
            val spent = slips.filter { it.advanceId == advance.id }.sumOf { it.companyPaidCents }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { returnAdvance = advance }) { Text("Return money") }
                        TextButton(onClick = { archiveAdvance = advance }) { Text("Archive") }
                    }
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

    archiveAdvance?.let { advance ->
        val balance = advance.amountCents - slips.filter { it.advanceId == advance.id }.sumOf { it.companyPaidCents } - returns.filter { it.advanceId == advance.id }.sumOf { it.amountCents }
        AlertDialog(
            onDismissRequest = { archiveAdvance = null },
            title = { Text("Archive this advance?") },
            text = {
                Text(
                    if (balance == 0L)
                        "This advance is settled. It will move out of the active dashboard and remain available in Archive."
                    else
                        "This advance still has ${money(balance)} outstanding. It will be archived but will remain visible on Home until the balance reaches R0.00."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    repository.archiveAdvance(advance, true)
                    archiveAdvance = null
                    onDone()
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { archiveAdvance = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReimbursementDialog(
    maxOutstandingCents: Long,
    onDismiss: () -> Unit,
    onSave: (Reimbursement) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var reference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record reimbursement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Record money ECI paid back to you for purchases made with your own money.")
                MoneyField("Amount reimbursed", amount, { amount = it })
                DateField(date, { date = it })
                TextFieldSimple("Reference", reference, { reference = it })
                TextFieldSimple("Notes", notes, { notes = it })
                Text("Currently owed: ${money(maxOutstandingCents)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseMoney(amount)
                val epoch = parseDate(date)
                when {
                    cents == null || cents <= 0 || epoch == null -> Toast.makeText(context, "Enter a valid amount and date.", Toast.LENGTH_SHORT).show()
                    maxOutstandingCents > 0L && cents > maxOutstandingCents -> Toast.makeText(context, "That is more than the amount currently owed to you.", Toast.LENGTH_LONG).show()
                    else -> onSave(Reimbursement(dateEpochDay = epoch, amountCents = cents, reference = reference.trim(), notes = notes.trim()))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ReturnMoneyDialog(advance: Advance, onDismiss: () -> Unit, onSave: (MoneyReturn) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Money returned") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Amount returned", amount, { amount = it })
                DateField(date, { date = it })
                TextFieldSimple("Reason for money returned", notes, { notes = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseMoney(amount); val epoch = parseDate(date)
                if (cents == null || cents <= 0 || epoch == null) Toast.makeText(context, "Enter a valid amount and date.", Toast.LENGTH_SHORT).show()
                else onSave(MoneyReturn(advanceId = advance.id, dateEpochDay = epoch, amountCents = cents, notes = notes.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun ArchiveScreen(
    repository: SlipRepository,
    advances: List<Advance>,
    slips: List<Slip>,
    returns: List<MoneyReturn>
) {
    val archivedAdvances = advances.filter { it.archived }.sortedWith(
        compareByDescending<Advance> { it.archivedAtMillis ?: 0L }.thenByDescending { it.dateEpochDay }
    )
    var returnAdvance by remember { mutableStateOf<Advance?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            InfoCard(
                "Archived advances",
                "Settled advances are removed from Home. Archived advances with an outstanding balance remain on Home until they reach R0.00."
            )
        }
        if (archivedAdvances.isEmpty()) {
            item { InfoCard("Archive is empty", "Archive an advance after its report has been sent.") }
        } else {
            items(archivedAdvances, key = { it.id }) { advance ->
                val spent = slips.filter { it.advanceId == advance.id }.sumOf { it.companyPaidCents }
                val returned = returns.filter { it.advanceId == advance.id }.sumOf { it.amountCents }
                val balance = advance.amountCents - spent - returned
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(advance.project.ifBlank { "Advance #${advance.id}" }, fontWeight = FontWeight.Bold)
                        Text("${dateText(advance.dateEpochDay)} • ${advance.reference.ifBlank { "No reference" }}")
                        Spacer(Modifier.height(6.dp))
                        Text("Received ${money(advance.amountCents)}")
                        Text("Slips ${money(spent)} • Returned ${money(returned)}")
                        Text(
                            if (balance == 0L) "SETTLED • R0.00" else "OUTSTANDING • ${money(balance)}",
                            fontWeight = FontWeight.Bold,
                            color = if (balance == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { returnAdvance = advance }) { Text("Return money") }
                            TextButton(onClick = { repository.archiveAdvance(advance, false) }) { Text("Restore active") }
                        }
                    }
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
}

@Composable
private fun SlipsScreen(slips: List<Slip>, onOpen: (Slip) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = slips.filter {
        search.isBlank() || listOf(it.supplier, it.purpose, it.project, it.receiptNumber).any { field -> field.contains(search, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Search slips") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            InfoCard("No slips", if (search.isBlank()) "Scan your first receipt from the Scan button." else "No slips match your search.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { SlipCard(it, onOpen) }
            }
        }
    }
}

@Composable
private fun SlipCard(slip: Slip, onOpen: (Slip) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onOpen(slip) }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(slip.supplier.ifBlank { "Supplier missing" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${slip.dateEpochDay?.let(::dateText) ?: "Date missing"} • ${slip.purpose.ifBlank { "Purpose missing" }}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (slip.isComplete) "Complete" else "Missing information", color = if (slip.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            Text(money(slip.totalCents), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditSlipScreen(
    original: Slip,
    advances: List<Advance>,
    onSave: (Slip) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var supplier by remember(original.id, original.imagePath) { mutableStateOf(original.supplier) }
    var date by remember(original.id, original.imagePath) { mutableStateOf(original.dateEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var receiptNo by remember(original.id, original.imagePath) { mutableStateOf(original.receiptNumber) }
    var subtotal by remember(original.id, original.imagePath) { mutableStateOf(original.subtotalCents?.let(::plainMoney) ?: "") }
    var vat by remember(original.id, original.imagePath) { mutableStateOf(original.vatCents?.let(::plainMoney) ?: "") }
    var total by remember(original.id, original.imagePath) { mutableStateOf(if (original.totalCents > 0) plainMoney(original.totalCents) else "") }
    var purpose by remember(original.id, original.imagePath) { mutableStateOf(original.purpose) }
    var project by remember(original.id, original.imagePath) { mutableStateOf(original.project) }
    var paymentRef by remember(original.id, original.imagePath) { mutableStateOf(original.paymentReference) }
    var paymentType by remember(original.id, original.imagePath) { mutableStateOf(original.paymentType) }
    var ownMoney by remember(original.id, original.imagePath) { mutableStateOf(if (original.ownMoneyCents > 0) plainMoney(original.ownMoneyCents) else "") }
    var selectedAdvanceId by remember(original.id, original.imagePath) {
        mutableStateOf(original.advanceId ?: if (original.id == 0L) advances.firstOrNull { !it.archived }?.id else null)
    }
    var deleteConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val bitmap = remember(original.imagePath) { BitmapFactory.decodeFile(original.imagePath) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Receipt scan",
                modifier = Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Fit
            )
        }
        if (original.ocrText.isBlank()) InfoCard("Manual details", "The receipt could not be read automatically. Enter the missing information below.")
        TextFieldSimple("Supplier *", supplier, { supplier = it }, error = supplier.isBlank())
        DateField(date, { date = it }, error = date.isBlank())
        TextFieldSimple("Receipt / invoice number", receiptNo, { receiptNo = it })
        MoneyField("Subtotal / excl. VAT", subtotal, { subtotal = it })
        MoneyField("VAT (confirm from receipt)", vat, { vat = it })
        MoneyField("Total *", total, { total = it }, error = total.isBlank())
        TextFieldSimple("Purpose / what was purchased for *", purpose, { purpose = it }, error = purpose.isBlank())
        TextFieldSimple("Project / site", project, { project = it })
        TextFieldSimple("Payment reference", paymentRef, { paymentRef = it })
        PaymentTypePicker(paymentType) { selected ->
            paymentType = selected
            if (selected == PaymentType.OWN) selectedAdvanceId = null
        }
        if (paymentType != PaymentType.OWN) {
            AdvancePicker(advances, selectedAdvanceId, { selectedAdvanceId = it })
        }
        if (paymentType == PaymentType.SPLIT) {
            MoneyField("My own money portion", ownMoney, { ownMoney = it })
            val previewTotal = parseMoney(total)
            val previewOwn = parseMoney(ownMoney)
            if (previewTotal != null && previewOwn != null && previewOwn in 1 until previewTotal) {
                Text("Company advance portion: ${money(previewTotal - previewOwn)}", style = MaterialTheme.typography.bodySmall)
            }
        } else if (paymentType == PaymentType.OWN) {
            Text("The full receipt total will be recorded as your own money and will not reduce an advance.", style = MaterialTheme.typography.bodySmall)
        }
        Text("VAT is read from the slip when printed. If it is not printed, the app may calculate the South African 15% VAT portion when the receipt supports it. The VAT field stays editable; enter 0.00 for a zero-rated/no-VAT purchase.", style = MaterialTheme.typography.bodySmall)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val totalCents = parseMoney(total)
                val epoch = parseDate(date)
                val ownCents = when (paymentType) {
                    PaymentType.ADVANCE -> 0L
                    PaymentType.OWN -> totalCents ?: 0L
                    PaymentType.SPLIT -> parseMoney(ownMoney) ?: -1L
                }
                if (totalCents == null || totalCents <= 0) {
                    Toast.makeText(context, "Total is required for reconciliation.", Toast.LENGTH_SHORT).show()
                } else if (paymentType == PaymentType.SPLIT && (ownCents <= 0L || ownCents >= totalCents)) {
                    Toast.makeText(context, "Enter the part of the receipt that you paid yourself.", Toast.LENGTH_LONG).show()
                } else if (paymentType != PaymentType.OWN && selectedAdvanceId == null) {
                    Toast.makeText(context, "Select the advance that paid the company portion.", Toast.LENGTH_LONG).show()
                } else {
                    onSave(
                        original.copy(
                            advanceId = if (paymentType == PaymentType.OWN) null else selectedAdvanceId,
                            supplier = supplier.trim(),
                            dateEpochDay = epoch,
                            receiptNumber = receiptNo.trim(),
                            subtotalCents = parseMoney(subtotal),
                            vatCents = parseMoney(vat),
                            totalCents = totalCents,
                            purpose = purpose.trim(),
                            project = project.trim(),
                            paymentReference = paymentRef.trim(),
                            paymentType = paymentType,
                            ownMoneyCents = ownCents
                        )
                    )
                }
            }
        ) { Text("Save slip") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (onDelete != null) {
            TextButton(onClick = { deleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete slip", color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete this slip?") },
            text = { Text("The stored receipt image and its record will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { deleteConfirm = false; onDelete?.invoke() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PaymentTypePicker(selected: PaymentType, onSelected: (PaymentType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = when (selected) {
        PaymentType.ADVANCE -> "Paid with: Company advance"
        PaymentType.OWN -> "Paid with: My own money"
        PaymentType.SPLIT -> "Paid with: Split payment"
    }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Company advance") }, onClick = { onSelected(PaymentType.ADVANCE); open = false })
            DropdownMenuItem(text = { Text("My own money") }, onClick = { onSelected(PaymentType.OWN); open = false })
            DropdownMenuItem(text = { Text("Split payment") }, onClick = { onSelected(PaymentType.SPLIT); open = false })
        }
    }
}

@Composable
private fun AdvancePicker(advances: List<Advance>, selectedId: Long?, onSelected: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = advances.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { it.project.ifBlank { "Advance #${it.id}" } } ?: "Allocate to advance (optional)")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No advance") }, onClick = { onSelected(null); open = false })
            advances.forEach { a ->
                DropdownMenuItem(
                    text = { Text("${a.project.ifBlank { "Advance #${a.id}" }} • ${money(a.amountCents)}") },
                    onClick = { onSelected(a.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun ReportsScreen(repository: SlipRepository, advances: List<Advance>, slips: List<Slip>, returns: List<MoneyReturn>, reimbursements: List<Reimbursement>) {
    val context = LocalContext.current
    var selectedAdvanceId by remember { mutableStateOf<Long?>(null) }
    val selectedAdvances = advances.filter { selectedAdvanceId == null || it.id == selectedAdvanceId }
    val selectedSlips = slips.filter { selectedAdvanceId == null || it.advanceId == selectedAdvanceId }
    val selectedReturns = returns.filter { selectedAdvanceId == null || it.advanceId == selectedAdvanceId }
    val selectedReimbursements = if (selectedAdvanceId == null) reimbursements else emptyList()
    val rec = repository.reconciliation(selectedAdvanceId)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdvancePicker(advances, selectedAdvanceId, { selectedAdvanceId = it })
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Reconciliation", fontWeight = FontWeight.Bold)
                Text("Received ${money(rec.receivedCents)}")
                Text("Slips ${money(rec.slipsCents)}")
                Text("Returned ${money(rec.returnedCents)}")
                Text("Outstanding ${money(rec.outstandingCents)}", fontWeight = FontWeight.Bold)
            }
        }
        if (selectedSlips.isEmpty()) {
            InfoCard("Nothing to export", "Capture at least one slip before creating PDFs.")
        } else {
            Button(
                onClick = {
                    runCatching {
                        val label = selectedAdvanceId?.let { id -> advances.firstOrNull { it.id == id }?.project?.ifBlank { "Advance #$id" } } ?: "All advances"
                        val file = PdfExporter.officePack(context, label, selectedAdvances, selectedSlips, selectedReturns, selectedReimbursements)
                        shareFile(context, file, "application/pdf", "ECI Office Pack")
                    }.onFailure { Toast.makeText(context, "Could not create Office Pack: ${it.message}", Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text("Create & share Office Pack PDF") }

            OutlinedButton(
                onClick = {
                    runCatching {
                        val file = PdfExporter.combinedDext(context, selectedSlips)
                        shareFile(context, file, "application/pdf", "ECI Dext Pack")
                    }.onFailure { Toast.makeText(context, "Could not create Dext PDF: ${it.message}", Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text("Dext: combined PDF (1 receipt/page)") }

            OutlinedButton(
                onClick = {
                    runCatching {
                        val files = PdfExporter.individualDext(context, selectedSlips)
                        shareFiles(context, files, "application/pdf", "ECI Dext Receipts")
                    }.onFailure { Toast.makeText(context, "Could not create Dext files: ${it.message}", Toast.LENGTH_LONG).show() }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text("Dext: individual receipt PDFs") }
        }
    }
}

@Composable
private fun SettingsScreen(repository: SlipRepository, advances: List<Advance>, slips: List<Slip>, returns: List<MoneyReturn>, reimbursements: List<Reimbursement>) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", 0) }
    var name by remember { mutableStateOf(prefs.getString("name", "Dave") ?: "Dave") }
    var company by remember { mutableStateOf(prefs.getString("company", "ECI Automation") ?: "ECI Automation") }
    var companyRegistration by remember { mutableStateOf(prefs.getString("company_registration", "") ?: "") }
    var companyVat by remember { mutableStateOf(prefs.getString("company_vat", "") ?: "") }
    var companyPhone by remember { mutableStateOf(prefs.getString("company_phone", "") ?: "") }
    var companyEmail by remember { mutableStateOf(prefs.getString("company_email", "") ?: "") }
    var companyAddress by remember { mutableStateOf(prefs.getString("company_address", "") ?: "") }
    var logoPath by remember { mutableStateOf(prefs.getString("company_logo_path", "") ?: "") }
    var officeEmail by remember { mutableStateOf(prefs.getString("office_email", "") ?: "") }
    var dextEmail by remember { mutableStateOf(prefs.getString("dext_email", "") ?: "") }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearActive by remember { mutableStateOf(false) }
    var clearArchived by remember { mutableStateOf(false) }
    var clearSlips by remember { mutableStateOf(false) }
    var clearReturns by remember { mutableStateOf(false) }
    var clearOwnMoney by remember { mutableStateOf(false) }
    var clearReimbursements by remember { mutableStateOf(false) }
    var clearSettings by remember { mutableStateOf(false) }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val target = File(context.cacheDir, "restore_${System.currentTimeMillis()}.zip")
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: error("Could not read selected file")
                restoreFile = target
            }.onFailure { Toast.makeText(context, "Could not open backup: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }


    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val dir = File(context.filesDir, "branding").apply { mkdirs() }
                val target = File(dir, "company_logo")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not read selected logo")
                logoPath = target.absolutePath
            }.onFailure { Toast.makeText(context, "Could not save logo: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val logoBitmap = remember(logoPath) {
        logoPath.takeIf { it.isNotBlank() && File(it).exists() }?.let(BitmapFactory::decodeFile)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Report identity")
        Text("These details appear on the accountant Office Pack.", style = MaterialTheme.typography.bodySmall)
        TextFieldSimple("User / Submitted by *", name, { name = it })

        Spacer(Modifier.height(6.dp)); SectionTitle("Company details")
        TextFieldSimple("Company name", company, { company = it })
        TextFieldSimple("Company registration number", companyRegistration, { companyRegistration = it })
        TextFieldSimple("Company VAT number", companyVat, { companyVat = it })
        TextFieldSimple("Company phone", companyPhone, { companyPhone = it })
        TextFieldSimple("Company email", companyEmail, { companyEmail = it })
        TextFieldSimple("Company address", companyAddress, { companyAddress = it })

        Spacer(Modifier.height(6.dp)); SectionTitle("Report branding")
        if (logoBitmap != null) {
            Card {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = "Company logo",
                    modifier = Modifier.fillMaxWidth().height(90.dp).padding(10.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text("Company logo selected", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("No company logo selected. The report will use a clean text ECI Automation header.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { logoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (logoBitmap == null) "Choose company logo" else "Change company logo")
        }
        if (logoPath.isNotBlank()) {
            TextButton(onClick = {
                runCatching { File(logoPath).delete() }
                logoPath = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Remove company logo") }
        }

        Spacer(Modifier.height(6.dp)); SectionTitle("Email handoff")
        TextFieldSimple("Office email", officeEmail, { officeEmail = it })
        TextFieldSimple("Dext email", dextEmail, { dextEmail = it })
        Button(onClick = {
            prefs.edit()
                .putString("name", name.trim())
                .putString("company", company.trim())
                .putString("company_registration", companyRegistration.trim())
                .putString("company_vat", companyVat.trim())
                .putString("company_phone", companyPhone.trim())
                .putString("company_email", companyEmail.trim())
                .putString("company_address", companyAddress.trim())
                .putString("company_logo_path", logoPath)
                .putString("office_email", officeEmail.trim())
                .putString("dext_email", dextEmail.trim())
                .apply()
            Toast.makeText(context, "Report and email settings saved", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }

        Spacer(Modifier.height(10.dp)); SectionTitle("Backup & restore")
        Text("Backups contain your local records and the stored receipt images. Keep the ZIP somewhere safe.")
        OutlinedButton(onClick = {
            runCatching {
                val file = BackupManager.createBackup(context, advances, slips, returns, reimbursements)
                shareFile(context, file, "application/zip", "ECI Slip Manager Backup")
            }.onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }, modifier = Modifier.fillMaxWidth()) { Text("Create & share backup ZIP") }
        OutlinedButton(onClick = { restorePicker.launch("application/zip") }, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup ZIP") }

        Spacer(Modifier.height(12.dp)); SectionTitle("Data management")
        Text("Clear test data selectively. Make a backup first if there is anything you may want to restore later.")
        OutlinedButton(onClick = { showClearDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear / reset selected data")
        }

        Spacer(Modifier.height(12.dp))
        InfoCard("Storage", "Version 0.7.2 stores the database, original receipt images and report branding privately on this Android phone. No AppDeploy, Replit, VPS, or cloud account is required.")
    }

    restoreFile?.let { file ->
        AlertDialog(
            onDismissRequest = { restoreFile = null; file.delete() },
            title = { Text("Restore backup?") },
            text = { Text("This will replace the current advances, slips and money-return records on this phone with the selected backup.") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val restored = BackupManager.restore(context, file)
                        repository.replaceAll(restored.advances, restored.slips, restored.returns, restored.reimbursements)
                        Toast.makeText(context, "Backup restored", Toast.LENGTH_LONG).show()
                    }.onFailure { Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_LONG).show() }
                    file.delete(); restoreFile = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { file.delete(); restoreFile = null }) { Text("Cancel") } }
        )
    }

    if (showClearDialog) {
        val allSelected = clearActive && clearArchived && clearSlips && clearReturns && clearOwnMoney && clearReimbursements && clearSettings
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear selected data") },
            text = {
                Column(Modifier.height(430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose only the information you want to remove. Deleting advances keeps their slips but makes those slips unallocated; linked money-return records are removed with the advance.")
                    OutlinedButton(onClick = {
                        runCatching {
                            val file = BackupManager.createBackup(context, advances, slips, returns, reimbursements)
                            shareFile(context, file, "application/zip", "ECI Slip Manager Backup")
                        }.onFailure { Toast.makeText(context, "Backup failed: ${it.message}", Toast.LENGTH_LONG).show() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Create backup first") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = allSelected, onCheckedChange = { value ->
                            clearActive = value; clearArchived = value; clearSlips = value; clearReturns = value
                            clearOwnMoney = value; clearReimbursements = value; clearSettings = value
                        })
                        Text("Select all")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearActive, { clearActive = it }); Text("Active advances (${advances.count { !it.archived }})") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearArchived, { clearArchived = it }); Text("Archived advances (${advances.count { it.archived }})") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearSlips, { clearSlips = it }); Text("Slips & receipt photos (${slips.size})") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearReturns, { clearReturns = it }); Text("Money returned records (${returns.size})") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearOwnMoney, { clearOwnMoney = it }); Text("Own-money allocations (${slips.count { it.ownMoneyCents > 0L || it.paymentType != PaymentType.ADVANCE }}) — keeps slips") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearReimbursements, { clearReimbursements = it }); Text("Reimbursement records (${reimbursements.size})") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearSettings, { clearSettings = it }); Text("Report identity, company details, logo and email settings") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val any = clearActive || clearArchived || clearSlips || clearReturns || clearOwnMoney || clearReimbursements || clearSettings
                    if (!any) {
                        Toast.makeText(context, "Select at least one item to clear.", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.clearSelectedData(clearActive, clearArchived, clearSlips, clearReturns, clearOwnMoney, clearReimbursements)
                        if (clearSettings) {
                            prefs.edit().clear().apply()
                            runCatching { if (logoPath.isNotBlank()) File(logoPath).delete() }
                            name = "Dave"; company = "ECI Automation"; companyRegistration = ""; companyVat = ""
                            companyPhone = ""; companyEmail = ""; companyAddress = ""; logoPath = ""
                            officeEmail = ""; dextEmail = ""
                        }
                        Toast.makeText(context, "Selected data cleared", Toast.LENGTH_LONG).show()
                        showClearDialog = false
                    }
                }) { Text("Clear selected", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

}

@Composable
private fun TextFieldSimple(label: String, value: String, onChange: (String) -> Unit, error: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = false, isError = error
    )
}

@Composable
private fun DateField(value: String, onChange: (String) -> Unit, error: Boolean = false) {
    val context = LocalContext.current

    fun openDatePicker() {
        val initial = runCatching { LocalDate.parse(value.trim()) }.getOrNull() ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onChange(LocalDate.of(year, month + 1, dayOfMonth).toString())
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Date (YYYY-MM-DD)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = {
            IconButton(onClick = ::openDatePicker) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Choose date")
            }
        }
    )
}

@Composable
private fun MoneyField(label: String, value: String, onChange: (String) -> Unit, error: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, prefix = { Text("R ") },
        modifier = Modifier.fillMaxWidth(), singleLine = true, isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

private fun shareFile(context: android.content.Context, file: File, mime: String, subject: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

private fun shareFiles(context: android.content.Context, files: List<File>, mime: String, subject: String) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) })
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mime
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        putExtra(Intent.EXTRA_SUBJECT, subject)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, subject))
}

private val zaLocale = Locale("en", "ZA")
private val currencyFormat = NumberFormat.getCurrencyInstance(zaLocale)
private val displayDate = DateTimeFormatter.ofPattern("dd MMM yyyy", zaLocale)

private fun money(cents: Long): String = currencyFormat.format(cents / 100.0)
private fun plainMoney(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0)
private fun dateText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(displayDate)

private fun parseMoney(text: String): Long? {
    val cleaned = text.trim().replace("R", "", ignoreCase = true).replace(" ", "")
    if (cleaned.isBlank()) return null
    val normalized = when {
        cleaned.contains(',') && cleaned.substringAfterLast(',').length == 2 -> cleaned.replace(".", "").replace(',', '.')
        else -> cleaned.replace(",", "")
    }
    return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
}

private fun parseDate(text: String): Long? = try {
    if (text.isBlank()) null else LocalDate.parse(text.trim()).toEpochDay()
} catch (_: DateTimeParseException) { null }
