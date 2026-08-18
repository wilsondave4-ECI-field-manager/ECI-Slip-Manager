package za.co.eci.slipmanager.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SlipRepository(context: Context) {
    private val db = SlipDatabase(context.applicationContext)

    private val _advances = MutableStateFlow<List<Advance>>(emptyList())
    val advances: StateFlow<List<Advance>> = _advances.asStateFlow()

    private val _slips = MutableStateFlow<List<Slip>>(emptyList())
    val slips: StateFlow<List<Slip>> = _slips.asStateFlow()

    private val _returns = MutableStateFlow<List<MoneyReturn>>(emptyList())
    val returns: StateFlow<List<MoneyReturn>> = _returns.asStateFlow()

    private val _reimbursements = MutableStateFlow<List<Reimbursement>>(emptyList())
    val reimbursements: StateFlow<List<Reimbursement>> = _reimbursements.asStateFlow()

    init { refresh() }

    fun refresh() {
        _advances.value = db.getAdvances()
        _slips.value = db.getSlips()
        _returns.value = db.getReturns()
        _reimbursements.value = db.getReimbursements()
    }

    fun saveAdvance(item: Advance): Long = db.upsertAdvance(item).also { refresh() }
    fun saveSlip(item: Slip): Long = db.upsertSlip(item).also { refresh() }
    fun saveReturn(item: MoneyReturn): Long = db.upsertReturn(item).also { refresh() }
    fun saveReimbursement(item: Reimbursement): Long = db.upsertReimbursement(item).also { refresh() }

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
        val selectedSlips = _slips.value.filter { slip ->
            if (advanceId == null) slip.advanceId != null && slip.advanceId in selectedIds
            else slip.advanceId == advanceId
        }
        val selectedReturns = _returns.value.filter { item ->
            if (advanceId == null) item.advanceId in selectedIds else item.advanceId == advanceId
        }
        return Reconciliation(
            receivedCents = selectedAdvances.sumOf { it.amountCents },
            slipsCents = selectedSlips.sumOf { it.companyPaidCents },
            returnedCents = selectedReturns.sumOf { it.amountCents }
        )
    }

    fun activeReconciliation(): Reconciliation {
        val activeIds = _advances.value.filterNot { it.archived }.map { it.id }.toSet()
        return Reconciliation(
            receivedCents = _advances.value.filterNot { it.archived }.sumOf { it.amountCents },
            slipsCents = _slips.value.filter { it.advanceId != null && it.advanceId in activeIds }.sumOf { it.companyPaidCents },
            returnedCents = _returns.value.filter { it.advanceId in activeIds }.sumOf { it.amountCents }
        )
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
