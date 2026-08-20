from pathlib import Path

path = Path("app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt")
text = path.read_text()
old = "com.google.android.gms.mlkit.vision.documentscanner"
new = "com.google.mlkit.vision.documentscanner"

if old in text:
    text = text.replace(old, new)
elif new not in text:
    raise SystemExit("Document scanner imports were not found")

path.write_text(text)
print("Corrected ML Kit document scanner package imports")
