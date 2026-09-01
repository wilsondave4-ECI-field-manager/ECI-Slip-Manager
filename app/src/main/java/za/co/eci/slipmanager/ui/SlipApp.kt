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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import za.co.eci.slipmanager.backup.BackupManager
import za.co.eci.slipmanager.data.Advance
import za.co.eci.slipmanager.data.AdvanceReportArchiveStore
import za.co.eci.slipmanager.data.CompanyCard
import za.co.eci.slipmanager.data.CompanyCardDetails
import za.co.eci.slipmanager.data.DocumentNumberStore
import za.co.eci.slipmanager.data.MoneyReturn
import za.co.eci.slipmanager.data.MoneyRequest
import za.co.eci.slipmanager.data.PaymentType
import za.co.eci.slipmanager.data.PersonalFundsSummary
import za.co.eci.slipmanager.data.PersonalReportArchiveStore
import za.co.eci.slipmanager.data.Reimbursement
import za.co.eci.slipmanager.data.Refund
import za.co.eci.slipmanager.data.Slip
import za.co.eci.slipmanager.data.SlipRepository
import za.co.eci.slipmanager.data.SyncState
import za.co.eci.slipmanager.ocr.ReceiptGuess
import za.co.eci.slipmanager.ocr.ReceiptOcr
import za.co.eci.slipmanager.ocr.ReceiptScanProcessor
import za.co.eci.slipmanager.pdf.PdfExporter
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class Screen { HOME, REQUEST_MONEY, CARDS, ADVANCES, REFUNDS, BALANCE_HISTORY, MISSING_PAYMENT, SLIPS, REPORTS, ARCHIVE, SETTINGS, EDIT_SLIP }

@Composable
private fun ServerLoginScreen(
    message: String,
    onSignIn: (String, String) -> Unit,
    onForgotPassword: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("ECI Slip Manager", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Sign in once to connect this phone. Afterwards, scanning and saving receipts works offline.")
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Work email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        if (message.isNotBlank()) {
            Spacer(Modifier.height(10.dp)); Text(message, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onSignIn(email.trim(), password) },
            enabled = email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) { Text("Sign in") }
        TextButton(
            onClick = { onForgotPassword(email.trim()) }, enabled = email.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Forgot password") }
    }
}

@Composable
private fun FirstLoginPasswordScreen(message: String, onSave: (String) -> Unit, onSignOut: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = password.length >= 10 && password == confirm
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Create your permanent password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Your temporary password worked. Choose a private password with at least 10 characters.")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(password, { password = it }, label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(confirm, { confirm = it }, label = { Text("Confirm password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (confirm.isNotBlank() && password != confirm) Text("The passwords do not match", color = MaterialTheme.colorScheme.error)
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(password) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Create permanent password") }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

@Composable
fun SlipApp(repository: SlipRepository) {
    val context = LocalContext.current
    val activity = context as Activity
    val advances by repository.advances.collectAsState()
    val slips by repository.slips.collectAsState()
    val returns by repository.returns.collectAsState()
    val reimbursements by repository.reimbursements.collectAsState()
    val refunds by repository.refunds.collectAsState()
    val cards by repository.cards.collectAsState()
    val moneyRequests by repository.moneyRequests.collectAsState()
    val cardDetails by repository.cardDetails.collectAsState()
    val session by repository.session.collectAsState()
    val serverMessage by repository.serverMessage.collectAsState()

    if (session == null) {
        ServerLoginScreen(serverMessage, repository::signIn, repository::forgotPassword)
        return
    }
    if (session?.mustChangePassword == true) {
        FirstLoginPasswordScreen(serverMessage, repository::changePassword, repository::signOut)
        return
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            repository.refresh()
        }
    }
    val personalArchiveStore = remember { PersonalReportArchiveStore(context) }
    val advanceArchiveStore = remember { AdvanceReportArchiveStore(context) }
    val documentNumberStore = remember { DocumentNumberStore(context) }
    var personalArchiveRevision by remember { mutableLongStateOf(0L) }
    val personalSummary = remember(slips, reimbursements, personalArchiveRevision) {
        personalArchiveStore.currentSummary(slips, reimbursements)
    }

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

    LaunchedEffect(advances, slips, returns) {
        // Close active advances only after the exact company-funded balance reaches zero.
        advances.filterNot { it.archived }.forEach { advance ->
            val advanceSlips = slips.filter { it.advanceId == advance.id }
            val advanceReturns = returns.filter { it.advanceId == advance.id }
            val hasActivity = advanceSlips.isNotEmpty() || advanceReturns.isNotEmpty()
            val balance = advance.amountCents - advanceSlips.sumOf { it.companyPaidCents } - advanceReturns.sumOf { it.amountCents }
            if (hasActivity && balance == 0L) {
                val previous = advanceArchiveStore.forAdvance(advance.id)
                val archivedEntry = previous ?: runCatching {
                    val documentNumber = documentNumberStore.nextNumber()
                    advanceArchiveStore.archiveSettled(
                        advance = advance,
                        slips = slips,
                        returns = returns,
                        documentNumber = documentNumber
                    ) { reportSlips, reportReturns, number ->
                        PdfExporter.officePack(
                            context = context,
                            title = advance.project.ifBlank { "Advance #${advance.id}" },
                            advances = listOf(advance),
                            slips = reportSlips,
                            returns = reportReturns,
                            reimbursements = emptyList(),
                            documentNumber = number
                        )
                    }
                }.getOrElse { error ->
                    Toast.makeText(context, "Could not save completed advance report: ${error.message}", Toast.LENGTH_LONG).show()
                    null
                }

                if (archivedEntry != null) {
                    repository.archiveAdvance(advance, true)
                    personalArchiveRevision++
                    if (previous == null) {
                        Toast.makeText(context, "Advance settled and archived as ${archivedEntry.documentNumber}", Toast.LENGTH_LONG).show()
                        launchPreparedReportEmail(
                            context,
                            advanceArchiveStore.reportFile(archivedEntry),
                            archivedEntry.documentNumber,
                            "ECI Advance Report"
                        )
                    }
                }
            }
        }

        // v0.7.4 already archived some settled advances before permanent PDF
        // snapshots existed. Create snapshots for those once, but do not pop an
        // email composer for historical/migrated reports.
        advances.filter { it.archived }.forEach { advance ->
            if (advanceArchiveStore.forAdvance(advance.id) == null) {
                val advanceSlips = slips.filter { it.advanceId == advance.id }
                val advanceReturns = returns.filter { it.advanceId == advance.id }
                val balance = advance.amountCents - advanceSlips.sumOf { it.companyPaidCents } - advanceReturns.sumOf { it.amountCents }
                if ((advanceSlips.isNotEmpty() || advanceReturns.isNotEmpty()) && balance == 0L) {
                    runCatching {
                        val documentNumber = documentNumberStore.nextNumber()
                        advanceArchiveStore.archiveSettled(advance, slips, returns, documentNumber) { reportSlips, reportReturns, number ->
                            PdfExporter.officePack(
                                context, advance.project.ifBlank { "Advance #${advance.id}" },
                                listOf(advance), reportSlips, reportReturns, emptyList(), number
                            )
                        }
                    }.onSuccess { if (it != null) personalArchiveRevision++ }
                }
            }
        }
    }

    LaunchedEffect(slips, reimbursements) {
        val livePersonal = personalArchiveStore.currentSummary(slips, reimbursements)
        if (livePersonal.usedCents > 0L && livePersonal.outstandingCents == 0L) {
            runCatching {
                val documentNumber = documentNumberStore.nextNumber()
                personalArchiveStore.closeIfSettled(slips, reimbursements, documentNumber) { settlementSlips, settlementReimbursements, number ->
                    PdfExporter.officePack(
                        context = context,
                        title = "Personal funds settlement",
                        advances = emptyList(),
                        slips = settlementSlips,
                        returns = emptyList(),
                        reimbursements = settlementReimbursements,
                        documentNumber = number
                    )
                }
            }.onSuccess { archived ->
                if (archived != null) {
                    personalArchiveRevision++
                    Toast.makeText(context, "Personal funds settled and archived as ${archived.documentNumber}", Toast.LENGTH_LONG).show()
                    launchPreparedReportEmail(
                        context,
                        personalArchiveStore.reportFile(archived),
                        archived.documentNumber,
                        "ECI Personal Funds Report"
                    )
                }
            }.onFailure { error ->
                Toast.makeText(context, "Could not archive settled personal-funds report: ${error.message}", Toast.LENGTH_LONG).show()
            }
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
                    Screen.REQUEST_MONEY -> "Request Money"
                    Screen.CARDS -> "Company Cards"
                    Screen.ADVANCES -> "Advances / Money Received"
                    Screen.REFUNDS -> "My Refunds"
                    Screen.BALANCE_HISTORY -> "My Balance / History"
                    Screen.MISSING_PAYMENT -> "Payment Missing"
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
            if (screen in setOf(Screen.HOME, Screen.BALANCE_HISTORY, Screen.SLIPS, Screen.REQUEST_MONEY, Screen.SETTINGS)) {
                BottomNav(screen, onNavigate = { screen = it }, onScan = ::startScan)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    repository = repository,
                    advances = advances,
                    slips = slips,
                    refunds = refunds,
                    personalSummary = personalSummary,
                    onAdvances = { screen = Screen.ADVANCES },
                    onBalanceHistory = { screen = Screen.BALANCE_HISTORY },
                    onRefunds = { screen = Screen.REFUNDS },
                    onSlip = { editingSlip = it; screen = Screen.EDIT_SLIP },
                    onScan = ::startScan,
                    onRequestMoney = { screen = Screen.REQUEST_MONEY },
                    onCards = { repository.refreshCardDetails(); screen = Screen.CARDS },
                    onReports = { screen = Screen.REPORTS }
                )
                Screen.REQUEST_MONEY -> MoneyRequestScreen(
                    requests = moneyRequests,
                    onSave = repository::saveMoneyRequest,
                    onBack = { screen = Screen.HOME }
                )
                Screen.CARDS -> CompanyCardsScreen(
                    details = cardDetails.copy(cards = if (cardDetails.cards.isEmpty()) cards else cardDetails.cards),
                    message = serverMessage,
                    onRefresh = repository::refreshCardDetails,
                    onRequest = repository::requestCardFunds,
                    onUpdate = repository::acknowledgeCardUpdate,
                    onBack = { screen = Screen.HOME }
                )
                Screen.REFUNDS -> MyRefundsScreen(refunds, serverMessage, repository::refreshFunding, repository::requestRefundUpdate)
                Screen.ADVANCES -> EmployeeAdvancesScreen(
                    advances = advances,
                    requests = moneyRequests,
                    onRequestMoney = { screen = Screen.REQUEST_MONEY },
                    onMissingPayment = { screen = Screen.MISSING_PAYMENT }
                )
                Screen.BALANCE_HISTORY -> BalanceHistoryScreen(
                    repository, advances, slips, returns,
                    onSlip = { editingSlip = it; screen = Screen.EDIT_SLIP },
                    onMissingPayment = { screen = Screen.MISSING_PAYMENT },
                    onCompletedReports = { screen = Screen.ARCHIVE }
                )
                Screen.MISSING_PAYMENT -> MissingPaymentScreen(
                    requests = moneyRequests,
                    message = serverMessage,
                    onReport = repository::reportPaymentReceived,
                    onBack = { screen = Screen.BALANCE_HISTORY }
                )
                Screen.SLIPS -> SlipsScreen(
                    slips = slips,
                    onOpen = { editingSlip = it; screen = Screen.EDIT_SLIP }
                )
                Screen.REPORTS -> ReportsScreen(advances, slips, returns, reimbursements, personalArchiveStore, documentNumberStore)
                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns, reimbursements, personalArchiveStore, advanceArchiveStore, documentNumberStore, personalArchiveRevision)
                Screen.SETTINGS -> SettingsScreen(repository, advances, slips, returns, reimbursements)
                Screen.EDIT_SLIP -> editingSlip?.let { original ->
                    EditSlipScreen(
                        original = original,
                        advances = advances.filter { !it.archived || repository.reconciliation(it.id).outstandingCents != 0L },
                        cards = cards,
                        onSave = { candidate ->
                            var adjusted = candidate
                            var movedToPersonal = 0L
                            val advanceId = candidate.advanceId
                            if (advanceId != null && candidate.serverAdvanceId == null) {
                                advances.firstOrNull { it.id == advanceId }?.let { advance ->
                                    val otherSpent = slips.filter { it.advanceId == advanceId && it.id != candidate.id }
                                        .sumOf { it.companyPaidCents }
                                    val returned = returns.filter { it.advanceId == advanceId }.sumOf { it.amountCents }
                                    val available = (advance.amountCents - otherSpent - returned).coerceAtLeast(0L)
                                    if (candidate.companyPaidCents > available) {
                                        val requiredOwn = (candidate.totalCents - available).coerceIn(0L, candidate.totalCents)
                                        adjusted = if (available > 0L) {
                                            candidate.copy(
                                                paymentType = PaymentType.SPLIT,
                                                ownMoneyCents = maxOf(candidate.ownMoneyCents, requiredOwn)
                                            )
                                        } else {
                                            candidate.copy(
                                                advanceId = null,
                                                paymentType = PaymentType.OWN,
                                                ownMoneyCents = candidate.totalCents
                                            )
                                        }
                                        movedToPersonal = (adjusted.ownMoneyCents - candidate.ownMoneyCents).coerceAtLeast(0L)
                                    }
                                }
                            }
                            repository.saveSlip(adjusted)
                            if (movedToPersonal > 0L) {
                                Toast.makeText(
                                    context,
                                    "Advance exhausted. ${money(movedToPersonal)} moved to Personal Funds.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
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
            if (title == "ECI Slip Manager") Text("Native • Offline-ready • VPS sync", style = MaterialTheme.typography.labelSmall)
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
            selected = screen == Screen.BALANCE_HISTORY || screen == Screen.SLIPS,
            onClick = { onNavigate(Screen.BALANCE_HISTORY) },
            icon = { Icon(Icons.Default.ReceiptLong, null) }, label = { Text("Balance") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onScan,
            icon = { Icon(Icons.Default.CameraAlt, null) }, label = { Text("Scan") }
        )
        NavigationBarItem(
            selected = screen == Screen.REQUEST_MONEY,
            onClick = { onNavigate(Screen.REQUEST_MONEY) },
            icon = { Icon(Icons.Default.Add, null) }, label = { Text("Requests") }
        )
        NavigationBarItem(
            selected = screen == Screen.SETTINGS,
            onClick = { onNavigate(Screen.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Profile") }
        )
    }
}

@Composable
private fun HomeScreen(
    repository: SlipRepository,
    advances: List<Advance>,
    slips: List<Slip>,
    refunds: List<Refund>,
    personalSummary: PersonalFundsSummary,
    onAdvances: () -> Unit,
    onBalanceHistory: () -> Unit,
    onRefunds: () -> Unit,
    onSlip: (Slip) -> Unit,
    onScan: () -> Unit,
    onRequestMoney: () -> Unit,
    onCards: () -> Unit,
    onReports: () -> Unit
) {
    val session by repository.session.collectAsState()
    val moneyRequests by repository.moneyRequests.collectAsState()
    val serverMessage by repository.serverMessage.collectAsState()
    val outstanding = repository.activeReconciliation().outstandingCents.coerceAtLeast(0L)
    val refundOutstanding = refunds.takeIf { it.isNotEmpty() }?.sumOf { it.outstandingCents + it.pendingCents }
        ?: personalSummary.outstandingCents
    val pendingCount = slips.count { it.syncState != SyncState.SYNCED } +
        moneyRequests.count { it.syncState != SyncState.SYNCED }
    val firstName = session?.displayName?.trim()?.substringBefore(' ')?.ifBlank { "Employee" } ?: "Employee"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Welcome, $firstName", style = MaterialTheme.typography.titleMedium)
        }
        item {
            DashboardBalanceCard("Company money outstanding", money(outstanding), Color(0xFFEFFAF2), Color(0xFF07883F), onAdvances)
        }
        item {
            DashboardBalanceCard("Refunds owed to you", money(refundOutstanding), Color(0xFFFFF1F1), Color(0xFFC9232B), onRefunds)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmployeeActionCard("Request Money", "Ask for company funds", Color(0xFF119447), Modifier.weight(1f), onRequestMoney)
                EmployeeActionCard("Capture Slip", "Submit a receipt or invoice", Color(0xFF0B67B7), Modifier.weight(1f), onScan)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmployeeActionCard("My Refunds", "Track refunds and request updates", Color(0xFFEA7100), Modifier.weight(1f), onRefunds)
                EmployeeActionCard("My Balance / History", "Advances, balances and activity", Color(0xFF7A42C3), Modifier.weight(1f), onBalanceHistory)
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                EmployeeActionCard("Company Cards", "Balance, funding and assignments", Color(0xFF1769AA), Modifier.fillMaxWidth(0.5f), onCards)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = if (pendingCount == 0) Color(0xFFEFFAF2) else Color(0xFFFFF8E7))) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (pendingCount == 0) "All activity synced" else "$pendingCount waiting to sync", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (pendingCount > 0) TextButton(onClick = repository::syncNow) { Text("Sync now") }
                }
            }
        }
        if (serverMessage.isNotBlank() && serverMessage.contains("Offline", ignoreCase = true)) {
            item { Text(serverMessage, style = MaterialTheme.typography.bodySmall) }
        }
        item { SectionTitle("Recent Activity") }
        val recentSlips = slips.sortedByDescending { it.createdAtMillis }.take(3)
        val recentRequests = moneyRequests.sortedByDescending { it.createdAtMillis }.take(2)
        if (recentSlips.isEmpty() && recentRequests.isEmpty()) {
            item { InfoCard("No recent activity", "Your receipts, requests and refunds will appear here.") }
        } else {
            items(recentRequests, key = { it.clientUuid }) { request ->
                Card(Modifier.fillMaxWidth().clickable(onClick = onRequestMoney)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Money request", fontWeight = FontWeight.Bold)
                        Text("${request.purpose} • ${money(request.requestedCents)}")
                        Text(if (request.serverId == null) syncLabel(request.syncState) else request.status.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(recentSlips, key = { it.id }) { slip -> SlipCard(slip, onSlip) }
        }
        item { OutlinedButton(onClick = onReports, modifier = Modifier.fillMaxWidth()) { Text("Reports") } }
    }
}

@Composable
private fun DashboardBalanceCard(label: String, value: String, background: Color, accent: Color, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(value, color = accent, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmployeeActionCard(title: String, subtitle: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(accent, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun syncLabel(state: SyncState): String = when (state) {
    SyncState.SYNCED -> "Synced"
    SyncState.SYNCING -> "Syncing"
    SyncState.FAILED -> "Waiting to retry"
    SyncState.PENDING, SyncState.LOCAL_ONLY -> "Waiting to sync"
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
private fun MoneyRequestScreen(
    requests: List<MoneyRequest>,
    onSave: (MoneyRequest) -> Unit,
    onBack: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var projectSite by remember { mutableStateOf("") }
    var requiredDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    val cents = parseMoney(amount)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Ask for company funds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Requests are saved on this phone first and sent automatically when there is a connection.")
        }
        item { MoneyField("Amount (R)", amount, { amount = it }, cents == null && amount.isNotBlank()) }
        item { TextFieldSimple("Purpose", purpose, { purpose = it }) }
        item { TextFieldSimple("Project / site (manual entry)", projectSite, { projectSite = it }) }
        item { DateField(requiredDate, { requiredDate = it }) }
        item { TextFieldSimple("Additional note (optional)", note, { note = it }) }
        item {
            Button(
                onClick = {
                    onSave(
                        MoneyRequest(
                            requestedCents = cents ?: 0L,
                            purpose = purpose.trim(),
                            projectSite = projectSite.trim(),
                            requiredDate = requiredDate,
                            employeeNote = note.trim()
                        )
                    )
                    amount = ""; purpose = ""; projectSite = ""; note = ""
                },
                enabled = cents != null && cents > 0L && purpose.isNotBlank() && projectSite.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Send money request", fontWeight = FontWeight.Bold) }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to home") } }
        item { SectionTitle("My requests") }
        if (requests.isEmpty()) item { InfoCard("No requests yet", "Your money requests will appear here.") }
        items(requests.sortedByDescending { it.createdAtMillis }, key = { it.clientUuid }) { request ->
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(request.purpose, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(money(request.requestedCents), fontWeight = FontWeight.Bold)
                    }
                    Text(request.projectSite)
                    val requestState = if (request.serverId == null) syncLabel(request.syncState) else request.status.replace('_', ' ')
                    Text("Needed ${request.requiredDate} • $requestState", style = MaterialTheme.typography.labelSmall)
                    request.approvedCents?.let { Text("Approved ${money(it)}", style = MaterialTheme.typography.labelSmall) }
                    if (request.accountantNote.isNotBlank()) Text("Accountant: ${request.accountantNote}", style = MaterialTheme.typography.bodySmall)
                    if (request.syncError.isNotBlank()) Text(request.syncError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun CompanyCardsScreen(
    details: CompanyCardDetails,
    message: String,
    onRefresh: () -> Unit,
    onRequest: (String, Long, String) -> Unit,
    onUpdate: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var selectedCardId by remember(details.cards) { mutableStateOf(details.cards.firstOrNull()?.serverId) }
    var amount by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    val cents = parseMoney(amount)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Company Cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("See the assigned card balance and request funds when needed.")
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        if (details.cards.isEmpty()) {
            item { InfoCard("No card assigned", "An accountant or administrator must assign a company card to you.") }
        } else {
            items(details.cards, key = { it.serverId }) { card ->
                Card(
                    Modifier.fillMaxWidth().clickable { selectedCardId = card.serverId },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedCardId == card.serverId) Color(0xFFEAF4FF) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(card.name, fontWeight = FontWeight.Bold)
                            if (card.reference.isNotBlank()) Text(card.reference, style = MaterialTheme.typography.labelSmall)
                        }
                        Text(money(card.balanceCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0B67B7))
                    }
                }
            }
            item { SectionTitle("Request money for selected card") }
            item { MoneyField("Amount (R)", amount, { amount = it }, cents == null && amount.isNotBlank()) }
            item { TextFieldSimple("Reason / purchase purpose", purpose, { purpose = it }) }
            item {
                Button(
                    onClick = {
                        selectedCardId?.let { onRequest(it, cents ?: 0L, purpose.trim()) }
                        amount = ""; purpose = ""
                    },
                    enabled = selectedCardId != null && cents != null && cents > 0L && purpose.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send card funding request") }
                Text("A connection is required to submit a company-card funding request.", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.primary) }
        if (details.updates.isNotEmpty()) {
            item { SectionTitle("Slip updates requested") }
            items(details.updates, key = { "update-${it.id}" }) { update ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(update.cardName, fontWeight = FontWeight.Bold)
                        if (update.message.isNotBlank()) Text(update.message)
                        update.bankBalanceCents?.let { Text("Bank balance reported: ${money(it)}") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onUpdate(update.id, false) }) { Text("Acknowledge") }
                            Button(onClick = { onUpdate(update.id, true) }) { Text("Slips updated") }
                        }
                    }
                }
            }
        }
        if (details.requests.isNotEmpty()) {
            item { SectionTitle("Card funding history") }
            items(details.requests, key = { "request-${it.id}" }) { request ->
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(request.cardName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(money(request.requestedCents), fontWeight = FontWeight.Bold)
                        }
                        Text(request.purpose)
                        Text(request.status, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to home") } }
    }
}

@Composable
private fun MyRefundsScreen(
    refunds: List<Refund>,
    message: String,
    onRefresh: () -> Unit,
    onRequestUpdate: (String) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("My Refunds", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Refunds are created automatically whenever your own money pays all or part of a slip.")
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
        if (message.isNotBlank()) item { Text(message, style = MaterialTheme.typography.bodySmall) }
        if (refunds.isEmpty()) {
            item { InfoCard("No refunds yet", "A refund will appear here automatically after a personal-money slip is synced.") }
        }
        items(refunds.sortedByDescending { it.openedAt }, key = { it.serverId }) { refund ->
            val updateWaiting = refund.updateRequestedAt.isNotBlank() &&
                (refund.accountantViewedAt.isBlank() || refund.updateRequestedAt > refund.accountantViewedAt)
            val label = when {
                refund.reimbursedCents > 0L && refund.outstandingCents == 0L && refund.pendingCents == 0L -> "Refunded"
                refund.reimbursedCents > 0L && refund.outstandingCents > 0L -> "Partly refunded"
                refund.pendingCents > 0L -> "Awaiting slip review"
                refund.accountantViewedAt.isNotBlank() -> "Viewed by accountant/admin"
                else -> "Submitted"
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(money(refund.outstandingCents), fontWeight = FontWeight.Bold, color = if (refund.outstandingCents > 0L) Color(0xFFC9232B) else Color(0xFF07883F))
                    }
                    Text("Personal purchases ${money(refund.totalCents)}")
                    Text("Approved ${money(refund.approvedCents)} • Refunded ${money(refund.reimbursedCents)}")
                    if (refund.pendingCents > 0L) Text("Awaiting review ${money(refund.pendingCents)}")
                    if (refund.accountantViewedAt.isNotBlank()) {
                        Text(
                            "Viewed by ${refund.accountantViewedByName.ifBlank { "accountant/admin" }} on ${displayTimestamp(refund.accountantViewedAt)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Not viewed yet", style = MaterialTheme.typography.bodySmall)
                    }
                    if (refund.reimbursedCents > 0L) {
                        Text(
                            "Refund recorded ${refund.lastReimbursedDate.ifBlank { displayTimestamp(refund.settledAt) }}" +
                                refund.lastReimbursementReference.takeIf { it.isNotBlank() }?.let { " • Ref $it" }.orEmpty(),
                            color = Color(0xFF07883F), fontWeight = FontWeight.Bold
                        )
                    }
                    if (updateWaiting) {
                        Text("Update requested ${displayTimestamp(refund.updateRequestedAt)}", color = Color(0xFFEA7100), fontWeight = FontWeight.Bold)
                    }
                    if (refund.status != "SETTLED" || refund.outstandingCents > 0L || refund.pendingCents > 0L) {
                        OutlinedButton(
                            onClick = { onRequestUpdate(refund.serverId) },
                            enabled = !updateWaiting,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (updateWaiting) "Update requested" else "Request an update") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmployeeAdvancesScreen(
    advances: List<Advance>,
    requests: List<MoneyRequest>,
    onRequestMoney: () -> Unit,
    onMissingPayment: () -> Unit
) {
    val approvedRequests = requests.filter { it.serverId != null && it.status in listOf("APPROVED", "PART_APPROVED") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Advances / Money Received", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Advances appear automatically after the accountant/admin records payment.")
        }
        item { Button(onClick = onRequestMoney, modifier = Modifier.fillMaxWidth()) { Text("Request money") } }
        item { SectionTitle("Your advances") }
        if (advances.isEmpty()) item { InfoCard("No money received yet", "Paid advances will appear here after the next sync.") }
        items(advances, key = { "funding-${it.id}" }) { advance ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(advance.project.ifBlank { advance.reference.ifBlank { "Company advance" } }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${money(advance.remainingCents)} left", fontWeight = FontWeight.Bold)
                    }
                    Text("${dateText(advance.dateEpochDay)} • ${advance.reference.ifBlank { "No payment reference" }}")
                    Text("Original ${money(advance.amountCents)} • Used on slips ${money(advance.spentCents)}")
                    Text("Returned ${money(advance.returnedCents)} • ${advance.status.replace('_', ' ')}")
                }
            }
        }
        if (approvedRequests.isNotEmpty()) {
            item { SectionTitle("Approved payments not yet on advances") }
            items(approvedRequests, key = { "approved-${it.serverId}" }) { request ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(request.purpose, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(money(request.approvedCents ?: request.requestedCents), fontWeight = FontWeight.Bold)
                        }
                        Text("${request.projectSite.ifBlank { "No project" }} • ${request.status.replace('_', ' ')}")
                        if (request.employeeReportedPaidAt.isNotBlank()) Text("Payment received reported • waiting for accountant/admin verification", color = Color(0xFFEA7100))
                    }
                }
            }
            item {
                OutlinedButton(onClick = onMissingPayment, modifier = Modifier.fillMaxWidth()) {
                    Text("Payment received but missing")
                }
            }
        }
        item {
            Text("Only use the missing-payment report after an accountant/admin has paid an approved or part-approved request and the advance is still absent.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BalanceHistoryScreen(
    repository: SlipRepository,
    advances: List<Advance>,
    slips: List<Slip>,
    returns: List<MoneyReturn>,
    onSlip: (Slip) -> Unit,
    onMissingPayment: () -> Unit,
    onCompletedReports: () -> Unit
) {
    val active = advances.filter { !it.archived && it.status in listOf("OPEN", "REOPENED") }
    val completed = advances.filterNot { it in active }

    fun remaining(advance: Advance): Long = if (advance.serverId != null) {
        advance.remainingCents
    } else {
        repository.reconciliation(advance.id).outstandingCents.coerceAtLeast(0L)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("My Balance / History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Each advance shows the original amount paid and the amount still available.")
        }
        item { SectionTitle("Open advances") }
        if (active.isEmpty()) item { InfoCard("No open advances", "A paid advance will appear here after the accountant/admin records it.") }
        items(active, key = { "active-${it.id}" }) { advance ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(advance.project.ifBlank { "Advance ${advance.reference.ifBlank { "#${advance.id}" }}" }, fontWeight = FontWeight.Bold)
                    Text("Paid ${dateText(advance.dateEpochDay)}${advance.reference.takeIf { it.isNotBlank() }?.let { " • Ref $it" }.orEmpty()}")
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) { Text("Original", style = MaterialTheme.typography.labelMedium); Text(money(advance.amountCents), fontWeight = FontWeight.Bold) }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) { Text("Remaining", style = MaterialTheme.typography.labelMedium); Text(money(remaining(advance)), fontWeight = FontWeight.Bold, color = Color(0xFF07883F)) }
                    }
                    Text("Used on slips ${money(advance.spentCents)} • Returned ${money(advance.returnedCents)}")
                    Text(advance.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            OutlinedButton(onClick = onMissingPayment, modifier = Modifier.fillMaxWidth()) {
                Text("Payment received but not showing?")
            }
        }
        if (completed.isNotEmpty()) {
            item { SectionTitle("Completed advances") }
            items(completed, key = { "completed-${it.id}" }) { advance ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(advance.project.ifBlank { "Advance ${advance.reference.ifBlank { "#${advance.id}" }}" }, fontWeight = FontWeight.Bold)
                        Text("Original ${money(advance.amountCents)} • Used on slips ${money(advance.spentCents)}")
                        Text("Returned ${money(advance.returnedCents)} • Remaining ${money(remaining(advance))}")
                        Text("${dateText(advance.dateEpochDay)} • ${advance.status.replace('_', ' ')}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        item { OutlinedButton(onClick = onCompletedReports, modifier = Modifier.fillMaxWidth()) { Text("Completed reports") } }
        item { SectionTitle("Transaction history") }
        if (slips.isEmpty() && returns.isEmpty()) item { InfoCard("No transactions yet", "Your synced slips and returned money will appear here.") }
        items(slips.sortedByDescending { it.createdAtMillis }, key = { "history-slip-${it.id}" }) { slip -> SlipCard(slip, onSlip) }
        items(returns.sortedByDescending { it.dateEpochDay }, key = { "history-return-${it.id}" }) { returned ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Column(Modifier.weight(1f)) { Text("Money returned", fontWeight = FontWeight.Bold); Text(dateText(returned.dateEpochDay)) }
                    Text(money(returned.amountCents), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MissingPaymentScreen(
    requests: List<MoneyRequest>,
    message: String,
    onReport: (String, Long, String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val eligible = requests.filter {
        it.serverId != null &&
            it.status in listOf("APPROVED", "PART_APPROVED") &&
            it.employeeReportedPaidAt.isBlank()
    }
    var selectedId by remember(eligible) { mutableStateOf(eligible.firstOrNull()?.serverId) }
    val selected = eligible.firstOrNull { it.serverId == selectedId }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }

    LaunchedEffect(selected?.serverId) {
        selected?.let {
            amount = plainMoney(it.employeeReportedPaidCents ?: it.approvedCents ?: it.requestedCents)
            date = it.employeeReportedPaidDate.ifBlank { LocalDate.now().toString() }
            reference = it.employeeReportedPaymentReference
            note = it.employeeReportedPaymentNote
            confirmed = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Payment received but missing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Use this only when an accountant or administrator has already paid you, but the advance is not showing. This sends an audited correction request; it does not create a second advance.")
        }
        if (eligible.isEmpty()) {
            item { InfoCard("No request available", "There must be an existing money request that has not already been recorded as paid.") }
        } else {
            item { SectionTitle("Select the related money request") }
            items(eligible, key = { "missing-${it.serverId}" }) { request ->
                Card(
                    Modifier.fillMaxWidth().clickable { selectedId = request.serverId },
                    colors = CardDefaults.cardColors(containerColor = if (selectedId == request.serverId) Color(0xFFEAF4FF) else MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) { Text(request.purpose, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(money(request.requestedCents), fontWeight = FontWeight.Bold) }
                        Text("${request.projectSite} • ${request.status.replace('_', ' ')}", style = MaterialTheme.typography.labelSmall)
                        if (request.employeeReportedPaidAt.isNotBlank()) Text("Already reported ${displayTimestamp(request.employeeReportedPaidAt)}", color = Color(0xFFEA7100))
                    }
                }
            }
            item { MoneyField("Amount actually received", amount, { amount = it }) }
            item { DateField(date, { date = it }) }
            item { TextFieldSimple("Payment / bank reference", reference, { reference = it }) }
            item { TextFieldSimple("Note (optional)", note, { note = it }) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("I confirm this money was already paid by an accountant or administrator.", modifier = Modifier.weight(1f))
                }
            }
            item {
                val cents = parseMoney(amount)
                Button(
                    onClick = {
                        val requestId = selected?.serverId ?: return@Button
                        onReport(requestId, cents ?: 0L, date, reference.trim(), note.trim())
                        confirmed = false
                    },
                    enabled = selected != null && cents != null && cents > 0L && parseDate(date) != null && reference.isNotBlank() && confirmed,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send correction to accountant/admin") }
            }
        }
        if (message.isNotBlank()) item { Text(message, style = MaterialTheme.typography.bodySmall) }
        item { OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to My Balance / History") } }
    }
}

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
    returns: List<MoneyReturn>,
    reimbursements: List<Reimbursement>,
    personalArchiveStore: PersonalReportArchiveStore,
    advanceArchiveStore: AdvanceReportArchiveStore,
    documentNumberStore: DocumentNumberStore,
    archiveRevision: Long
) {
    val context = LocalContext.current
    val personalArchives = remember(slips, reimbursements, archiveRevision) { personalArchiveStore.entries() }
    val advanceArchives = remember(advances, slips, returns, archiveRevision) { advanceArchiveStore.entries() }
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
                "Completed reports",
                "Settled advances and fully reimbursed personal-funds reports move here automatically. Each completed Office Pack keeps its document number permanently."
            )
        }

        if (personalArchives.isNotEmpty()) {
            item { SectionTitle("Personal funds settlements") }
            items(personalArchives, key = { "personal_${it.id}" }) { archived ->
                val closedDate = java.time.Instant.ofEpochMilli(archived.closedAtMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(displayDate)
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Personal funds settlement", fontWeight = FontWeight.Bold)
                        if (archived.documentNumber.isNotBlank()) Text("Document ${archived.documentNumber}", fontWeight = FontWeight.Bold)
                        else Text("Legacy report (created before document numbering)", style = MaterialTheme.typography.bodySmall)
                        Text("Closed $closedDate")
                        Text("Used ${money(archived.usedCents)}")
                        Text("Reimbursed ${money(archived.reimbursedCents)}")
                        Text("SETTLED • R0.00", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val saved = personalArchiveStore.reportFile(archived)
                                    val report = if (saved.exists()) saved else {
                                        val number = archived.documentNumber.ifBlank { documentNumberStore.nextNumber() }
                                        PdfExporter.officePack(
                                            context, "Personal funds settlement", emptyList(),
                                            personalArchiveStore.slipsFor(archived, slips), emptyList(),
                                            personalArchiveStore.reimbursementsFor(archived, reimbursements), number
                                        )
                                    }
                                    shareFile(context, report, "application/pdf", "ECI Personal Funds ${archived.documentNumber}".trim())
                                }.onFailure { Toast.makeText(context, "Could not share archived report: ${it.message}", Toast.LENGTH_LONG).show() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share report") }
                    }
                }
            }
        }

        if (archivedAdvances.isNotEmpty()) {
            item { SectionTitle("Archived advances") }
            items(archivedAdvances, key = { it.id }) { advance ->
                val advanceSlips = slips.filter { it.advanceId == advance.id }
                val advanceReturns = returns.filter { it.advanceId == advance.id }
                val spent = if (advance.serverId != null) advance.spentCents else advanceSlips.sumOf { it.companyPaidCents }
                val returned = if (advance.serverId != null) advance.returnedCents else advanceReturns.sumOf { it.amountCents }
                val balance = if (advance.serverId != null) advance.remainingCents else advance.amountCents - spent - returned
                val settled = advance.status == "SETTLED" || balance == 0L
                val savedEntry = advanceArchives.firstOrNull { it.advanceId == advance.id }
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(advance.project.ifBlank { "Advance #${advance.id}" }, fontWeight = FontWeight.Bold)
                        savedEntry?.documentNumber?.takeIf { it.isNotBlank() }?.let { Text("Document $it", fontWeight = FontWeight.Bold) }
                        Text("${dateText(advance.dateEpochDay)} • ${advance.reference.ifBlank { "No reference" }}")
                        Text("Received ${money(advance.amountCents)}")
                        Text("Slips ${money(spent)} • Returned ${money(returned)}")
                        if (advance.settledAt.isNotBlank()) Text("Settled ${displayTimestamp(advance.settledAt)}")
                        if ((savedEntry?.personalTransferredCents ?: 0L) > 0L) {
                            Text("Transferred to Personal Funds ${money(savedEntry!!.personalTransferredCents)}")
                        }
                        Text(
                            if (settled) "SETTLED • R0.00" else "OUTSTANDING • ${money(balance)}",
                            fontWeight = FontWeight.Bold,
                            color = if (settled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (settled) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val existing = advanceArchiveStore.forAdvance(advance.id)
                                        val entry = existing ?: run {
                                            val number = documentNumberStore.nextNumber()
                                            advanceArchiveStore.archiveSettled(advance, slips, returns, number) { reportSlips, reportReturns, docNo ->
                                                PdfExporter.officePack(
                                                    context, advance.project.ifBlank { "Advance #${advance.id}" },
                                                    listOf(advance), reportSlips, reportReturns, emptyList(), docNo
                                                )
                                            } ?: error("Could not create archived advance report")
                                        }
                                        shareFile(context, advanceArchiveStore.reportFile(entry), "application/pdf", "ECI Advance ${entry.documentNumber}")
                                    }.onFailure { Toast.makeText(context, "Could not share archived report: ${it.message}", Toast.LENGTH_LONG).show() }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Share report") }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { returnAdvance = advance }) { Text("Return money") }
                                TextButton(onClick = { repository.archiveAdvance(advance, false) }) { Text("Restore active") }
                            }
                        }
                    }
                }
            }
        }

        if (personalArchives.isEmpty() && archivedAdvances.isEmpty()) {
            item { InfoCard("Archive is empty", "A completed report will appear here automatically when its balance reaches R0.00.") }
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
                val status = when {
                    !slip.isComplete -> "Missing information"
                    slip.syncState == SyncState.SYNCED -> "Synced"
                    slip.syncState == SyncState.FAILED -> "Sync needs attention: ${slip.syncError}"
                    slip.syncState == SyncState.LOCAL_ONLY -> "Stored on this phone"
                    else -> "Saved offline • waiting to sync"
                }
                Text(status, color = if (slip.isComplete && slip.syncState != SyncState.FAILED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            Text(money(slip.totalCents), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditSlipScreen(
    original: Slip,
    advances: List<Advance>,
    cards: List<CompanyCard>,
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
    var selectedCardId by remember(original.id, original.imagePath) { mutableStateOf(original.serverCardId) }
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
            if (selected == PaymentType.OWN || selected == PaymentType.CARD) selectedAdvanceId = null
            if (selected != PaymentType.CARD) selectedCardId = null
        }
        if (paymentType == PaymentType.ADVANCE || paymentType == PaymentType.SPLIT) {
            AdvancePicker(advances, selectedAdvanceId, { selectedAdvanceId = it })
        }
        if (paymentType == PaymentType.CARD) {
            CardPicker(cards, selectedCardId, { selectedCardId = it })
            if (cards.isEmpty()) Text("No company card is currently assigned to you. Connect and refresh first.", color = MaterialTheme.colorScheme.error)
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
                    PaymentType.CARD -> 0L
                    PaymentType.OWN -> totalCents ?: 0L
                    PaymentType.SPLIT -> parseMoney(ownMoney) ?: -1L
                }
                if (totalCents == null || totalCents <= 0) {
                    Toast.makeText(context, "Total is required for reconciliation.", Toast.LENGTH_SHORT).show()
                } else if (paymentType == PaymentType.SPLIT && (ownCents <= 0L || ownCents >= totalCents)) {
                    Toast.makeText(context, "Enter the part of the receipt that you paid yourself.", Toast.LENGTH_LONG).show()
                } else if ((paymentType == PaymentType.ADVANCE || paymentType == PaymentType.SPLIT) && selectedAdvanceId == null) {
                    Toast.makeText(context, "Select the advance that paid the company portion.", Toast.LENGTH_LONG).show()
                } else if (paymentType == PaymentType.CARD && selectedCardId == null) {
                    Toast.makeText(context, "Select the company card used for this purchase.", Toast.LENGTH_LONG).show()
                } else {
                    val selectedAdvance = advances.firstOrNull { it.id == selectedAdvanceId }
                    onSave(
                        original.copy(
                            advanceId = if (paymentType == PaymentType.ADVANCE || paymentType == PaymentType.SPLIT) selectedAdvanceId else null,
                            serverAdvanceId = if (paymentType == PaymentType.ADVANCE || paymentType == PaymentType.SPLIT) selectedAdvance?.serverId else null,
                            serverCardId = if (paymentType == PaymentType.CARD) selectedCardId else null,
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
                            ownMoneyCents = ownCents,
                            syncState = SyncState.PENDING,
                            syncError = ""
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
        PaymentType.CARD -> "Paid with: Company card"
        PaymentType.OWN -> "Paid with: My own money"
        PaymentType.SPLIT -> "Paid with: Split payment"
    }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Company advance") }, onClick = { onSelected(PaymentType.ADVANCE); open = false })
            DropdownMenuItem(text = { Text("Company card") }, onClick = { onSelected(PaymentType.CARD); open = false })
            DropdownMenuItem(text = { Text("My own money") }, onClick = { onSelected(PaymentType.OWN); open = false })
            DropdownMenuItem(text = { Text("Split payment") }, onClick = { onSelected(PaymentType.SPLIT); open = false })
        }
    }
}

@Composable
private fun CardPicker(cards: List<CompanyCard>, selectedId: String?, onSelected: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = cards.firstOrNull { it.serverId == selectedId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.let { "${it.name} • ${money(it.balanceCents)}" } ?: "Select assigned company card")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            cards.forEach { card ->
                DropdownMenuItem(
                    text = { Text("${card.name} • ${money(card.balanceCents)}") },
                    onClick = { onSelected(card.serverId); open = false }
                )
            }
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
private fun ReportsScreen(
    advances: List<Advance>,
    slips: List<Slip>,
    returns: List<MoneyReturn>,
    reimbursements: List<Reimbursement>,
    personalArchiveStore: PersonalReportArchiveStore,
    documentNumberStore: DocumentNumberStore
) {
    val context = LocalContext.current
    val activeAdvances = advances.filterNot { it.archived }
    val activeAdvanceIds = activeAdvances.map { it.id }.toSet()
    val personalSlipFloor = personalArchiveStore.latestSlipId()
    val personalReimbursementFloor = personalArchiveStore.latestReimbursementId()
    var selectedAdvanceId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(activeAdvances) {
        if (selectedAdvanceId != null && activeAdvances.none { it.id == selectedAdvanceId }) selectedAdvanceId = null
    }

    val selectedAdvances = activeAdvances.filter { selectedAdvanceId == null || it.id == selectedAdvanceId }
    val selectedSlips = if (selectedAdvanceId == null) {
        slips.filter { slip ->
            val activeCompanySlip = slip.advanceId != null && slip.advanceId in activeAdvanceIds
            // A rollover split receipt stays attached to the closed advance, but
            // its own-money portion must also remain in the live Personal Funds report.
            val currentOwnMoneySlip = slip.ownMoneyCents > 0L && slip.id > personalSlipFloor
            activeCompanySlip || currentOwnMoneySlip
        }
    } else {
        slips.filter { it.advanceId == selectedAdvanceId }
    }
    val selectedReturns = returns.filter {
        if (selectedAdvanceId == null) it.advanceId in activeAdvanceIds else it.advanceId == selectedAdvanceId
    }
    val selectedReimbursements = if (selectedAdvanceId == null) {
        reimbursements.filter { it.id > personalReimbursementFloor }
    } else emptyList()

    val selectedAdvanceIds = selectedAdvances.map { it.id }.toSet()
    val receivedCents = selectedAdvances.sumOf { it.amountCents }
    val companySlipCents = selectedSlips.filter { it.advanceId != null && it.advanceId in selectedAdvanceIds }.sumOf { it.companyPaidCents }
    val returnedCents = selectedReturns.sumOf { it.amountCents }
    val outstandingCents = receivedCents - companySlipCents - returnedCents
    val currentOwnMoney = selectedSlips.sumOf { it.ownMoneyCents }
    val currentReimbursed = selectedReimbursements.sumOf { it.amountCents }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdvancePicker(activeAdvances, selectedAdvanceId, { selectedAdvanceId = it })
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Reconciliation", fontWeight = FontWeight.Bold)
                Text("Received ${money(receivedCents)}")
                Text("Company-funded slips ${money(companySlipCents)}")
                Text("Returned ${money(returnedCents)}")
                Text("Outstanding advance ${money(outstandingCents)}", fontWeight = FontWeight.Bold)
                if (selectedAdvanceId == null && currentOwnMoney > 0L) {
                    Spacer(Modifier.height(4.dp))
                    Text("Personal funds used ${money(currentOwnMoney)}")
                    Text("Personal funds reimbursed ${money(currentReimbursed)}")
                    Text("ECI owes ${money((currentOwnMoney - currentReimbursed).coerceAtLeast(0L))}", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (selectedSlips.isEmpty()) {
            InfoCard("Nothing to export", "Capture a current slip before creating PDFs. Completed reports are available from Archive.")
        } else {
            Button(
                onClick = {
                    runCatching {
                        val documentNumber = documentNumberStore.nextNumber()
                        val label = selectedAdvanceId?.let { id -> activeAdvances.firstOrNull { it.id == id }?.project?.ifBlank { "Advance #$id" } } ?: "Current report"
                        val file = PdfExporter.officePack(
                            context, label, selectedAdvances, selectedSlips, selectedReturns, selectedReimbursements, documentNumber
                        )
                        shareFile(context, file, "application/pdf", "ECI Office Pack $documentNumber")
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
    var documentPrefix by remember { mutableStateOf(prefs.getString("document_prefix", DocumentNumberStore.DEFAULT_PREFIX) ?: DocumentNumberStore.DEFAULT_PREFIX) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var clearActive by remember { mutableStateOf(false) }
    var clearArchived by remember { mutableStateOf(false) }
    var clearSlips by remember { mutableStateOf(false) }
    var clearReturns by remember { mutableStateOf(false) }
    var clearOwnMoney by remember { mutableStateOf(false) }
    var clearReimbursements by remember { mutableStateOf(false) }
    var clearSettings by remember { mutableStateOf(false) }
    val session by repository.session.collectAsState()
    val serverMessage by repository.serverMessage.collectAsState()
    val pendingCount = slips.count { it.syncState != SyncState.SYNCED && it.syncState != SyncState.LOCAL_ONLY }

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
        SectionTitle("ECI server")
        Text("Signed in as ${session?.displayName ?: "Unknown"} • ${session?.email.orEmpty()}")
        Text(if (pendingCount == 0) "All receipts synced" else "$pendingCount receipt(s) waiting to sync")
        if (serverMessage.isNotBlank()) Text(serverMessage, style = MaterialTheme.typography.bodySmall)
        Button(onClick = repository::syncNow, modifier = Modifier.fillMaxWidth()) { Text("Sync now") }
        OutlinedButton(onClick = repository::refreshFunding, modifier = Modifier.fillMaxWidth()) { Text("Refresh advances and cards") }
        TextButton(onClick = repository::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out from this phone") }
        Spacer(Modifier.height(8.dp))
        SectionTitle("Report identity")
        Text("These details appear on the accountant Office Pack.", style = MaterialTheme.typography.bodySmall)
        TextFieldSimple("User / Submitted by *", name, { name = it })
        TextFieldSimple("Document prefix", documentPrefix, { documentPrefix = it })
        Text("Next reports use ${DocumentNumberStore.normalizePrefix(documentPrefix)}-YYYYMMDD-N. Archived reports keep their original number.", style = MaterialTheme.typography.bodySmall)

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
                .putString("document_prefix", DocumentNumberStore.normalizePrefix(documentPrefix))
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
        InfoCard("Offline storage", "The scanner, OCR, database and receipt images stay on this Android phone. Completed receipts queue safely and sync to the ECI VPS whenever a connection is available.")
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
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(clearSettings, { clearSettings = it }); Text("Report identity, company details, document prefix, logo and email settings") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val any = clearActive || clearArchived || clearSlips || clearReturns || clearOwnMoney || clearReimbursements || clearSettings
                    if (!any) {
                        Toast.makeText(context, "Select at least one item to clear.", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.clearSelectedData(clearActive, clearArchived, clearSlips, clearReturns, clearOwnMoney, clearReimbursements)
                        if (clearSlips || clearOwnMoney || clearReimbursements) PersonalReportArchiveStore(context).clear()
                        if (clearActive || clearArchived || clearSlips || clearReturns || clearOwnMoney) AdvanceReportArchiveStore(context).clear()
                        if (clearSettings) {
                            prefs.edit().clear().apply()
                            runCatching { if (logoPath.isNotBlank()) File(logoPath).delete() }
                            name = "Dave"; company = "ECI Automation"; companyRegistration = ""; companyVat = ""
                            companyPhone = ""; companyEmail = ""; companyAddress = ""; logoPath = ""
                            officeEmail = ""; dextEmail = ""; documentPrefix = DocumentNumberStore.DEFAULT_PREFIX
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

private fun launchPreparedReportEmail(
    context: android.content.Context,
    file: File,
    documentNumber: String,
    reportName: String
) {
    val prefs = context.getSharedPreferences("settings", 0)
    val officeEmail = prefs.getString("office_email", "")?.trim().orEmpty()
    if (officeEmail.isBlank()) {
        Toast.makeText(context, "$documentNumber archived. Add an Office email in Settings to prepare email automatically.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_EMAIL, arrayOf(officeEmail))
        putExtra(Intent.EXTRA_SUBJECT, "$reportName - $documentNumber")
        putExtra(Intent.EXTRA_TEXT, "Please find attached $reportName $documentNumber generated by ECI Slip Manager.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Send $documentNumber"))
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
private val displayDateTime = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", zaLocale)

private fun money(cents: Long): String = currencyFormat.format(cents / 100.0)
private fun plainMoney(cents: Long): String = String.format(Locale.US, "%.2f", cents / 100.0)
private fun dateText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(displayDate)
private fun displayTimestamp(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(displayDateTime)
}.getOrElse { value.take(16).replace('T', ' ') }

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
