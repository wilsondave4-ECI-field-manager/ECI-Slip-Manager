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

    init { refresh() }

    fun refresh() {
        _advances.value = db.getAdvances()
        _slips.value = db.getSlips()
        _returns.value = db.getReturns()
    }

    fun saveAdvance(item: Advance): Long = db.upsertAdvance(item).also { refresh() }
    fun saveSlip(item: Slip): Long = db.upsertSlip(item).also { refresh() }
    fun saveReturn(item: MoneyReturn): Long = db.upsertReturn(item).also { refresh() }

    fun deleteSlip(item: Slip) {
        db.deleteSlip(item.id)
        if (item.imagePath.isNotBlank()) runCatching { java.io.File(item.imagePath).delete() }
        refresh()
    }

    fun reconciliation(advanceId: Long? = null): Reconciliation {
        val advances = _advances.value.filter { advanceId == null || it.id == advanceId }
        val slips = _slips.value.filter { advanceId == null || it.advanceId == advanceId }
        val returns = _returns.value.filter { advanceId == null || it.advanceId == advanceId }
        return Reconciliation(
            receivedCents = advances.sumOf { it.amountCents },
            slipsCents = slips.sumOf { it.totalCents },
            returnedCents = returns.sumOf { it.amountCents }
        )
    }

    fun replaceAll(advances: List<Advance>, slips: List<Slip>, returns: List<MoneyReturn>) {
        db.replaceAll(advances, slips, returns)
        refresh()
    }
}
