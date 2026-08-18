from pathlib import Path

root = Path(__file__).resolve().parents[1]
ui = root / "app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt"
text = ui.read_text()

if "import androidx.compose.foundation.layout.safeDrawingPadding" not in text:
    text = text.replace(
        "import androidx.compose.foundation.layout.padding\n",
        "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.safeDrawingPadding\n"
    )

state_anchor = "    var processingOcr by remember { mutableStateOf(false) }\n    var lastBackMillis by remember { mutableLongStateOf(0L) }\n"
state_replacement = state_anchor + "    val crashFile = remember { File(context.filesDir, \"last_crash.txt\") }\n    var showPreviousCrash by remember { mutableStateOf(crashFile.exists()) }\n"
if "var showPreviousCrash" not in text:
    text = text.replace(state_anchor, state_replacement, 1)

text = text.replace(
    "    Scaffold(\n        topBar = {",
    "    Scaffold(\n        modifier = Modifier.fillMaxSize().safeDrawingPadding(),\n        topBar = {",
    1
)

marker = "\n}\n\n@Composable\nprivate fun AppTopBar"
if "Previous crash detected" not in text:
    crash_dialog = '''\n\n    if (showPreviousCrash && crashFile.exists()) {\n        AlertDialog(\n            onDismissRequest = { showPreviousCrash = false },\n            title = { Text(\"Previous crash detected\") },\n            text = { Text(\"ECI Slip Manager saved the Android crash details. Share the log so the exact cause can be fixed, or dismiss it to continue using the app.\") },\n            confirmButton = {\n                TextButton(onClick = {\n                    runCatching { shareFile(context, crashFile, \"text/plain\", \"ECI Slip Manager Crash Log\") }\n                        .onFailure { Toast.makeText(context, \"Could not share crash log: ${it.message}\", Toast.LENGTH_LONG).show() }\n                }) { Text(\"Share crash log\") }\n            },\n            dismissButton = {\n                TextButton(onClick = {\n                    crashFile.delete()\n                    showPreviousCrash = false\n                }) { Text(\"Dismiss\") }\n            }\n        )\n    }\n'''
    text = text.replace(marker, crash_dialog + marker, 1)

ui.write_text(text)

gradle = root / "app/build.gradle.kts"
g = gradle.read_text().replace("versionCode = 5", "versionCode = 6").replace('versionName = "0.4.1"', 'versionName = "0.4.2"')
gradle.write_text(g)
