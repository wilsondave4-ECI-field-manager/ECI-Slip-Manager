package za.co.eci.slipmanager.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SlipDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE advances (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                server_id TEXT NULL UNIQUE,
                date_epoch_day INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL,
                reference TEXT NOT NULL DEFAULT '',
                project TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                archived INTEGER NOT NULL DEFAULT 0,
                archived_at_millis INTEGER NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE slips (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_uuid TEXT NOT NULL,
                server_id TEXT NULL,
                sync_state TEXT NOT NULL DEFAULT 'PENDING',
                sync_error TEXT NOT NULL DEFAULT '',
                advance_id INTEGER NULL,
                server_advance_id TEXT NULL,
                server_card_id TEXT NULL,
                supplier TEXT NOT NULL DEFAULT '',
                date_epoch_day INTEGER NULL,
                receipt_number TEXT NOT NULL DEFAULT '',
                subtotal_cents INTEGER NULL,
                vat_cents INTEGER NULL,
                total_cents INTEGER NOT NULL,
                purpose TEXT NOT NULL DEFAULT '',
                project TEXT NOT NULL DEFAULT '',
                payment_reference TEXT NOT NULL DEFAULT '',
                image_path TEXT NOT NULL,
                ocr_text TEXT NOT NULL DEFAULT '',
                created_at_millis INTEGER NOT NULL,
                payment_type TEXT NOT NULL DEFAULT 'ADVANCE',
                own_money_cents INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(advance_id) REFERENCES advances(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE money_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                advance_id INTEGER NOT NULL,
                date_epoch_day INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(advance_id) REFERENCES advances(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        createReimbursementsTable(db)
        createCompanyCardsTable(db)
        createMoneyRequestsTable(db)
        db.execSQL("CREATE INDEX idx_slips_advance ON slips(advance_id)")
        db.execSQL("CREATE INDEX idx_slips_supplier ON slips(supplier)")
        db.execSQL("CREATE UNIQUE INDEX idx_slips_client_uuid ON slips(client_uuid)")
        db.execSQL("CREATE INDEX idx_slips_sync_state ON slips(sync_state)")
    }

    private fun createReimbursementsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reimbursements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date_epoch_day INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL,
                reference TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    private fun createCompanyCardsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS company_cards (
                server_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                reference TEXT NOT NULL DEFAULT '',
                balance_cents INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    private fun createMoneyRequestsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS money_requests (
                client_uuid TEXT PRIMARY KEY,
                server_id TEXT NULL,
                sync_state TEXT NOT NULL DEFAULT 'PENDING',
                sync_error TEXT NOT NULL DEFAULT '',
                requested_cents INTEGER NOT NULL,
                purpose TEXT NOT NULL,
                project_site TEXT NOT NULL DEFAULT '',
                required_date TEXT NOT NULL DEFAULT '',
                employee_note TEXT NOT NULL DEFAULT '',
                created_at_millis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_money_requests_sync_state ON money_requests(sync_state)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE advances ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE advances ADD COLUMN archived_at_millis INTEGER NULL")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE slips ADD COLUMN payment_type TEXT NOT NULL DEFAULT 'ADVANCE'")
            db.execSQL("ALTER TABLE slips ADD COLUMN own_money_cents INTEGER NOT NULL DEFAULT 0")
            createReimbursementsTable(db)
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE advances ADD COLUMN server_id TEXT NULL")
            db.execSQL("ALTER TABLE slips ADD COLUMN client_uuid TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE slips ADD COLUMN server_id TEXT NULL")
            db.execSQL("ALTER TABLE slips ADD COLUMN sync_state TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("ALTER TABLE slips ADD COLUMN sync_error TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE slips ADD COLUMN server_advance_id TEXT NULL")
            db.execSQL("ALTER TABLE slips ADD COLUMN server_card_id TEXT NULL")
            createCompanyCardsTable(db)
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_advances_server_id ON advances(server_id) WHERE server_id IS NOT NULL")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_slips_client_uuid ON slips(client_uuid) WHERE client_uuid<>''")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_slips_sync_state ON slips(sync_state)")
        }
        if (oldVersion < 5) createMoneyRequestsTable(db)
    }

    fun getMoneyRequests(): List<MoneyRequest> = readableDatabase.query(
        "money_requests", null, null, null, null, null, "created_at_millis DESC"
    ).use { c -> buildList {
        while (c.moveToNext()) add(MoneyRequest(
            clientUuid = c.getString(c.getColumnIndexOrThrow("client_uuid")),
            serverId = c.getColumnIndexOrThrow("server_id").let { if (c.isNull(it)) null else c.getString(it) },
            syncState = runCatching { SyncState.valueOf(c.getString(c.getColumnIndexOrThrow("sync_state"))) }.getOrDefault(SyncState.PENDING),
            syncError = c.getString(c.getColumnIndexOrThrow("sync_error")),
            requestedCents = c.getLong(c.getColumnIndexOrThrow("requested_cents")),
            purpose = c.getString(c.getColumnIndexOrThrow("purpose")),
            projectSite = c.getString(c.getColumnIndexOrThrow("project_site")),
            requiredDate = c.getString(c.getColumnIndexOrThrow("required_date")),
            employeeNote = c.getString(c.getColumnIndexOrThrow("employee_note")),
            createdAtMillis = c.getLong(c.getColumnIndexOrThrow("created_at_millis"))
        ))
    } }

    fun upsertMoneyRequest(item: MoneyRequest) {
        writableDatabase.insertWithOnConflict("money_requests", null, ContentValues().apply {
            put("client_uuid", item.clientUuid)
            if (item.serverId == null) putNull("server_id") else put("server_id", item.serverId)
            put("sync_state", item.syncState.name); put("sync_error", item.syncError)
            put("requested_cents", item.requestedCents); put("purpose", item.purpose)
            put("project_site", item.projectSite); put("required_date", item.requiredDate)
            put("employee_note", item.employeeNote); put("created_at_millis", item.createdAtMillis)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun pendingMoneyRequests(): List<MoneyRequest> = getMoneyRequests().filter {
        it.syncState == SyncState.PENDING || it.syncState == SyncState.FAILED || it.syncState == SyncState.SYNCING
    }

    fun markMoneyRequestSyncing(id: String) = updateMoneyRequestSync(id, SyncState.SYNCING, "", null)
    fun markMoneyRequestFailed(id: String, error: String) = updateMoneyRequestSync(id, SyncState.FAILED, error.take(500), null)
    fun markMoneyRequestSynced(id: String, serverId: String) = updateMoneyRequestSync(id, SyncState.SYNCED, "", serverId)

    private fun updateMoneyRequestSync(id: String, state: SyncState, error: String, serverId: String?) {
        writableDatabase.update("money_requests", ContentValues().apply {
            put("sync_state", state.name); put("sync_error", error)
            if (serverId != null) put("server_id", serverId)
        }, "client_uuid=?", arrayOf(id))
    }

    fun getAdvances(): List<Advance> = readableDatabase.query(
        "advances", null, null, null, null, null, "date_epoch_day DESC, id DESC"
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val archivedAtCol = c.getColumnIndexOrThrow("archived_at_millis")
                add(
                    Advance(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        serverId = c.getColumnIndex("server_id").takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString),
                        dateEpochDay = c.getLong(c.getColumnIndexOrThrow("date_epoch_day")),
                        amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents")),
                        reference = c.getString(c.getColumnIndexOrThrow("reference")),
                        project = c.getString(c.getColumnIndexOrThrow("project")),
                        notes = c.getString(c.getColumnIndexOrThrow("notes")),
                        archived = c.getInt(c.getColumnIndexOrThrow("archived")) != 0,
                        archivedAtMillis = if (c.isNull(archivedAtCol)) null else c.getLong(archivedAtCol)
                    )
                )
            }
        }
    }

    fun getSlips(): List<Slip> = readableDatabase.query(
        "slips", null, null, null, null, null, "created_at_millis DESC, id DESC"
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val advanceCol = c.getColumnIndexOrThrow("advance_id")
                val dateCol = c.getColumnIndexOrThrow("date_epoch_day")
                val subtotalCol = c.getColumnIndexOrThrow("subtotal_cents")
                val vatCol = c.getColumnIndexOrThrow("vat_cents")
                val paymentType = runCatching {
                    PaymentType.valueOf(c.getString(c.getColumnIndexOrThrow("payment_type")))
                }.getOrDefault(PaymentType.ADVANCE)
                add(
                    Slip(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        clientUuid = c.getColumnIndex("client_uuid").takeIf { it >= 0 }?.let(c::getString).orEmpty(),
                        serverId = c.getColumnIndex("server_id").takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString),
                        syncState = c.getColumnIndex("sync_state").takeIf { it >= 0 }?.let {
                            runCatching { SyncState.valueOf(c.getString(it)) }.getOrDefault(SyncState.LOCAL_ONLY)
                        } ?: SyncState.LOCAL_ONLY,
                        syncError = c.getColumnIndex("sync_error").takeIf { it >= 0 }?.let(c::getString).orEmpty(),
                        advanceId = if (c.isNull(advanceCol)) null else c.getLong(advanceCol),
                        serverAdvanceId = c.getColumnIndex("server_advance_id").takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString),
                        serverCardId = c.getColumnIndex("server_card_id").takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString),
                        supplier = c.getString(c.getColumnIndexOrThrow("supplier")),
                        dateEpochDay = if (c.isNull(dateCol)) null else c.getLong(dateCol),
                        receiptNumber = c.getString(c.getColumnIndexOrThrow("receipt_number")),
                        subtotalCents = if (c.isNull(subtotalCol)) null else c.getLong(subtotalCol),
                        vatCents = if (c.isNull(vatCol)) null else c.getLong(vatCol),
                        totalCents = c.getLong(c.getColumnIndexOrThrow("total_cents")),
                        purpose = c.getString(c.getColumnIndexOrThrow("purpose")),
                        project = c.getString(c.getColumnIndexOrThrow("project")),
                        paymentReference = c.getString(c.getColumnIndexOrThrow("payment_reference")),
                        imagePath = c.getString(c.getColumnIndexOrThrow("image_path")),
                        ocrText = c.getString(c.getColumnIndexOrThrow("ocr_text")),
                        createdAtMillis = c.getLong(c.getColumnIndexOrThrow("created_at_millis")),
                        paymentType = paymentType,
                        ownMoneyCents = c.getLong(c.getColumnIndexOrThrow("own_money_cents"))
                    )
                )
            }
        }
    }

    fun getReturns(): List<MoneyReturn> = readableDatabase.query(
        "money_returns", null, null, null, null, null, "date_epoch_day DESC, id DESC"
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    MoneyReturn(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        advanceId = c.getLong(c.getColumnIndexOrThrow("advance_id")),
                        dateEpochDay = c.getLong(c.getColumnIndexOrThrow("date_epoch_day")),
                        amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents")),
                        notes = c.getString(c.getColumnIndexOrThrow("notes"))
                    )
                )
            }
        }
    }

    fun getReimbursements(): List<Reimbursement> = readableDatabase.query(
        "reimbursements", null, null, null, null, null, "date_epoch_day DESC, id DESC"
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    Reimbursement(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        dateEpochDay = c.getLong(c.getColumnIndexOrThrow("date_epoch_day")),
                        amountCents = c.getLong(c.getColumnIndexOrThrow("amount_cents")),
                        reference = c.getString(c.getColumnIndexOrThrow("reference")),
                        notes = c.getString(c.getColumnIndexOrThrow("notes"))
                    )
                )
            }
        }
    }

    fun upsertAdvance(item: Advance): Long {
        val values = ContentValues().apply {
            if (item.serverId == null) putNull("server_id") else put("server_id", item.serverId)
            put("date_epoch_day", item.dateEpochDay)
            put("amount_cents", item.amountCents)
            put("reference", item.reference)
            put("project", item.project)
            put("notes", item.notes)
            put("archived", if (item.archived) 1 else 0)
            if (item.archivedAtMillis == null) putNull("archived_at_millis") else put("archived_at_millis", item.archivedAtMillis)
        }
        return if (item.id == 0L) {
            writableDatabase.insertOrThrow("advances", null, values)
        } else {
            writableDatabase.update("advances", values, "id=?", arrayOf(item.id.toString()))
            item.id
        }
    }

    fun upsertSlip(item: Slip): Long {
        val values = ContentValues().apply {
            put("client_uuid", item.clientUuid)
            if (item.serverId == null) putNull("server_id") else put("server_id", item.serverId)
            put("sync_state", item.syncState.name)
            put("sync_error", item.syncError)
            if (item.advanceId == null) putNull("advance_id") else put("advance_id", item.advanceId)
            if (item.serverAdvanceId == null) putNull("server_advance_id") else put("server_advance_id", item.serverAdvanceId)
            if (item.serverCardId == null) putNull("server_card_id") else put("server_card_id", item.serverCardId)
            put("supplier", item.supplier)
            if (item.dateEpochDay == null) putNull("date_epoch_day") else put("date_epoch_day", item.dateEpochDay)
            put("receipt_number", item.receiptNumber)
            if (item.subtotalCents == null) putNull("subtotal_cents") else put("subtotal_cents", item.subtotalCents)
            if (item.vatCents == null) putNull("vat_cents") else put("vat_cents", item.vatCents)
            put("total_cents", item.totalCents)
            put("purpose", item.purpose)
            put("project", item.project)
            put("payment_reference", item.paymentReference)
            put("image_path", item.imagePath)
            put("ocr_text", item.ocrText)
            put("created_at_millis", item.createdAtMillis)
            put("payment_type", item.paymentType.name)
            put("own_money_cents", item.ownMoneyCents)
        }
        return if (item.id == 0L) {
            writableDatabase.insertOrThrow("slips", null, values)
        } else {
            writableDatabase.update("slips", values, "id=?", arrayOf(item.id.toString()))
            item.id
        }
    }

    fun upsertReturn(item: MoneyReturn): Long {
        val values = ContentValues().apply {
            put("advance_id", item.advanceId)
            put("date_epoch_day", item.dateEpochDay)
            put("amount_cents", item.amountCents)
            put("notes", item.notes)
        }
        return if (item.id == 0L) {
            writableDatabase.insertOrThrow("money_returns", null, values)
        } else {
            writableDatabase.update("money_returns", values, "id=?", arrayOf(item.id.toString()))
            item.id
        }
    }

    fun upsertReimbursement(item: Reimbursement): Long {
        val values = ContentValues().apply {
            put("date_epoch_day", item.dateEpochDay)
            put("amount_cents", item.amountCents)
            put("reference", item.reference)
            put("notes", item.notes)
        }
        return if (item.id == 0L) {
            writableDatabase.insertOrThrow("reimbursements", null, values)
        } else {
            writableDatabase.update("reimbursements", values, "id=?", arrayOf(item.id.toString()))
            item.id
        }
    }

    fun deleteSlip(id: Long) { writableDatabase.delete("slips", "id=?", arrayOf(id.toString())) }
    fun deleteAdvance(id: Long) { writableDatabase.delete("advances", "id=?", arrayOf(id.toString())) }
    fun deleteReimbursement(id: Long) { writableDatabase.delete("reimbursements", "id=?", arrayOf(id.toString())) }

    fun clearActiveAdvances() { writableDatabase.delete("advances", "archived=0", null) }
    fun clearArchivedAdvances() { writableDatabase.delete("advances", "archived=1", null) }
    fun clearReturns() { writableDatabase.delete("money_returns", null, null) }
    fun clearReimbursements() { writableDatabase.delete("reimbursements", null, null) }
    fun clearSlips() { writableDatabase.delete("slips", null, null) }
    fun clearOwnMoneyAllocations() {
        val values = ContentValues().apply {
            put("payment_type", PaymentType.ADVANCE.name)
            put("own_money_cents", 0L)
        }
        writableDatabase.update("slips", values, "own_money_cents>0 OR payment_type!='ADVANCE'", null)
    }

    fun pendingSlips(): List<Slip> = getSlips().filter {
        it.syncState == SyncState.PENDING || it.syncState == SyncState.FAILED || it.syncState == SyncState.SYNCING
    }

    fun markSlipSyncing(id: Long) {
        writableDatabase.update("slips", ContentValues().apply {
            put("sync_state", SyncState.SYNCING.name); put("sync_error", "")
        }, "id=?", arrayOf(id.toString()))
    }

    fun markSlipSynced(id: Long, serverId: String) {
        writableDatabase.update("slips", ContentValues().apply {
            put("server_id", serverId); put("sync_state", SyncState.SYNCED.name); put("sync_error", "")
        }, "id=?", arrayOf(id.toString()))
    }

    fun markSlipFailed(id: Long, error: String) {
        writableDatabase.update("slips", ContentValues().apply {
            put("sync_state", SyncState.FAILED.name); put("sync_error", error.take(500))
        }, "id=?", arrayOf(id.toString()))
    }

    fun replaceServerFunding(advances: List<Advance>, cards: List<CompanyCard>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("advances", "server_id IS NOT NULL", null)
            advances.forEach(::upsertAdvance)
            db.delete("company_cards", null, null)
            cards.forEach { card ->
                db.insertOrThrow("company_cards", null, ContentValues().apply {
                    put("server_id", card.serverId); put("name", card.name)
                    put("reference", card.reference); put("balance_cents", card.balanceCents)
                })
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun getCompanyCards(): List<CompanyCard> = readableDatabase.query(
        "company_cards", null, null, null, null, null, "name"
    ).use { c -> buildList {
        while (c.moveToNext()) add(CompanyCard(
            serverId = c.getString(c.getColumnIndexOrThrow("server_id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            reference = c.getString(c.getColumnIndexOrThrow("reference")),
            balanceCents = c.getLong(c.getColumnIndexOrThrow("balance_cents"))
        ))
    } }

    fun replaceCompanyCards(cards: List<CompanyCard>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("company_cards", null, null)
            cards.forEach { card -> db.insertOrThrow("company_cards", null, ContentValues().apply {
                put("server_id", card.serverId); put("name", card.name)
                put("reference", card.reference); put("balance_cents", card.balanceCents)
            }) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun replaceAll(
        advances: List<Advance>,
        slips: List<Slip>,
        returns: List<MoneyReturn>,
        reimbursements: List<Reimbursement>
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("reimbursements", null, null)
            db.delete("money_returns", null, null)
            db.delete("slips", null, null)
            db.delete("advances", null, null)

            advances.sortedBy { it.id }.forEach { item ->
                val v = ContentValues().apply {
                    put("id", item.id)
                    if (item.serverId == null) putNull("server_id") else put("server_id", item.serverId)
                    put("date_epoch_day", item.dateEpochDay)
                    put("amount_cents", item.amountCents)
                    put("reference", item.reference)
                    put("project", item.project)
                    put("notes", item.notes)
                    put("archived", if (item.archived) 1 else 0)
                    if (item.archivedAtMillis == null) putNull("archived_at_millis") else put("archived_at_millis", item.archivedAtMillis)
                }
                db.insertOrThrow("advances", null, v)
            }
            slips.sortedBy { it.id }.forEach { item ->
                val v = ContentValues().apply {
                    put("id", item.id)
                    put("client_uuid", item.clientUuid)
                    if (item.serverId == null) putNull("server_id") else put("server_id", item.serverId)
                    put("sync_state", item.syncState.name)
                    put("sync_error", item.syncError)
                    if (item.advanceId == null) putNull("advance_id") else put("advance_id", item.advanceId)
                    if (item.serverAdvanceId == null) putNull("server_advance_id") else put("server_advance_id", item.serverAdvanceId)
                    if (item.serverCardId == null) putNull("server_card_id") else put("server_card_id", item.serverCardId)
                    put("supplier", item.supplier)
                    if (item.dateEpochDay == null) putNull("date_epoch_day") else put("date_epoch_day", item.dateEpochDay)
                    put("receipt_number", item.receiptNumber)
                    if (item.subtotalCents == null) putNull("subtotal_cents") else put("subtotal_cents", item.subtotalCents)
                    if (item.vatCents == null) putNull("vat_cents") else put("vat_cents", item.vatCents)
                    put("total_cents", item.totalCents)
                    put("purpose", item.purpose)
                    put("project", item.project)
                    put("payment_reference", item.paymentReference)
                    put("image_path", item.imagePath)
                    put("ocr_text", item.ocrText)
                    put("created_at_millis", item.createdAtMillis)
                    put("payment_type", item.paymentType.name)
                    put("own_money_cents", item.ownMoneyCents)
                }
                db.insertOrThrow("slips", null, v)
            }
            returns.sortedBy { it.id }.forEach { item ->
                val v = ContentValues().apply {
                    put("id", item.id)
                    put("advance_id", item.advanceId)
                    put("date_epoch_day", item.dateEpochDay)
                    put("amount_cents", item.amountCents)
                    put("notes", item.notes)
                }
                db.insertOrThrow("money_returns", null, v)
            }
            reimbursements.sortedBy { it.id }.forEach { item ->
                val v = ContentValues().apply {
                    put("id", item.id)
                    put("date_epoch_day", item.dateEpochDay)
                    put("amount_cents", item.amountCents)
                    put("reference", item.reference)
                    put("notes", item.notes)
                }
                db.insertOrThrow("reimbursements", null, v)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        private const val DB_NAME = "eci_slips.db"
        private const val DB_VERSION = 5
    }
}
