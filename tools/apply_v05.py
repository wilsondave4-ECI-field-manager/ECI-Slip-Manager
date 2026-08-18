from pathlib import Path

root = Path(__file__).resolve().parents[1]
ui_path = root / "app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt"
gradle_path = root / "app/build.gradle.kts"
ui = ui_path.read_text()


def rep(old: str, new: str):
    global ui
    if old not in ui:
        raise SystemExit(f"Pattern not found: {old[:100]!r}")
    ui = ui.replace(old, new, 1)

rep(
    "import androidx.compose.material.icons.filled.Add\n",
    "import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.Archive\n"
)
rep(
    "private enum class Screen { HOME, ADVANCES, SLIPS, REPORTS, SETTINGS, EDIT_SLIP }",
    "private enum class Screen { HOME, ADVANCES, SLIPS, REPORTS, ARCHIVE, SETTINGS, EDIT_SLIP }"
)
rep(
    '                    Screen.REPORTS -> "Reports & Export"\n                    Screen.SETTINGS -> "Settings"',
    '                    Screen.REPORTS -> "Reports & Export"\n                    Screen.ARCHIVE -> "Archive"\n                    Screen.SETTINGS -> "Settings"'
)
rep(
    "            if (screen in setOf(Screen.HOME, Screen.SLIPS, Screen.REPORTS)) {",
    "            if (screen in setOf(Screen.HOME, Screen.SLIPS, Screen.REPORTS, Screen.ARCHIVE)) {"
)
rep(
    "                        advances = advances,\n                        onSave = {",
    "                        advances = advances.filter { !it.archived || repository.reconciliation(it.id).outstandingCents != 0L },\n                        onSave = {"
)
rep(
    "                Screen.REPORTS -> ReportsScreen(repository, advances, slips, returns)\n                Screen.SETTINGS -> SettingsScreen(repository, advances, slips, returns)",
    "                Screen.REPORTS -> ReportsScreen(repository, advances, slips, returns)\n                Screen.ARCHIVE -> ArchiveScreen(repository, advances, slips, returns)\n                Screen.SETTINGS -> SettingsScreen(repository, advances, slips, returns)"
)

# Bottom navigation: add Archive at far right.
rep(
    '''        NavigationBarItem(\n            selected = screen == Screen.REPORTS,\n            onClick = { onNavigate(Screen.REPORTS) },\n            icon = { Icon(Icons.Default.Description, null) }, label = { Text("Reports") }\n        )\n    }\n}''',
    '''        NavigationBarItem(\n            selected = screen == Screen.REPORTS,\n            onClick = { onNavigate(Screen.REPORTS) },\n            icon = { Icon(Icons.Default.Description, null) }, label = { Text("Reports") }\n        )\n        NavigationBarItem(\n            selected = screen == Screen.ARCHIVE,\n            onClick = { onNavigate(Screen.ARCHIVE) },\n            icon = { Icon(Icons.Default.Archive, null) }, label = { Text("Archive") }\n        )\n    }\n}'''
)

# Home only shows active advances plus archived advances that still have a non-zero balance.
rep(
    '''    var selectedAdvanceId by remember { mutableStateOf<Long?>(null) }\n    LaunchedEffect(advances) {\n        val newest = advances.firstOrNull()?.id\n        if (selectedAdvanceId == null || advances.none { it.id == selectedAdvanceId }) selectedAdvanceId = newest\n    }\n    val selectedAdvance = advances.firstOrNull { it.id == selectedAdvanceId }\n    val rec = selectedAdvanceId?.let { repository.reconciliation(it) } ?: repository.reconciliation()\n    val visibleSlips = if (selectedAdvanceId == null) slips else slips.filter { it.advanceId == selectedAdvanceId }''',
    '''    val dashboardAdvances = advances.filter { advance ->\n        !advance.archived || repository.reconciliation(advance.id).outstandingCents != 0L\n    }\n    var selectedAdvanceId by remember { mutableStateOf<Long?>(null) }\n    LaunchedEffect(dashboardAdvances) {\n        val newest = dashboardAdvances.firstOrNull { !it.archived }?.id ?: dashboardAdvances.firstOrNull()?.id\n        if (selectedAdvanceId == null || dashboardAdvances.none { it.id == selectedAdvanceId }) selectedAdvanceId = newest\n    }\n    val selectedAdvance = dashboardAdvances.firstOrNull { it.id == selectedAdvanceId }\n    val rec = selectedAdvanceId?.let { repository.reconciliation(it) } ?: repository.reconciliation()\n    val visibleSlips = if (selectedAdvanceId == null) slips else slips.filter { it.advanceId == selectedAdvanceId }'''
)
rep(
    '''                        Text(\n                            "${it.project.ifBlank { "Advance #${it.id}" }} • ${dateText(it.dateEpochDay)}",\n                            color = MaterialTheme.colorScheme.onPrimary,''',
    '''                        Text(\n                            "${it.project.ifBlank { "Advance #${it.id}" }} • ${dateText(it.dateEpochDay)}${if (it.archived) " • ARCHIVED" else ""}",\n                            color = MaterialTheme.colorScheme.onPrimary,'''
)
rep("        if (advances.isEmpty()) item { InfoCard(\"Start here\", \"Add the money paid into your account, then scan slips against it.\") }",
    "        if (dashboardAdvances.isEmpty()) item { InfoCard(\"Start here\", \"Add the money paid into your account, then scan slips against it.\") }")
rep(
    '''        if (advances.isNotEmpty()) {\n            item { SectionTitle("Loaded advances") }\n            item {\n                AdvanceSelectorRow(\n                    advances = advances,''',
    '''        if (dashboardAdvances.isNotEmpty()) {\n            item { SectionTitle("Loaded advances") }\n            item {\n                AdvanceSelectorRow(\n                    advances = dashboardAdvances,'''
)
rep(
    '''            val selected = advance.id == selectedId\n            val label = advance.project.ifBlank { "Advance #${advance.id}" }''',
    '''            val selected = advance.id == selectedId\n            val label = advance.project.ifBlank { "Advance #${advance.id}" }\n            val archivedMark = if (advance.archived) " • Archived" else ""'''
)
ui = ui.replace(
    'Text("${dateText(advance.dateEpochDay)} • ${money(advance.amountCents)}", style = MaterialTheme.typography.labelSmall)',
    'Text("${dateText(advance.dateEpochDay)} • ${money(advance.amountCents)}$archivedMark", style = MaterialTheme.typography.labelSmall)'
)

# Active advance screen gets archive action and hides already archived records.
rep(
    '''    var editingAdvance by remember { mutableStateOf<Advance?>(null) }\n    var deleteAdvance by remember { mutableStateOf<Advance?>(null) }\n    var returnAdvance by remember { mutableStateOf<Advance?>(null) }''',
    '''    var editingAdvance by remember { mutableStateOf<Advance?>(null) }\n    var deleteAdvance by remember { mutableStateOf<Advance?>(null) }\n    var archiveAdvance by remember { mutableStateOf<Advance?>(null) }\n    var returnAdvance by remember { mutableStateOf<Advance?>(null) }\n    val activeAdvances = advances.filterNot { it.archived }'''
)
rep("        if (advances.isNotEmpty()) item { SectionTitle(\"Advances\") }\n        items(advances, key = { it.id }) { advance ->",
    "        if (activeAdvances.isNotEmpty()) item { SectionTitle(\"Advances\") }\n        items(activeAdvances, key = { it.id }) { advance ->")
rep(
    '''                    TextButton(onClick = { returnAdvance = advance }) { Text("Record money returned") }''',
    '''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {\n                        TextButton(onClick = { returnAdvance = advance }) { Text("Return money") }\n                        TextButton(onClick = { archiveAdvance = advance }) { Text("Archive") }\n                    }'''
)
# Preserve archive fields on correction (mainly defensive if restored from older UI paths).
rep(
    '''                                reference = reference.trim(),\n                                project = project.trim(),\n                                notes = notes.trim()\n                            )''',
    '''                                reference = reference.trim(),\n                                project = project.trim(),\n                                notes = notes.trim(),\n                                archived = editingAdvance?.archived ?: false,\n                                archivedAtMillis = editingAdvance?.archivedAtMillis\n                            )'''
)

# Add archive confirmation after delete dialog and before the screen closes.
marker = '''    deleteAdvance?.let { advance ->\n        AlertDialog(\n            onDismissRequest = { deleteAdvance = null },\n            title = { Text("Delete money received?") },'''
if marker not in ui:
    raise SystemExit("Delete dialog marker not found")
insert_point = '''    }\n}\n\n@Composable\nprivate fun ReturnMoneyDialog'''
archive_dialog = '''    }\n\n    archiveAdvance?.let { advance ->\n        val balance = advance.amountCents - slips.filter { it.advanceId == advance.id }.sumOf { it.totalCents } - returns.filter { it.advanceId == advance.id }.sumOf { it.amountCents }\n        AlertDialog(\n            onDismissRequest = { archiveAdvance = null },\n            title = { Text("Archive this advance?") },\n            text = {\n                Text(\n                    if (balance == 0L)\n                        "This advance is settled. It will move out of the active dashboard and remain available in Archive."\n                    else\n                        "This advance still has ${money(balance)} outstanding. It will be archived but will remain visible on Home until the balance reaches R0.00."\n                )\n            },\n            confirmButton = {\n                TextButton(onClick = {\n                    repository.archiveAdvance(advance, true)\n                    archiveAdvance = null\n                    onDone()\n                }) { Text("Archive") }\n            },\n            dismissButton = { TextButton(onClick = { archiveAdvance = null }) { Text("Cancel") } }\n        )\n    }\n}\n\n@Composable\nprivate fun ReturnMoneyDialog'''
if insert_point not in ui:
    raise SystemExit("Advance screen end marker not found")
ui = ui.replace(insert_point, archive_dialog, 1)

# Add Archive screen before Slips screen.
archive_screen = r'''
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
                val spent = slips.filter { it.advanceId == advance.id }.sumOf { it.totalCents }
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

'''
needle = "@Composable\nprivate fun SlipsScreen"
if needle not in ui:
    raise SystemExit("SlipsScreen marker not found")
ui = ui.replace(needle, archive_screen + needle, 1)

ui_path.write_text(ui)

gradle = gradle_path.read_text()
gradle = gradle.replace('versionCode = 6', 'versionCode = 7')
gradle = gradle.replace('versionName = "0.4.2"', 'versionName = "0.5.0"')
gradle_path.write_text(gradle)
