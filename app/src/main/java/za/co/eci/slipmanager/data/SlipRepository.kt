package za.co.eci.slipmanager.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import za.co.eci.slipmanager.sync.ExpenseApi
import za.co.eci.slipmanager.sync.ReceiptSyncWorker
import za.co.eci.slipmanager.sync.SessionStore

class SlipRepository(context: Context) {
    private val cardCache = context.applicationContext.getSharedPreferences("card_activity_cache", Context.MODE_PRIVATE)
    private val _cardActivity = MutableStateFlow<CompanyCardActivity?>(null)
    val cardActivity: StateFlow<CompanyCardActivity?> = _cardActivity.asStateFlow()
    private var selectedActivityCardId: String? = null
    private var activitySequence = 0L

    private val appContext = context.applicationContext
    private val db = SlipDatabase(appContext)
    private val sessions = SessionStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _advances = MutableStateFlow<List<Advance>>(emptyList())
    val advances: StateFlow<List<Advance>> = _advances.asStateFlow()

    private val _slips = MutableStateFlow<List<Slip>>(emptyList())
    val slips: StateFlow<List<Slip>> = _slips.asStateFlow()

    private val _returns = MutableStateFlow<List<MoneyReturn>>(emptyList())
    val returns: StateFlow<List<MoneyReturn>> = _returns.asStateFlow()

    private val _reimbursements = MutableStateFlow<List<Reimbursement>>(emptyList())
    val reimbursements: StateFlow<List<Reimbursement>> = _reimbursements.asStateFlow()

    private val _refunds = MutableStateFlow<List<Refund>>(emptyList())
    val refunds: StateFlow<List<Refund>> = _refunds.asStateFlow()

    private val _cards = MutableStateFlow<List<CompanyCard>>(emptyList())
    val cards: StateFlow<List<CompanyCard>> = _cards.asStateFlow()

    private val _moneyRequests = MutableStateFlow<List<MoneyRequest>>(emptyList())
    val moneyRequests: StateFlow<List<MoneyRequest>> = _moneyRequests.asStateFlow()

    private val _cardDetails = MutableStateFlow(CompanyCardDetails())
    val cardDetails: StateFlow<CompanyCardDetails> = _cardDetails.asStateFlow()

    private val _session = MutableStateFlow(sessions.load())
    val session: StateFlow<ServerSession?> = _session.asStateFlow()

    private val _serverMessage = MutableStateFlow("")
    val serverMessage: StateFlow<String> = _serverMessage.asStateFlow()

    init {
        refresh()
        ReceiptSyncWorker.install(appContext)
        if (_session.value != null) ReceiptSyncWorker.request(appContext)
    }

    fun refresh() {
        _advances.value = db.getAdvances()
        _slips.value = db.getSlips()
        _returns.value = db.getReturns()
        _reimbursements.value = db.getReimbursements()
        _refunds.value = db.getRefunds()
        _cards.value = db.getCompanyCards()
        _moneyRequests.value = db.getMoneyRequests()
    }

    fun saveAdvance(item: Advance): Long = db.upsertAdvance(item).also { refresh() }
    fun prepareSlipForEdit(item: Slip, ready: (Slip) -> Unit) {
        val session = _session.value
        if (item.id == 0L || session == null) { ready(item); return }
        _serverMessage.value = "Checking the original receipt…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().receiptState(session, item.clientUuid) } }
                .onSuccess { state ->
                    // Preserve the phone's edited fields, including unsent amounts.
                    ready(item.copy(serverId = state.getString("serverId"), serverVersion = state.getString("version"), serverTotalCents = state.getLong("totalCents")))
                    _serverMessage.value = "Original receipt checked. Corrections require a reason."
                }
                .onFailure {
                    ready(item)
                    _serverMessage.value = "Original receipt could not be checked. Offline work stays on this phone; an unknown version must be checked before correction sync."
                }
        }
    }

    fun saveSlip(item: Slip): Long {
        val pending = item.copy(
            syncState = SyncState.PENDING,
            syncError = "",
            uploadMutationId = java.util.UUID.randomUUID().toString()
        )
        return db.upsertSlip(pending).also {
            refresh()
            ReceiptSyncWorker.request(appContext)
        }
    }
    fun saveReturn(item: MoneyReturn): Long = db.upsertReturn(item).also { refresh() }
    fun saveReimbursement(item: Reimbursement): Long = db.upsertReimbursement(item).also { refresh() }

    fun saveMoneyRequest(item: MoneyRequest) {
        db.upsertMoneyRequest(item); refresh()
        _serverMessage.value = "Money request saved safely on this phone"
        ReceiptSyncWorker.request(appContext)
    }

    fun signIn(email: String, password: String) {
        _serverMessage.value = "Signing in…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().login(email, password) } }
                .onSuccess { session ->
                    sessions.save(session); _session.value = session
                    _serverMessage.value = "Connected to ${session.companyName}"
                    refreshFunding()
                }
                .onFailure { _serverMessage.value = it.message ?: "Sign-in failed" }
        }
    }

    fun changePassword(password: String) {
        val current = _session.value ?: return
        _serverMessage.value = "Saving password…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().changePassword(current, password) } }
                .onSuccess { updated ->
                    sessions.save(updated); _session.value = updated
                    _serverMessage.value = "Password saved"
                    refreshFunding()
                }
                .onFailure { _serverMessage.value = it.message ?: "Could not save password" }
        }
    }

    fun forgotPassword(email: String) {
        _serverMessage.value = "Sending reset email…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().requestPasswordReset(email) } }
                .onSuccess { _serverMessage.value = "If that account exists, its reset email has been sent." }
                .onFailure { _serverMessage.value = it.message ?: "Could not request a reset" }
        }
    }

    fun signOut() {
        sessions.clear(); _session.value = null; _serverMessage.value = ""
        _cardActivity.value = null; _cardDetails.value = CompanyCardDetails(); selectedActivityCardId = null; activitySequence++
    }

    fun refreshFunding() {
        val current = _session.value ?: return
        _serverMessage.value = "Refreshing advances and cards…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().funding(current) } }
                .onSuccess { funding ->
                    withContext(Dispatchers.IO) {
                        db.replaceServerActivity(funding.advances, funding.cards, funding.refunds, funding.moneyRequests)
                    }
                    refresh()
                    _serverMessage.value = "Office balances downloaded. Receipt and request uploads are checked separately."
                    ReceiptSyncWorker.request(appContext)
                    refreshCardDetails()
                }
                .onFailure { error ->
                    _serverMessage.value = when (error) {
                        is za.co.eci.slipmanager.sync.ApiException -> if (error.status == 401) {
                            "Your server session expired — sign out and sign in again"
                        } else {
                            "Server refresh failed (${error.status}): ${error.message}"
                        }
                        else -> "Could not reach the VPS: ${error.message ?: "connection failed"}"
                    }
                }
        }
    }

    fun requestRefundUpdate(refundId: String) {
        val current = _session.value ?: return
        _serverMessage.value = "Requesting refund update…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().requestRefundUpdate(current, refundId) } }
                .onSuccess {
                    _serverMessage.value = "Refund update requested"
                    refreshFunding()
                }
                .onFailure { _serverMessage.value = it.message ?: "Could not request refund update" }
        }
    }

    fun reportPaymentReceived(requestId: String, amountCents: Long, date: String, reference: String, note: String) {
        val current = _session.value ?: return
        _serverMessage.value = "Reporting the missing payment…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ExpenseApi().reportPaymentReceived(current, requestId, amountCents, date, reference, note)
                }
            }.onSuccess {
                _serverMessage.value = "Payment reported to the accountant/admin"
                refreshFunding()
            }.onFailure { _serverMessage.value = it.message ?: "Could not report the payment" }
        }
    }

    fun syncNow() {
        _serverMessage.value = "Sync started…"
        ReceiptSyncWorker.request(appContext)
        refreshFunding()
    }

    private fun cardOwnerKey(session: ServerSession) = "${session.companyId}:${session.userId}"

    fun selectCard(cardId: String?) {
        selectedActivityCardId = cardId
        val sequence = ++activitySequence
        _cardActivity.value = null
        if (cardId == null) return
        val current = _session.value ?: return
        val key = cardOwnerKey(current) + ":" + cardId
        cardCache.getString(key, null)?.let { raw ->
            runCatching { CompanyCardActivity.parse(raw, true) }.getOrNull()?.takeIf { it.cardId == cardId }?.let { _cardActivity.value = it }
        }
        _serverMessage.value = "Refreshing ${_cards.value.firstOrNull { it.serverId == cardId }?.name ?: "card"}…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().companyCardActivity(current, cardId) } }
                .onSuccess { raw ->
                    val activity = runCatching { CompanyCardActivity.parse(raw) }.getOrNull()
                    if (activity == null || activity.cardId != cardId) {
                        if (sequence == activitySequence) _serverMessage.value = "Card details could not be confirmed. Refresh this card again."
                        return@onSuccess
                    }
                    cardCache.edit().putString(key, raw).apply()
                    if (sequence == activitySequence && selectedActivityCardId == cardId && _session.value?.userId == current.userId && _session.value?.companyId == current.companyId) {
                        _cardActivity.value = activity
                        _serverMessage.value = "${activity.name} details refreshed"
                    }
                }.onFailure { error ->
                    if (sequence == activitySequence && selectedActivityCardId == cardId) {
                        if (error is za.co.eci.slipmanager.sync.ApiException && error.status in listOf(401,403,404)) {
                            _cardActivity.value = null
                            cardCache.edit().remove(key).apply()
                            _serverMessage.value = error.message ?: "This card is no longer available to this account"
                        } else _serverMessage.value = if (_cardActivity.value != null) "Offline — saved details for this card" else "Could not load this card: ${error.message}"
                    }
                }
        }
    }

    fun openCardReceipt(slipId: String) {
        val current = _session.value ?: return
        scope.launch {
            runCatching {
                val file = withContext(Dispatchers.IO) {
                    val dir = java.io.File(appContext.filesDir,"card-receipts/" + cardOwnerKey(current).replace(':','_'))
                    listOf("pdf","png","jpg").map { java.io.File(dir,"$slipId.$it") }.firstOrNull { it.isFile }
                        ?: ExpenseApi().downloadCardReceipt(current,slipId,dir)
                }
                val type = when (file.extension) { "pdf" -> "application/pdf"; "png" -> "image/png"; else -> "image/jpeg" }
                val uri = androidx.core.content.FileProvider.getUriForFile(appContext, appContext.packageName + ".files", file)
                appContext.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).setDataAndType(uri,type)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { _serverMessage.value = "Could not open the receipt: ${it.message}. Its record is retained." }
        }
    }

    fun refreshCardDetails() {
        val current = _session.value ?: return
        val directoryKey = "directory:" + cardOwnerKey(current)
        if (_cardDetails.value.cards.isEmpty()) cardCache.getString(directoryKey, null)?.let { raw ->
            runCatching {
                val rows = org.json.JSONArray(raw)
                val saved = buildList { for (i in 0 until rows.length()) rows.getJSONObject(i).let { o -> add(CompanyCard(o.getString("id"),o.getString("name"),o.optString("reference"),o.getLong("balance"))) } }
                _cardDetails.value = CompanyCardDetails(cards = saved)
            }
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().companyCardDetails(current) } }
                .onSuccess { details ->
                    if (_session.value?.userId != current.userId || _session.value?.companyId != current.companyId) return@onSuccess
                    _cardDetails.value = details
                    val saved = org.json.JSONArray()
                    details.cards.forEach { saved.put(org.json.JSONObject().put("id",it.serverId).put("name",it.name).put("reference",it.reference).put("balance",it.balanceCents)) }
                    cardCache.edit().putString(directoryKey,saved.toString()).apply()
                    withContext(Dispatchers.IO) { db.replaceCompanyCards(details.cards) }
                    refresh()
                    _serverMessage.value = "Card balances downloaded. Check the activity sync status for pending work."
                }
                .onFailure { _serverMessage.value = "Offline — showing the last saved card balances" }
        }
    }

    fun requestCardFunds(cardId: String, amountCents: Long, purpose: String) {
        val current = _session.value ?: return
        _serverMessage.value = "Sending card funding request…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { ExpenseApi().requestCardFunds(current, cardId, amountCents, purpose) } }
                .onSuccess { _serverMessage.value = "Card funding request sent"; refreshCardDetails() }
                .onFailure { _serverMessage.value = it.message ?: "Card funding request failed" }
        }
    }

    fun acknowledgeCardUpdate(id: String, complete: Boolean) {
        val current = _session.value ?: return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                if (complete) ExpenseApi().resolveCardUpdate(current, id) else ExpenseApi().acknowledgeCardUpdate(current, id)
            } }.onSuccess { _serverMessage.value = if (complete) "Card slips marked up to date" else "Update acknowledged"; refreshCardDetails() }
                .onFailure { _serverMessage.value = it.message ?: "Could not update the request" }
        }
    }

    fun archiveAdvance(item: Advance, archived: Boolean) {
        db.upsertAdvance(
            item.copy(
                archived = archived,
                archivedAtMillis = if (archived) (item.archivedAtMillis ?: System.currentTimeMillis()) else null
            )
        )
        refresh()
    }

    fun deleteAdvance(item: Advance) {
        db.deleteAdvance(item.id)
        refresh()
    }

    fun deleteSlip(item: Slip) {
        db.deleteSlip(item.id)
        if (item.imagePath.isNotBlank()) runCatching { java.io.File(item.imagePath).delete() }
        refresh()
    }

    fun deleteReimbursement(item: Reimbursement) {
        db.deleteReimbursement(item.id)
        refresh()
    }

    fun reconciliation(advanceId: Long? = null): Reconciliation {
        val selectedAdvances = _advances.value.filter { advanceId == null || it.id == advanceId }
        val selectedIds = selectedAdvances.map { it.id }.toSet()
        val localAdvances = selectedAdvances.filter { it.serverId == null }
        val serverAdvances = selectedAdvances.filter { it.serverId != null }
        val selectedSlips = _slips.value.filter { slip ->
            if (advanceId == null) slip.advanceId != null && slip.advanceId in selectedIds
            else slip.advanceId == advanceId
        }
        val selectedReturns = _returns.value.filter { item ->
            if (advanceId == null) item.advanceId in selectedIds else item.advanceId == advanceId
        }
        val serverPending = _slips.value.filter { slip ->
            slip.syncState != SyncState.SYNCED && serverAdvances.any { it.serverId == slip.serverAdvanceId }
        }.sumOf { it.companyPaidCents }
        val serverUsed = serverAdvances.sumOf { (it.amountCents - it.remainingCents).coerceAtLeast(0L) }
        return Reconciliation(
            receivedCents = selectedAdvances.sumOf { it.amountCents },
            slipsCents = localAdvances.let { locals -> selectedSlips.filter { slip -> locals.any { it.id == slip.advanceId } }.sumOf { it.companyPaidCents } } + serverUsed + serverPending,
            returnedCents = selectedReturns.filter { item -> localAdvances.any { it.id == item.advanceId } }.sumOf { it.amountCents }
        )
    }

    fun activeReconciliation(): Reconciliation {
        val active = _advances.value.filter { !it.archived && it.status in listOf("OPEN", "REOPENED") }
        val ids = active.map { it.id }.toSet()
        val serverIds = active.mapNotNull { it.serverId }.toSet()
        val received = active.sumOf { it.amountCents }
        val serverUsed = active.filter { it.serverId != null }.sumOf { (it.amountCents - it.remainingCents).coerceAtLeast(0L) }
        val pendingServer = _slips.value.filter { it.syncState != SyncState.SYNCED && it.serverAdvanceId in serverIds }.sumOf { it.companyPaidCents }
        val localSpent = _slips.value.filter { it.advanceId in ids && it.serverAdvanceId == null }.sumOf { it.companyPaidCents }
        val localReturns = _returns.value.filter { returnItem -> active.any { it.serverId == null && it.id == returnItem.advanceId } }.sumOf { it.amountCents }
        return Reconciliation(received, serverUsed + pendingServer + localSpent, localReturns)
    }

    fun personalFundsSummary(): PersonalFundsSummary = PersonalFundsSummary(
        usedCents = _slips.value.sumOf { it.ownMoneyCents },
        reimbursedCents = _reimbursements.value.sumOf { it.amountCents }
    )

    fun clearSelectedData(
        activeAdvances: Boolean,
        archivedAdvances: Boolean,
        slipsAndImages: Boolean,
        returns: Boolean,
        ownMoneyAllocations: Boolean,
        reimbursements: Boolean
    ) {
        val images = if (slipsAndImages) _slips.value.map { it.imagePath } else emptyList()
        if (returns) db.clearReturns()
        if (reimbursements) db.clearReimbursements()
        if (slipsAndImages) db.clearSlips()
        else if (ownMoneyAllocations) db.clearOwnMoneyAllocations()
        if (activeAdvances) db.clearActiveAdvances()
        if (archivedAdvances) db.clearArchivedAdvances()
        images.filter { it.isNotBlank() }.forEach { path -> runCatching { java.io.File(path).delete() } }
        refresh()
    }

    fun replaceAll(
        advances: List<Advance>,
        slips: List<Slip>,
        returns: List<MoneyReturn>,
        reimbursements: List<Reimbursement> = emptyList()
    ) {
        db.replaceAll(advances, slips, returns, reimbursements)
        refresh()
    }
}
