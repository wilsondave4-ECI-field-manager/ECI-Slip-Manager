from pathlib import Path

path = Path("app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt")
text = path.read_text()

# Normalize the ML Kit package if an earlier build patch used the artifact's
# Maven group as the Kotlin package name.
text = text.replace(
    "com.google.android.gms.mlkit.vision.documentscanner",
    "com.google.mlkit.vision.documentscanner",
)

# Earlier verification runs can execute this helper more than once. Keep these
# imports idempotent so repeated builds never create conflicting imports.
deduped_imports = {
    "import androidx.activity.result.IntentSenderRequest",
    "import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions",
    "import com.google.mlkit.vision.documentscanner.GmsDocumentScanning",
    "import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult",
    "import za.co.eci.slipmanager.ocr.ReceiptScanProcessor",
}
seen = set()
out = []
for line in text.splitlines():
    if line in deduped_imports:
        if line in seen:
            continue
        seen.add(line)
    out.append(line)

path.write_text("\n".join(out) + "\n")
print("Normalized v0.7.3 scanner imports")
