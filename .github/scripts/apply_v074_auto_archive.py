from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


# --- SlipApp.kt -------------------------------------------------------------
path = Path("app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt")
text = path.read_text()

text = replace_once(
    text,
    "import za.co.eci.slipmanager.data.PaymentType\n",
    "import za.co.eci.slipmanager.data.PaymentType\nimport za.co.eci.slipmanager.data.PersonalFundsSummary\nimport za.co.eci.slipmanager.data.PersonalReportArchiveStore\n",
    "SlipApp imports",
)

text = replace_once(
    text,
    "    val reimbursements by repository.reimbursements.collectAsState()\n\n    var screen by remember { mutableStateOf(Screen.HOME) }",
    "    val reimbursements by repository.reimbursements.collectAsState()\n    val personalArchiveStore = remember { PersonalReportArchiveStore(context) }\n    var personalArchiveRevision by remember { mutableLongStateOf(0L) }\n    val personalSummary = remember(slips, reimbursements, personalArchiveRevision) {\n        personalArchiveStore.currentSummary(slips, reimbursements)\n    }\n\n    var screen by remember { mutableStateOf(Screen.HOME) }",
    "SlipApp archive state",
)

text = replace_once(
    text,
    "    BackHandler {\n",
    """    LaunchedEffect(advances, slips, returns) {
        advances.filterNot { it.archived }.forEach { advance ->
            val hasActivity = slips.any { it.advanceId == advance.id } || returns.any { it.advanceId == advance.id }
            if (hasActivity && repository.reconciliation(advance.id).outstandingCents == 0L) {
                repository.archiveAdvance(advance, true)
            }
        }
    }

    LaunchedEffect(slips, reimbursements) {
        val livePersonal = personalArchiveStore.currentSummary(slips, reimbursements)
        if (livePersonal.usedCents > 0L && livePersonal.outstandingCents == 0L) {
            runCatching {
                personalArchiveStore.closeIfSettled(slips, reimbursements) { settlementSlips, settlementReimbursements ->
                    PdfExporter.officePack(
                        context,
                        "Own money settlement",
                        emptyList(),
                        settlementSlips,
                        emptyList(),
                        settlementReimbursements
                    )
                }
            }.onSuccess { archived ->
                if (archived != null) {
                    personalArchiveRevision++
                    Toast.makeText(context, "Own-money report settled and moved to Archive", Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                Toast.makeText(context, "Could not archive settled own-money report: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler {
""",
    "SlipApp auto archive effects",
)

text = replace_once(
    text,
    "                    slips = slips,\n                    onAdvances = { screen = Screen.ADVANCES },",
    "                    slips = slips,\n                    personalSummary = personalSummary,\n                    onAdvances = { screen = Screen.ADVANCES },",
    "HomeScreen call",
)

text = replace_once(
    text,
    "                Screen.REPORTS -> ReportsScreen(repository, advances, slips, returns, reimbursements)\n                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns)\n",
    "                Screen.REPORTS -> ReportsScreen(advances, slips, returns, reimbursements, personalArchiveStore)\n                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns, reimbursements, personalArchiveStore, personalArchiveRevision)\n",
    "Reports/Archive calls",
)

text = replace_once(
    text,
    "    advances: List<Advance>,\n    slips: List<Slip>,\n    onAdvances: () -> Unit,",
    "    advances: List<Advance>,\n    slips: List<Slip>,\n    personalSummary: PersonalFundsSummary,\n    onAdvances: () -> Unit,",
    "HomeScreen signature",
)

text = replace_once(
    text,
    "    val personal = repository.personalFundsSummary()\n",
    "    val personal = personalSummary\n",
    "Home personal summary",
)

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
    archiveRevision: Long
) {
    val context = LocalContext.current
    val personalArchives = remember(slips, reimbursements, archiveRevision) { personalArchiveStore.entries() }
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
                "Settled advances and fully reimbursed own-money reports move here automatically. Use Share report whenever you need to resend a completed Office Pack."
            )
        }

        if (personalArchives.isNotEmpty()) {
            item { SectionTitle("Own-money settlements") }
            items(personalArchives, key = { "personal_${it.id}" }) { archived ->
                val closedDate = java.time.Instant.ofEpochMilli(archived.closedAtMillis)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(displayDate)
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Own money settlement", fontWeight = FontWeight.Bold)
                        Text("Closed $closedDate")
                        Text("Used ${money(archived.usedCents)}")
                        Text("Reimbursed ${money(archived.reimbursedCents)}")
                        Text("SETTLED • R0.00", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val saved = personalArchiveStore.reportFile(archived)
                                    val report = if (saved.exists()) saved else {
                                        PdfExporter.officePack(
                                            context,
                                            "Own money settlement",
                                            emptyList(),
                                            personalArchiveStore.slipsFor(archived, slips),
                                            emptyList(),
                                            personalArchiveStore.reimbursementsFor(archived, reimbursements)
                                        )
                                    }
                                    shareFile(context, report, "application/pdf", "ECI Own Money Settlement")
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
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val label = advance.project.ifBlank { "Advance #${advance.id}" }
                                    val report = PdfExporter.officePack(
                                        context, label, listOf(advance), advanceSlips, advanceReturns, emptyList()
                                    )
                                    shareFile(context, report, "application/pdf", "ECI Office Pack")
                                }.onFailure { Toast.makeText(context, "Could not share archived report: ${it.message}", Toast.LENGTH_LONG).show() }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share report") }
                        if (balance != 0L) {
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

reports_start = text.index("@Composable\nprivate fun ReportsScreen(")
reports_end = text.index("@Composable\nprivate fun SettingsScreen", reports_start)
new_reports = r'''@Composable
private fun ReportsScreen(
    advances: List<Advance>,
    slips: List<Slip>,
    returns: List<MoneyReturn>,
    reimbursements: List<Reimbursement>,
    personalArchiveStore: PersonalReportArchiveStore
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
            val currentOwnMoneySlip = slip.advanceId == null && slip.ownMoneyCents > 0L && slip.id > personalSlipFloor
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdvancePicker(activeAdvances, selectedAdvanceId, { selectedAdvanceId = it })
        Card {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text("Reconciliation", fontWeight = FontWeight.Bold)
                Text("Received ${money(receivedCents)}")
                Text("Slips ${money(companySlipCents)}")
                Text("Returned ${money(returnedCents)}")
                Text("Outstanding ${money(outstandingCents)}", fontWeight = FontWeight.Bold)
            }
        }
        if (selectedSlips.isEmpty()) {
            InfoCard("Nothing to export", "Capture a current slip before creating PDFs. Completed reports are available from Archive.")
        } else {
            Button(
                onClick = {
                    runCatching {
                        val label = selectedAdvanceId?.let { id -> activeAdvances.firstOrNull { it.id == id }?.project?.ifBlank { "Advance #$id" } } ?: "Current report"
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

'''
text = text[:reports_start] + new_reports + text[reports_end:]

text = replace_once(
    text,
    "                        repository.clearSelectedData(clearActive, clearArchived, clearSlips, clearReturns, clearOwnMoney, clearReimbursements)\n",
    "                        repository.clearSelectedData(clearActive, clearArchived, clearSlips, clearReturns, clearOwnMoney, clearReimbursements)\n                        if (clearSlips || clearOwnMoney || clearReimbursements) PersonalReportArchiveStore(context).clear()\n",
    "Settings clear archive",
)

text = text.replace(
    "Version 0.7.2 stores the database, original receipt images and report branding privately on this Android phone.",
    "Version 0.7.4 stores the database, receipt images, completed report archive and report branding privately on this Android phone.",
)

path.write_text(text)


# --- BackupManager.kt -------------------------------------------------------
path = Path("app/src/main/java/za/co/eci/slipmanager/backup/BackupManager.kt")
text = path.read_text()
text = replace_once(
    text,
    "import za.co.eci.slipmanager.data.PaymentType\n",
    "import za.co.eci.slipmanager.data.PaymentType\nimport za.co.eci.slipmanager.data.PersonalReportArchiveStore\n",
    "Backup import",
)
text = text.replace('put("version", 3)', 'put("version", 4)', 1)

backup_anchor = """            slips.forEach { slip ->
                val src = File(slip.imagePath)
                if (src.exists()) {
                    zip.putNextEntry(ZipEntry("receipts/${src.name}"))
                    src.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
"""
backup_extra = backup_anchor + """            val archiveMetadata = File(context.filesDir, PersonalReportArchiveStore.METADATA_FILE_NAME)
            if (archiveMetadata.exists()) {
                zip.putNextEntry(ZipEntry("report_archive/${PersonalReportArchiveStore.METADATA_FILE_NAME}"))
                archiveMetadata.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val archivedReports = File(context.filesDir, PersonalReportArchiveStore.REPORTS_DIR_NAME)
            archivedReports.listFiles()?.filter { it.isFile }?.forEach { report ->
                zip.putNextEntry(ZipEntry("report_archive/reports/${report.name}"))
                report.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
"""
text = replace_once(text, backup_anchor, backup_extra, "Backup archive files")

restore_anchor = """            val slips = originalSlips.map { slip ->
                val oldName = File(slip.imagePath).name
                val entry = zip.getEntry("receipts/$oldName")
                if (entry != null) {
                    val target = File(receiptsDir, oldName)
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    slip.copy(imagePath = target.absolutePath)
                } else slip
            }
            return Restored(advances, slips, returns, reimbursements)
"""
restore_new = """            val slips = originalSlips.map { slip ->
                val oldName = File(slip.imagePath).name
                val entry = zip.getEntry("receipts/$oldName")
                if (entry != null) {
                    val target = File(receiptsDir, oldName)
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    slip.copy(imagePath = target.absolutePath)
                } else slip
            }

            val archiveMetadata = File(context.filesDir, PersonalReportArchiveStore.METADATA_FILE_NAME)
            val archiveReportsDir = File(context.filesDir, PersonalReportArchiveStore.REPORTS_DIR_NAME)
            archiveMetadata.delete()
            archiveReportsDir.deleteRecursively()
            archiveReportsDir.mkdirs()
            zip.getEntry("report_archive/${PersonalReportArchiveStore.METADATA_FILE_NAME}")?.let { entry ->
                zip.getInputStream(entry).use { input -> archiveMetadata.outputStream().use { input.copyTo(it) } }
            }
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("report_archive/reports/") }
                .forEach { entry ->
                    val target = File(archiveReportsDir, File(entry.name).name)
                    zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                }

            return Restored(advances, slips, returns, reimbursements)
"""
text = replace_once(text, restore_anchor, restore_new, "Restore archive files")
path.write_text(text)


# --- build.gradle.kts -------------------------------------------------------
path = Path("app/build.gradle.kts")
text = path.read_text()
text = text.replace("// v0.7.3 B&W document scanner release", "// v0.7.4 automatic completed-report archive", 1)
text = text.replace("versionCode = 13", "versionCode = 14", 1)
text = text.replace('versionName = "0.7.3"', 'versionName = "0.7.4"', 1)
path.write_text(text)

print("Applied v0.7.4 automatic report archive upgrade")
