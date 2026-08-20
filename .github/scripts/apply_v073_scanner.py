from pathlib import Path

path = Path("app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt")
text = path.read_text()

text = text.replace(
    "import androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.contract.ActivityResultContracts\n",
    "import androidx.activity.compose.rememberLauncherForActivityResult\nimport androidx.activity.result.IntentSenderRequest\nimport androidx.activity.result.contract.ActivityResultContracts\n",
)

text = text.replace(
    "import androidx.core.content.FileProvider\n",
    "import androidx.core.content.FileProvider\nimport com.google.android.gms.mlkit.vision.documentscanner.GmsDocumentScannerOptions\nimport com.google.android.gms.mlkit.vision.documentscanner.GmsDocumentScanning\nimport com.google.android.gms.mlkit.vision.documentscanner.GmsDocumentScanningResult\n",
)

text = text.replace(
    "import za.co.eci.slipmanager.ocr.ReceiptOcr\n",
    "import za.co.eci.slipmanager.ocr.ReceiptOcr\nimport za.co.eci.slipmanager.ocr.ReceiptScanProcessor\n",
)

start = text.index("    fun makeReceiptFile(): File {")
end = text.index("\n\n    BackHandler {", start)

scanner_block = '''    fun makeReceiptFile(): File {
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
    }'''

text = text[:start] + scanner_block + text[end:]
path.write_text(text)
print("Applied v0.7.3 document scanner flow")
