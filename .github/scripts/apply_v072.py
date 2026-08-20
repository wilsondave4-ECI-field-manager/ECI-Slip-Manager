from pathlib import Path


def rep(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Patch target not found: {label}")
    return text.replace(old, new, 1)

# ---------------- Settings / report identity ----------------
ui_path = Path('app/src/main/java/za/co/eci/slipmanager/ui/SlipApp.kt')
ui = ui_path.read_text()

old_state = '''    var name by remember { mutableStateOf(prefs.getString("name", "Dave") ?: "Dave") }
    var company by remember { mutableStateOf(prefs.getString("company", "ECI Automation") ?: "ECI Automation") }
    var officeEmail by remember { mutableStateOf(prefs.getString("office_email", "") ?: "") }
    var dextEmail by remember { mutableStateOf(prefs.getString("dext_email", "") ?: "") }
'''
new_state = '''    var name by remember { mutableStateOf(prefs.getString("name", "Dave") ?: "Dave") }
    var company by remember { mutableStateOf(prefs.getString("company", "ECI Automation") ?: "ECI Automation") }
    var companyRegistration by remember { mutableStateOf(prefs.getString("company_registration", "") ?: "") }
    var companyVat by remember { mutableStateOf(prefs.getString("company_vat", "") ?: "") }
    var companyPhone by remember { mutableStateOf(prefs.getString("company_phone", "") ?: "") }
    var companyEmail by remember { mutableStateOf(prefs.getString("company_email", "") ?: "") }
    var companyAddress by remember { mutableStateOf(prefs.getString("company_address", "") ?: "") }
    var logoPath by remember { mutableStateOf(prefs.getString("company_logo_path", "") ?: "") }
    var officeEmail by remember { mutableStateOf(prefs.getString("office_email", "") ?: "") }
    var dextEmail by remember { mutableStateOf(prefs.getString("dext_email", "") ?: "") }
'''
if 'company_registration' not in ui:
    ui = rep(ui, old_state, new_state, 'settings state')

settings_start = ui.index('private fun SettingsScreen')
column_marker = '\n    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)'
column_pos = ui.index(column_marker, settings_start)
if 'val logoPicker =' not in ui[settings_start:column_pos]:
    launcher = '''

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
'''
    ui = ui[:column_pos] + launcher + ui[column_pos:]

old_details = '''        SectionTitle("Your details")
        TextFieldSimple("Name", name, { name = it })
        TextFieldSimple("Company", company, { company = it })
        TextFieldSimple("Office email", officeEmail, { officeEmail = it })
        TextFieldSimple("Dext email", dextEmail, { dextEmail = it })
        Button(onClick = {
            prefs.edit().putString("name", name).putString("company", company).putString("office_email", officeEmail).putString("dext_email", dextEmail).apply()
            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("Save settings") }
'''
new_details = '''        SectionTitle("Report identity")
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
'''
if 'SectionTitle("Report identity")' not in ui:
    ui = rep(ui, old_details, new_details, 'settings details UI')

ui = ui.replace('Text("Name, company and email settings")', 'Text("Report identity, company details, logo and email settings")')
old_reset = 'name = "Dave"; company = "ECI Automation"; officeEmail = ""; dextEmail = ""'
new_reset = '''runCatching { if (logoPath.isNotBlank()) File(logoPath).delete() }
                            name = "Dave"; company = "ECI Automation"; companyRegistration = ""; companyVat = ""
                            companyPhone = ""; companyEmail = ""; companyAddress = ""; logoPath = ""
                            officeEmail = ""; dextEmail = ""'''
if old_reset in ui:
    ui = ui.replace(old_reset, new_reset, 1)
ui = ui.replace('InfoCard("Storage", "Version 0.6 stores the database and original receipt images privately on this Android phone. No AppDeploy, Replit, VPS, or cloud account is required.")',
                'InfoCard("Storage", "Version 0.7.2 stores the database, original receipt images and report branding privately on this Android phone. No AppDeploy, Replit, VPS, or cloud account is required.")')
ui_path.write_text(ui)

# ---------------- Professional PDF header / footer ----------------
pdf_path = Path('app/src/main/java/za/co/eci/slipmanager/pdf/PdfExporter.kt')
pdf = pdf_path.read_text()
if 'import java.time.LocalDateTime' not in pdf:
    pdf = pdf.replace('import java.time.LocalDate\n', 'import java.time.LocalDate\nimport java.time.LocalDateTime\n')
if 'generatedFmt' not in pdf:
    pdf = pdf.replace('    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", za)\n',
                      '    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", za)\n    private val generatedFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", za)\n')

old_init = '''        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
        var y = 48f
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; isFakeBoldText = true }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bodySmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }

        fun newTextPage() {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
            y = 45f
        }

        canvasOf(page).drawText("ECI Slip Manager", 36f, y, heading); y += 28f
        canvasOf(page).drawText(title, 36f, y, sub); y += 26f
'''
new_init = '''        val identity = loadIdentity(context)
        val logo = loadLogo(identity.logoPath)
        val generated = LocalDateTime.now().format(generatedFmt)
        var nextPageNo = 1
        var currentPageNo = nextPageNo++
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, currentPageNo).create())
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
        val bodySmall = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f; color = Color.rgb(205, 205, 205) }
        var y = drawProfessionalHeader(page.canvas, identity, logo, title, generated, currentPageNo)

        fun newTextPage() {
            drawFooter(page.canvas, identity, currentPageNo)
            doc.finishPage(page)
            currentPageNo = nextPageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, currentPageNo).create())
            y = drawProfessionalHeader(page.canvas, identity, logo, title, generated, currentPageNo)
        }

        drawSectionHeader(page.canvas, "RECONCILIATION", y); y += 25f
'''
if 'drawProfessionalHeader(page.canvas' not in pdf:
    pdf = rep(pdf, old_init, new_init, 'PDF page init')

pdf = pdf.replace('canvasOf(page).drawText("VAT summary", 36f, y, sub); y += 18f', 'drawSectionHeader(page.canvas, "VAT SUMMARY", y); y += 25f')
pdf = pdf.replace('canvasOf(page).drawText("Money returned details", 36f, y, sub)\n            y += 18f', 'drawSectionHeader(page.canvas, "MONEY RETURNED", y)\n            y += 25f')
pdf = pdf.replace('canvasOf(page).drawText("Personal funds used", 36f, y, sub); y += 18f', 'drawSectionHeader(page.canvas, "PERSONAL FUNDS", y); y += 25f')
old_register = '''        y += 10f
        canvasOf(page).drawLine(36f, y, PAGE_W - 36f, y, line); y += 22f
        canvasOf(page).drawText("Slip register", 36f, y, sub); y += 20f
'''
new_register = '''        y += 8f
        drawSectionHeader(page.canvas, "SLIP REGISTER", y); y += 25f
'''
if old_register in pdf:
    pdf = pdf.replace(old_register, new_register, 1)

old_finish = '''        }
        doc.finishPage(page)

        slips.forEach { slip ->
            val bitmap = loadReceiptForPdf(slip.imagePath) ?: return@forEach
            val receiptPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
            drawReceiptPage(receiptPage.canvas, bitmap, slip)
            doc.finishPage(receiptPage)
            bitmap.recycle()
        }

        FileOutputStream(file).use { doc.writeTo(it) }
'''
new_finish = '''        }
        drawFooter(page.canvas, identity, currentPageNo)
        doc.finishPage(page)

        slips.forEachIndexed { index, slip ->
            val bitmap = loadReceiptForPdf(slip.imagePath) ?: return@forEachIndexed
            val receiptPageNo = nextPageNo++
            val receiptPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, receiptPageNo).create())
            drawProfessionalReceiptPage(receiptPage.canvas, bitmap, slip, identity, logo, title, generated, receiptPageNo, index + 1, slips.size)
            doc.finishPage(receiptPage)
            bitmap.recycle()
        }

        logo?.recycle()
        FileOutputStream(file).use { doc.writeTo(it) }
'''
if 'drawProfessionalReceiptPage(receiptPage.canvas' not in pdf:
    pdf = rep(pdf, old_finish, new_finish, 'PDF receipt/finalize')

helper_marker = '    private fun loadReceiptForPdf(path: String): Bitmap? {'
if 'private data class ReportIdentity' not in pdf:
    helpers = r'''    private data class ReportIdentity(
        val submittedBy: String,
        val company: String,
        val registration: String,
        val vatNumber: String,
        val phone: String,
        val email: String,
        val address: String,
        val logoPath: String
    )

    private fun loadIdentity(context: Context): ReportIdentity {
        val prefs = context.getSharedPreferences("settings", 0)
        return ReportIdentity(
            submittedBy = prefs.getString("name", "Dave")?.trim().orEmpty().ifBlank { "Not specified" },
            company = prefs.getString("company", "ECI Automation")?.trim().orEmpty().ifBlank { "ECI Automation" },
            registration = prefs.getString("company_registration", "")?.trim().orEmpty(),
            vatNumber = prefs.getString("company_vat", "")?.trim().orEmpty(),
            phone = prefs.getString("company_phone", "")?.trim().orEmpty(),
            email = prefs.getString("company_email", "")?.trim().orEmpty(),
            address = prefs.getString("company_address", "")?.trim().orEmpty(),
            logoPath = prefs.getString("company_logo_path", "")?.trim().orEmpty()
        )
    }

    private fun loadLogo(path: String): Bitmap? {
        if (path.isBlank() || !File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 600) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    }

    private fun drawProfessionalHeader(
        canvas: Canvas,
        identity: ReportIdentity,
        logo: Bitmap?,
        reportLabel: String,
        generated: String,
        pageNumber: Int
    ): Float {
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 193, 7) }
        val strong = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 32, 32); textSize = 15f; isFakeBoldText = true }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(95, 95, 95); textSize = 7.5f }
        val meta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(75, 75, 75); textSize = 8f }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 205, 205); strokeWidth = 0.8f }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 7f, yellow)

        if (logo != null) {
            drawBitmapFit(canvas, logo, RectF(36f, 20f, 150f, 72f))
        } else {
            canvas.drawText(identity.company.uppercase(Locale.getDefault()), 36f, 48f, strong)
            canvas.drawRect(36f, 57f, 112f, 61f, yellow)
        }

        var rightY = 27f
        val companyRight = Paint(small).apply { isFakeBoldText = true; color = Color.rgb(32, 32, 32) }
        drawRightText(canvas, identity.company, PAGE_W - 36f, rightY, companyRight); rightY += 10f
        val details = mutableListOf<String>()
        if (identity.registration.isNotBlank()) details += "Reg: ${identity.registration}"
        if (identity.vatNumber.isNotBlank()) details += "VAT: ${identity.vatNumber}"
        if (identity.phone.isNotBlank()) details += identity.phone
        if (identity.email.isNotBlank()) details += identity.email
        if (identity.address.isNotBlank()) details += identity.address.replace('\n', ' ')
        details.take(4).forEach { detail ->
            drawRightText(canvas, shorten(detail, 58), PAGE_W - 36f, rightY, small); rightY += 9f
        }

        canvas.drawLine(36f, 82f, PAGE_W - 36f, 82f, divider)
        canvas.drawText("EXPENSE & ADVANCE REPORT", 36f, 108f, strong)
        drawRightText(canvas, "Page $pageNumber", PAGE_W - 36f, 108f, meta)
        canvas.drawText("Report: ${shorten(reportLabel, 58)}", 36f, 126f, meta)
        canvas.drawText("Submitted by: ${shorten(identity.submittedBy, 42)}", 36f, 140f, meta)
        drawRightText(canvas, "Generated: $generated", PAGE_W - 36f, 140f, meta)
        canvas.drawLine(36f, 150f, PAGE_W - 36f, 150f, divider)
        return 174f
    }

    private fun drawSectionHeader(canvas: Canvas, text: String, baseline: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(242, 242, 242) }
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 193, 7) }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 32, 32); textSize = 10.5f; isFakeBoldText = true }
        canvas.drawRect(36f, baseline - 12f, PAGE_W - 36f, baseline + 7f, bg)
        canvas.drawRect(36f, baseline - 12f, 41f, baseline + 7f, yellow)
        canvas.drawText(text, 48f, baseline + 2f, label)
    }

    private fun drawFooter(canvas: Canvas, identity: ReportIdentity, pageNumber: Int) {
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 205, 205); strokeWidth = 0.7f }
        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(105, 105, 105); textSize = 7f }
        canvas.drawLine(36f, 812f, PAGE_W - 36f, 812f, divider)
        canvas.drawText("Generated by ECI Slip Manager | Submitted by ${shorten(identity.submittedBy, 34)}", 36f, 827f, footer)
        drawRightText(canvas, "Page $pageNumber", PAGE_W - 36f, 827f, footer)
    }

    private fun drawProfessionalReceiptPage(
        canvas: Canvas,
        bitmap: Bitmap,
        slip: Slip,
        identity: ReportIdentity,
        logo: Bitmap?,
        reportLabel: String,
        generated: String,
        pageNumber: Int,
        receiptIndex: Int,
        receiptCount: Int
    ) {
        val yellow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 193, 7) }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 32, 32); textSize = 11f; isFakeBoldText = true }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(55, 55, 55); textSize = 8f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(105, 105, 105); textSize = 7.5f }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(205, 205, 205); strokeWidth = 0.8f }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 7f, yellow)
        if (logo != null) drawBitmapFit(canvas, logo, RectF(36f, 17f, 125f, 55f))
        else canvas.drawText(identity.company.uppercase(Locale.getDefault()), 36f, 39f, head)
        drawRightText(canvas, "EXPENSE & ADVANCE REPORT", PAGE_W - 36f, 27f, head)
        drawRightText(canvas, "Receipt $receiptIndex of $receiptCount | Page $pageNumber", PAGE_W - 36f, 42f, small)
        canvas.drawLine(36f, 64f, PAGE_W - 36f, 64f, divider)

        val date = slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).format(dateFmt) } ?: "Date missing"
        val vatText = slip.vatCents?.let(::fmt) ?: "NOT CAPTURED"
        val exVatText = slip.subtotalCents?.let(::fmt)
            ?: slip.vatCents?.let { fmt((slip.totalCents - it).coerceAtLeast(0L)) }
            ?: "NOT CAPTURED"
        canvas.drawText(shorten(slip.supplier.ifBlank { "Supplier missing" }, 62), 36f, 86f, head)
        canvas.drawText("$date | ${shorten(reportLabel, 50)}", 36f, 100f, small)
        canvas.drawText("Ex VAT $exVatText | VAT $vatText | Total ${fmt(slip.totalCents)}", 36f, 116f, body)
        drawRightText(canvas, "Generated $generated", PAGE_W - 36f, 100f, small)
        canvas.drawLine(36f, 126f, PAGE_W - 36f, 126f, divider)

        val top = 140f
        val bottom = 797f
        val maxW = PAGE_W - 48f
        val maxH = bottom - top
        val scale = minOf(maxW / bitmap.width.toFloat(), maxH / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = (PAGE_W - w) / 2f
        val imageTop = top + (maxH - h).coerceAtLeast(0f) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, imageTop, left + w, imageTop + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        drawFooter(canvas, identity, pageNumber)
    }

    private fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, box: RectF) {
        val scale = minOf(box.width() / bitmap.width.toFloat(), box.height() / bitmap.height.toFloat())
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        canvas.drawBitmap(bitmap, null, RectF(box.left, box.top + (box.height() - h) / 2f, box.left + w, box.top + (box.height() - h) / 2f + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawRightText(canvas: Canvas, text: String, right: Float, y: Float, paint: Paint) {
        canvas.drawText(text, right - paint.measureText(text), y, paint)
    }

    private fun shorten(text: String, maxChars: Int): String {
        val clean = text.replace('\n', ' ').replace('\r', ' ').trim()
        return if (clean.length <= maxChars) clean else clean.take((maxChars - 3).coerceAtLeast(1)) + "..."
    }

'''
    pdf = pdf.replace(helper_marker, helpers + helper_marker, 1)

pdf_path.write_text(pdf)

# ---------------- Version / signed artifact ----------------
gradle_path = Path('app/build.gradle.kts')
gradle = gradle_path.read_text()
gradle = gradle.replace('versionCode = 11', 'versionCode = 12')
gradle = gradle.replace('versionName = "0.7.1"', 'versionName = "0.7.2"')
gradle_path.write_text(gradle)

workflow_path = Path('.github/workflows/android.yml')
workflow = workflow_path.read_text().replace('ECI-Slip-Manager-v0.7.1-signed', 'ECI-Slip-Manager-v0.7.2-signed')
workflow_path.write_text(workflow)

print('v0.7.2 professional report patch applied')
