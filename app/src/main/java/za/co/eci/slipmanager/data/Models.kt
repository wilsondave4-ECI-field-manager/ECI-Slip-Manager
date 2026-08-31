package za.co.eci.slipmanager.data

import java.util.UUID

enum class PaymentType { ADVANCE, CARD, OWN, SPLIT }
enum class SyncState { LOCAL_ONLY, PENDING, SYNCING, SYNCED, FAILED }

data class Advance(
    val id: Long = 0,
    val serverId: String? = null,
    val dateEpochDay: Long,
    val amountCents: Long,
    val remainingCents: Long = amountCents,
    val reference: String = "",
    val project: String = "",
    val notes: String = "",
    val status: String = "OPEN",
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

data class MoneyRequest(
    val clientUuid: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val syncState: SyncState = SyncState.PENDING,
    val syncError: String = "",
    val requestedCents: Long,
    val purpose: String,
    val projectSite: String = "",
    val requiredDate: String = "",
    val employeeNote: String = "",
    val status: String = "SUBMITTED",
    val approvedCents: Long? = null,
    val accountantNote: String = "",
    val employeeReportedPaidAt: String = "",
    val employeeReportedPaidCents: Long? = null,
    val employeeReportedPaidDate: String = "",
    val employeeReportedPaymentReference: String = "",
    val employeeReportedPaymentNote: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class Refund(
    val serverId: String,
    val status: String,
    val openedAt: String,
    val settledAt: String = "",
    val totalCents: Long,
    val approvedCents: Long,
    val pendingCents: Long,
    val reimbursedCents: Long,
    val outstandingCents: Long,
    val accountantViewedAt: String = "",
    val accountantViewedByName: String = "",
    val updateRequestedAt: String = "",
    val lastReimbursedDate: String = "",
    val lastReimbursementReference: String = ""
)

data class CompanyCardFundingRequest(
    val id: String,
    val cardId: String,
    val cardName: String,
    val requestedCents: Long,
    val approvedCents: Long? = null,
    val purpose: String,
    val status: String,
    val submittedAt: String
)

data class CompanyCardUpdateRequest(
    val id: String,
    val cardName: String,
    val bankBalanceCents: Long? = null,
    val message: String = "",
    val requestedAt: String,
    val acknowledged: Boolean = false
)

data class CompanyCardDetails(
    val cards: List<CompanyCard> = emptyList(),
    val requests: List<CompanyCardFundingRequest> = emptyList(),
    val updates: List<CompanyCardUpdateRequest> = emptyList()
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
