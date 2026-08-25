package za.co.eci.slipmanager.data

import java.util.UUID

enum class PaymentType { ADVANCE, CARD, OWN, SPLIT }
enum class SyncState { LOCAL_ONLY, PENDING, SYNCING, SYNCED, FAILED }

data class Advance(
    val id: Long = 0,
    val serverId: String? = null,
    val dateEpochDay: Long,
    val amountCents: Long,
    val reference: String = "",
    val project: String = "",
    val notes: String = "",
    val archived: Boolean = false,
    val archivedAtMillis: Long? = null
)

data class Slip(
    val id: Long = 0,
    val clientUuid: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val syncError: String = "",
    val advanceId: Long? = null,
    val serverAdvanceId: String? = null,
    val serverCardId: String? = null,
    val supplier: String = "",
    val dateEpochDay: Long? = null,
    val receiptNumber: String = "",
    val subtotalCents: Long? = null,
    val vatCents: Long? = null,
    val totalCents: Long,
    val purpose: String = "",
    val project: String = "",
    val paymentReference: String = "",
    val imagePath: String,
    val ocrText: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val paymentType: PaymentType = PaymentType.ADVANCE,
    val ownMoneyCents: Long = 0L
) {
    val isComplete: Boolean
        get() = supplier.isNotBlank() && dateEpochDay != null && totalCents > 0 && purpose.isNotBlank()

    val companyPaidCents: Long
        get() = (totalCents - ownMoneyCents).coerceAtLeast(0L)
}

data class CompanyCard(
    val serverId: String,
    val name: String,
    val reference: String = "",
    val balanceCents: Long = 0L
)

data class ServerSession(
    val cookie: String,
    val userId: String,
    val employeeId: String,
    val email: String,
    val displayName: String,
    val companyId: String,
    val companyName: String,
    val reportCode: String = "",
    val mustChangePassword: Boolean = false
)

data class MoneyReturn(
    val id: Long = 0,
    val advanceId: Long,
    val dateEpochDay: Long,
    val amountCents: Long,
    val notes: String = ""
)

data class Reimbursement(
    val id: Long = 0,
    val dateEpochDay: Long,
    val amountCents: Long,
    val reference: String = "",
    val notes: String = ""
)

data class Reconciliation(
    val receivedCents: Long,
    val slipsCents: Long,
    val returnedCents: Long
) {
    val outstandingCents: Long get() = receivedCents - slipsCents - returnedCents
}

data class PersonalFundsSummary(
    val usedCents: Long,
    val reimbursedCents: Long
) {
    val outstandingCents: Long get() = (usedCents - reimbursedCents).coerceAtLeast(0L)
}
