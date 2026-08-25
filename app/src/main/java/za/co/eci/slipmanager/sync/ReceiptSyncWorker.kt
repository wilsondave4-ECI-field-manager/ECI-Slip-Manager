package za.co.eci.slipmanager.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import za.co.eci.slipmanager.data.PaymentType
import za.co.eci.slipmanager.data.SlipDatabase
import java.util.concurrent.TimeUnit

class ReceiptSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val session = SessionStore(applicationContext).load() ?: return@withContext Result.success()
        val db = SlipDatabase(applicationContext)
        val api = ExpenseApi()
        try {
            for (request in db.pendingMoneyRequests().sortedBy { it.createdAtMillis }) {
                db.markMoneyRequestSyncing(request.clientUuid)
                try {
                    db.markMoneyRequestSynced(request.clientUuid, api.uploadMoneyRequest(session, request))
                } catch (error: ApiException) {
                    db.markMoneyRequestFailed(request.clientUuid, error.message ?: "Server rejected request")
                    if (error.status == 401 || error.status >= 500) return@withContext Result.retry()
                } catch (error: Exception) {
                    db.markMoneyRequestFailed(request.clientUuid, error.message ?: "Connection failed")
                    return@withContext Result.retry()
                }
            }
            for (slip in db.pendingSlips().sortedBy { it.createdAtMillis }) {
                if (!slip.isComplete || slip.clientUuid.isBlank()) continue
                if ((slip.paymentType == PaymentType.ADVANCE || slip.paymentType == PaymentType.SPLIT) && slip.serverAdvanceId.isNullOrBlank()) {
                    db.markSlipFailed(slip.id, "Select a synced company advance")
                    continue
                }
                if (slip.paymentType == PaymentType.CARD && slip.serverCardId.isNullOrBlank()) {
                    db.markSlipFailed(slip.id, "Select an assigned company card")
                    continue
                }
                db.markSlipSyncing(slip.id)
                try {
                    db.markSlipSynced(slip.id, api.uploadReceipt(session, slip))
                } catch (error: ApiException) {
                    db.markSlipFailed(slip.id, error.message ?: "Server rejected receipt")
                    if (error.status == 401 || error.status >= 500) return@withContext Result.retry()
                } catch (error: Exception) {
                    db.markSlipFailed(slip.id, error.message ?: "Connection failed")
                    return@withContext Result.retry()
                }
            }
            runCatching {
                val funding = api.funding(session)
                db.replaceServerFunding(funding.advances, funding.cards)
            }
            Result.success()
        } finally { db.close() }
    }

    companion object {
        private const val PERIODIC = "eci-receipt-periodic-sync"
        private const val IMMEDIATE = "eci-receipt-immediate-sync"
        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun install(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReceiptSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connected).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun request(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReceiptSyncWorker>().setConstraints(connected).build()
            WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
