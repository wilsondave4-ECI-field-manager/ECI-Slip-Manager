from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    new, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 regex match, found {count}")
    return new


# ---------------------------------------------------------------------------
# SlipApp.kt
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt")
text = path.read_text()

text = replace_once(
    text,
    "import za.co.eci.slipmanager.data.Advance\n",
    "import za.co.eci.slipmanager.data.Advance\nimport za.co.eci.slipmanager.data.AdvanceReportArchiveStore\nimport za.co.eci.slipmanager.data.DocumentNumberStore\n",
    "SlipApp data imports",
)

text = replace_once(
    text,
    "    val personalArchiveStore = remember { PersonalReportArchiveStore(context) }\n    var personalArchiveRevision by remember { mutableLongStateOf(0L) }",
    "    val personalArchiveStore = remember { PersonalReportArchiveStore(context) }\n    val advanceArchiveStore = remember { AdvanceReportArchiveStore(context) }\n    val documentNumberStore = remember { DocumentNumberStore(context) }\n    var personalArchiveRevision by remember { mutableLongStateOf(0L) }",
    "SlipApp stores",
)

new_effects = r'''    LaunchedEffect(advances, slips, returns) {
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

    BackHandler'''

text = regex_once(
    text,
    r"    LaunchedEffect\(advances, slips, returns\) \{.*?\n    BackHandler",
    new_effects,
    "SlipApp archive effects",
)

text = replace_once(
    text,
    "                Screen.REPORTS -> ReportsScreen(advances, slips, returns, reimbursements, personalArchiveStore)\n                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns, reimbursements, personalArchiveStore, personalArchiveRevision)",
    "                Screen.REPORTS -> ReportsScreen(advances, slips, returns, reimbursements, personalArchiveStore, documentNumberStore)\n                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns, reimbursements, personalArchiveStore, advanceArchiveStore, documentNumberStore, personalArchiveRevision)",
    "SlipApp report/archive calls",
)

old_save = '''                        onSave = {
                            repository.saveSlip(it)
                            editingSlip = null
                            screen = Screen.SLIPS
                        },'''
new_save = '''                        onSave = { candidate ->
                            var adjusted = candidate
                            var movedToPersonal = 0L
                            val advanceId = candidate.advanceId
                            if (advanceId != null) {
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
                        },'''
text = replace_once(text, old_save, new_save, "SlipApp save rollover")

# Replace ArchiveScreen as one block.
archive_start = text.index("@Composable\nprivate fun ArchiveScreen(")
archive_end = text.index("@Composable\nprivate fun SlipsScreen", archive_start)
new_archive = r'''@Composable
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
                val spent = advanceSlips.sumOf { it.companyPaidCents }
                val returned = advanceReturns.sumOf { it.amountCents }
                val balance = advance.amountCents - spent - returned
                val savedEntry = advanceArchives.firstOrNull { it.advanceId == advance.id }
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(advance.project.ifBlank { "Advance #${advance.id}" }, fontWeight = FontWeight.Bold)
                        savedEntry?.documentNumber?.takeIf { it.isNotBlank() }?.let { Text("Document $it", fontWeight = FontWeight.Bold) }
                        Text("${dateText(advance.dateEpochDay)} • ${advance.reference.ifBlank { "No reference" }}")
                        Text("Received ${money(advance.amountCents)}")
                        Text("Slips ${money(spent)} • Returned ${money(returned)}")
                        if ((savedEntry?.personalTransferredCents ?: 0L) > 0L) {
                            Text("Transferred to Personal Funds ${money(savedEntry!!.personalTransferredCents)}")
                        }
                        Text(
                            if (balance == 0L) "SETTLED • R0.00" else "OUTSTANDING • ${money(balance)}",
                            fontWeight = FontWeight.Bold,
                            color = if (balance == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (balance == 0L) {
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

'''
text = text[:archive_start] + new_archive + text[archive_end:]

# Replace ReportsScreen as one block.
reports_start = text.index("@Composable\nprivate fun ReportsScreen(")
reports_end = text.index("@Composable\nprivate fun SettingsScreen", reports_start)
new_reports = r'''@Composable
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

'''
text = text[:reports_start] + new_reports + text[reports_end:]

# Settings: prefix state, field, save and reset.
text = replace_once(
    text,
    '    var dextEmail by remember { mutableStateOf(prefs.getString("dext_email", "") ?: "") }\n',
    '    var dextEmail by remember { mutableStateOf(prefs.getString("dext_email", "") ?: "") }\n    var documentPrefix by remember { mutableStateOf(prefs.getString("document_prefix", DocumentNumberStore.DEFAULT_PREFIX) ?: DocumentNumberStore.DEFAULT_PREFIX) }\n',
    "Settings prefix state",
)
text = replace_once(
    text,
    '        TextFieldSimple("User / Submitted by *", name, { name = it })\n\n        Spacer(Modifier.height(6.dp)); SectionTitle("Company details")',
    '        TextFieldSimple("User / Submitted by *", name, { name = it })\n        TextFieldSimple("Document prefix", documentPrefix, { documentPrefix = it })\n        Text("Next reports use ${DocumentNumberStore.normalizePrefix(documentPrefix)}-YYYYMMDD-N. Archived reports keep their original number.", style = MaterialTheme.typography.bodySmall)\n\n        Spacer(Modifier.height(6.dp)); SectionTitle("Company details")',
    "Settings prefix field",
)
text = replace_once(
    text,
    '                .putString("dext_email", dextEmail.trim())\n                .apply()',
    '                .putString("dext_email", dextEmail.trim())\n                .putString("document_prefix", DocumentNumberStore.normalizePrefix(documentPrefix))\n                .apply()',
    "Settings save prefix",
)
text = text.replace(
    'InfoCard("Storage", "Version 0.7.4 stores the database, receipt images, completed report archive and report branding privately on this Android phone.',
    'InfoCard("Storage", "Version 0.7.5 stores the database, receipt images, completed report archive, document numbering and report branding privately on this Android phone.',
)
text = replace_once(
    text,
    '                            officeEmail = ""; dextEmail = ""\n',
    '                            officeEmail = ""; dextEmail = ""; documentPrefix = DocumentNumberStore.DEFAULT_PREFIX\n',
    "Settings clear prefix",
)
text = text.replace(
    'Text("Report identity, company details, logo and email settings")',
    'Text("Report identity, company details, document prefix, logo and email settings")',
)

# Clear permanent report snapshots when corresponding source data is cleared.
text = replace_once(
    text,
    '                        if (clearSlips || clearOwnMoney || clearReimbursements) PersonalReportArchiveStore(context).clear()\n',
    '                        if (clearSlips || clearOwnMoney || clearReimbursements) PersonalReportArchiveStore(context).clear()\n                        if (clearActive || clearArchived || clearSlips || clearReturns || clearOwnMoney) AdvanceReportArchiveStore(context).clear()\n',
    "Settings clear advance archive",
)

# Add prepared-email helper before generic sharing helpers.
email_helper = r'''private fun launchPreparedReportEmail(
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

'''
text = replace_once(
    text,
    'private fun shareFile(context: android.content.Context, file: File, mime: String, subject: String) {',
    email_helper + 'private fun shareFile(context: android.content.Context, file: File, mime: String, subject: String) {',
    "Email helper",
)

path.write_text(text)


# ---------------------------------------------------------------------------
# PdfExporter.kt
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/za/co/eci/slipmanager/pdf/PdfExporter.kt")
text = path.read_text()
text = replace_once(
    text,
    '        reimbursements: List<Reimbursement> = emptyList()\n    ): File {\n        val dir = File(context.cacheDir, "exports").apply { mkdirs() }\n        val file = File(dir, "ECI_Office_Pack_${System.currentTimeMillis()}.pdf")',
    '        reimbursements: List<Reimbursement> = emptyList(),\n        documentNumber: String? = null\n    ): File {\n        val dir = File(context.cacheDir, "exports").apply { mkdirs() }\n        val safeDocument = documentNumber?.replace(Regex("[^A-Za-z0-9_-]"), "_")\n        val file = File(dir, if (safeDocument.isNullOrBlank()) "ECI_Office_Pack_${System.currentTimeMillis()}.pdf" else "${safeDocument}_Office_Pack.pdf")',
    "Pdf officePack signature",
)
text = text.replace(
    'drawProfessionalHeader(page.canvas, identity, logo, title, generated, currentPageNo)',
    'drawProfessionalHeader(page.canvas, identity, logo, title, generated, currentPageNo, documentNumber)',
)
text = replace_once(
    text,
    '            drawProfessionalReceiptPage(receiptPage.canvas, bitmap, slip, identity, logo, title, generated, receiptPageNo, index + 1, slips.size)',
    '            drawProfessionalReceiptPage(receiptPage.canvas, bitmap, slip, identity, logo, title, generated, receiptPageNo, index + 1, slips.size, documentNumber)',
    "Pdf receipt call",
)

# Advance rollover note after reconciliation totals.
marker = '''        ).forEach {
            canvasOf(page).drawText(it, 36f, y, body)
            y += 18f
        }

        y += 4f'''
replacement = '''        ).forEach {
            canvasOf(page).drawText(it, 36f, y, body)
            y += 18f
        }
        if (advances.isNotEmpty() && outstanding == 0L && personalUsed > 0L) {
            canvasOf(page).drawText("Personal funds transferred for reimbursement: ${fmt(personalUsed)}", 36f, y, sub)
            y += 16f
            canvasOf(page).drawText("The personal-funded portion is carried forward in the open Personal Funds settlement.", 36f, y, bodySmall)
            y += 18f
        }

        y += 4f'''
text = replace_once(text, marker, replacement, "Pdf rollover note")

# Header signature + document number display.
text = replace_once(
    text,
    '        generated: String,\n        pageNumber: Int\n    ): Float {',
    '        generated: String,\n        pageNumber: Int,\n        documentNumber: String?\n    ): Float {',
    "Pdf header signature",
)
text = replace_once(
    text,
    '        canvas.drawText("Report: ${shorten(reportLabel, 58)}", 36f, 126f, meta)\n        canvas.drawText("Submitted by: ${shorten(identity.submittedBy, 42)}", 36f, 140f, meta)',
    '        canvas.drawText("Report: ${shorten(reportLabel, 58)}", 36f, 126f, meta)\n        if (!documentNumber.isNullOrBlank()) drawRightText(canvas, "Document: $documentNumber", PAGE_W - 36f, 126f, meta)\n        canvas.drawText("Submitted by: ${shorten(identity.submittedBy, 42)}", 36f, 140f, meta)',
    "Pdf header document number",
)

# Receipt header signature + funding line.
text = replace_once(
    text,
    '        pageNumber: Int,\n        receiptIndex: Int,\n        receiptCount: Int\n    ) {',
    '        pageNumber: Int,\n        receiptIndex: Int,\n        receiptCount: Int,\n        documentNumber: String?\n    ) {',
    "Pdf receipt signature",
)
text = replace_once(
    text,
    '        drawRightText(canvas, "Receipt $receiptIndex of $receiptCount | Page $pageNumber", PAGE_W - 36f, 42f, small)\n        canvas.drawLine(36f, 64f, PAGE_W - 36f, 64f, divider)',
    '        drawRightText(canvas, "Receipt $receiptIndex of $receiptCount | Page $pageNumber", PAGE_W - 36f, 42f, small)\n        if (!documentNumber.isNullOrBlank()) drawRightText(canvas, "Document $documentNumber", PAGE_W - 36f, 54f, small)\n        canvas.drawLine(36f, 64f, PAGE_W - 36f, 64f, divider)',
    "Pdf receipt document number",
)
text = replace_once(
    text,
    '        canvas.drawText("Ex VAT $exVatText | VAT $vatText | Total ${fmt(slip.totalCents)}", 36f, 116f, body)\n        drawRightText(canvas, "Generated $generated", PAGE_W - 36f, 100f, small)\n        canvas.drawLine(36f, 126f, PAGE_W - 36f, 126f, divider)\n\n        val top = 140f',
    '''        canvas.drawText("Ex VAT $exVatText | VAT $vatText | Total ${fmt(slip.totalCents)}", 36f, 116f, body)
        val funding = when (slip.paymentType) {
            PaymentType.ADVANCE -> "Company advance ${fmt(slip.companyPaidCents)}"
            PaymentType.OWN -> "Personal funds ${fmt(slip.ownMoneyCents)}"
            PaymentType.SPLIT -> "Company ${fmt(slip.companyPaidCents)} + Personal ${fmt(slip.ownMoneyCents)}"
        }
        canvas.drawText("Funding: $funding", 36f, 131f, body)
        drawRightText(canvas, "Generated $generated", PAGE_W - 36f, 100f, small)
        canvas.drawLine(36f, 141f, PAGE_W - 36f, 141f, divider)

        val top = 154f''',
    "Pdf receipt funding",
)

path.write_text(text)


# ---------------------------------------------------------------------------
# BackupManager.kt
# ---------------------------------------------------------------------------
path = Path("app/src/main/java/za/co/eci/slipmanager/backup/BackupManager.kt")
text = path.read_text()
text = replace_once(
    text,
    'import za.co.eci.slipmanager.data.Advance\n',
    'import za.co.eci.slipmanager.data.Advance\nimport za.co.eci.slipmanager.data.AdvanceReportArchiveStore\nimport za.co.eci.slipmanager.data.DocumentNumberStore\n',
    "Backup imports",
)
text = text.replace('put("version", 4)', 'put("version", 5)')

backup_insert = '''            val advanceMetadata = File(context.filesDir, AdvanceReportArchiveStore.METADATA_FILE_NAME)
            if (advanceMetadata.exists()) {
                zip.putNextEntry(ZipEntry("report_archive/${AdvanceReportArchiveStore.METADATA_FILE_NAME}"))
                advanceMetadata.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val advanceReports = File(context.filesDir, AdvanceReportArchiveStore.REPORTS_DIR_NAME)
            advanceReports.listFiles()?.filter { it.isFile }?.forEach { report ->
                zip.putNextEntry(ZipEntry("report_archive/advance_reports/${report.name}"))
                report.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val documentCounters = File(context.filesDir, DocumentNumberStore.FILE_NAME)
            if (documentCounters.exists()) {
                zip.putNextEntry(ZipEntry("report_archive/${DocumentNumberStore.FILE_NAME}"))
                documentCounters.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
'''
needle = '''            val archivedReports = File(context.filesDir, PersonalReportArchiveStore.REPORTS_DIR_NAME)
            archivedReports.listFiles()?.filter { it.isFile }?.forEach { report ->
                zip.putNextEntry(ZipEntry("report_archive/reports/${report.name}"))
                report.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
'''
text = replace_once(text, needle, needle + backup_insert, "Backup archive insert")

restore_insert = '''
            val advanceMetadata = File(context.filesDir, AdvanceReportArchiveStore.METADATA_FILE_NAME)
            val advanceReportsDir = File(context.filesDir, AdvanceReportArchiveStore.REPORTS_DIR_NAME)
            advanceMetadata.delete()
            advanceReportsDir.deleteRecursively()
            advanceReportsDir.mkdirs()
            zip.getEntry("report_archive/${AdvanceReportArchiveStore.METADATA_FILE_NAME}")?.let { entry ->
                zip.getInputStream(entry).use { input -> advanceMetadata.outputStream().use { input.copyTo(it) } }
            }
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("report_archive/advance_reports/") }
                .forEach { entry ->
                    val target = File(advanceReportsDir, File(entry.name).name)
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }

            // Never reset counters when restoring an older backup that did not
            // contain them; preserving the current counter avoids duplicate
            // accounting document numbers.
            zip.getEntry("report_archive/${DocumentNumberStore.FILE_NAME}")?.let { entry ->
                val counterFile = File(context.filesDir, DocumentNumberStore.FILE_NAME)
                zip.getInputStream(entry).use { input -> counterFile.outputStream().use { input.copyTo(it) } }
            }
'''
needle_restore = '''            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("report_archive/reports/") }
                .forEach { entry ->
                    val target = File(archiveReportsDir, File(entry.name).name)
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }

            return Restored(advances, slips, returns, reimbursements)
'''
text = replace_once(
    text,
    needle_restore,
    needle_restore.replace('\n            return Restored(advances, slips, returns, reimbursements)\n', restore_insert + '\n            return Restored(advances, slips, returns, reimbursements)\n'),
    "Backup restore insert",
)
path.write_text(text)


# ---------------------------------------------------------------------------
# build.gradle.kts
# ---------------------------------------------------------------------------
path = Path("app/build.gradle.kts")
text = path.read_text()
text = text.replace('versionCode = 14', 'versionCode = 15')
text = text.replace('versionName = "0.7.4"', 'versionName = "0.7.5"')
text = text.replace('// v0.7.3 B&W document scanner final verification', '// v0.7.5 advance rollover, permanent reports and document numbering')
path.write_text(text)

print("Applied v0.7.5 rollover/document-number changes")
