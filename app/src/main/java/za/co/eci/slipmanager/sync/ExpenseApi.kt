package za.co.eci.slipmanager.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import za.co.eci.slipmanager.BuildConfig
import za.co.eci.slipmanager.data.Advance
import za.co.eci.slipmanager.data.CompanyCard
import za.co.eci.slipmanager.data.CompanyCardDetails
import za.co.eci.slipmanager.data.CompanyCardFundingRequest
import za.co.eci.slipmanager.data.CompanyCardUpdateRequest
import za.co.eci.slipmanager.data.MoneyRequest
import za.co.eci.slipmanager.data.PaymentType
import za.co.eci.slipmanager.data.ServerSession
import za.co.eci.slipmanager.data.Slip
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class ServerFunding(val advances: List<Advance>, val cards: List<CompanyCard>)
class ApiException(val status: Int, message: String) : Exception(message)

class ExpenseApi {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).build()
    private val base = BuildConfig.ECI_SERVER_URL.trimEnd('/') + "/api"

    fun login(email: String, password: String): ServerSession {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
        val request = Request.Builder().url("$base/auth/login").post(body.toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Sign-in failed"))
            val cookie = response.headers("Set-Cookie").firstOrNull()
                ?.substringBefore(';') ?: throw ApiException(500, "The server did not return a secure session")
            return sessionFrom(JSONObject(payload).getJSONObject("user"), cookie)
        }
    }

    fun changePassword(session: ServerSession, password: String): ServerSession {
        val body = JSONObject().put("password", password).toString()
        val request = authorized(session, "$base/auth/change-password").post(body.toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Could not change password"))
            return sessionFrom(JSONObject(payload).getJSONObject("user"), session.cookie)
        }
    }

    fun requestPasswordReset(email: String) {
        val body = JSONObject().put("email", email.trim()).toString()
        val request = Request.Builder().url("$base/auth/forgot-password").post(body.toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.code, "Could not request password reset")
        }
    }

    fun funding(session: ServerSession): ServerFunding {
        val request = authorized(session, "$base/employee/activity")
            .header("Cache-Control", "no-cache")
            .get().build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Could not refresh company funding"))
            val root = JSONObject(payload)
            if (!root.optBoolean("ok", true)) throw ApiException(502, errorMessage(payload, "Invalid employee activity response"))
            val activity = root.optJSONObject("snapshot") ?: root
            val advancesJson = activity.optJSONArray("advances")
            val advances = buildList {
                if (advancesJson != null) for (i in 0 until advancesJson.length()) {
                    val o = advancesJson.getJSONObject(i)
                    val paidDate = optionalString(o, "paid_date")
                    val date = runCatching { LocalDate.parse(paidDate) }.getOrDefault(LocalDate.now())
                    add(Advance(
                        serverId = o.getString("id"),
                        dateEpochDay = date.toEpochDay(),
                        amountCents = o.optString("remaining_cents", "0").toLongOrNull() ?: 0L,
                        reference = optionalString(o, "payment_reference"),
                        project = optionalString(o, "project_name").ifBlank { optionalString(o, "project_site") },
                        notes = "Available balance synced from ${session.companyName}"
                    ))
                }
            }
            val cardsJson = activity.optJSONArray("companyCards")
            val cards = buildList {
                if (cardsJson != null) for (i in 0 until cardsJson.length()) {
                    val o = cardsJson.getJSONObject(i)
                    add(CompanyCard(
                        serverId = o.getString("id"), name = o.getString("name"),
                        reference = optionalString(o, "card_reference"),
                        balanceCents = o.optString("balance_cents", "0").toLongOrNull() ?: 0L
                    ))
                }
            }
            return ServerFunding(advances, cards)
        }
    }

    fun uploadReceipt(session: ServerSession, slip: Slip): String {
        val image = File(slip.imagePath)
        require(image.isFile) { "Stored receipt image is missing" }
        val source = when (slip.paymentType) {
            PaymentType.CARD -> "CARD"
            PaymentType.OWN -> "OWN"
            PaymentType.ADVANCE, PaymentType.SPLIT -> "ADVANCE"
        }
        val metadata = JSONObject()
            .put("clientUuid", slip.clientUuid)
            .put("projectId", JSONObject.NULL)
            .put("projectSite", nullableText(slip.project))
            .put("paymentSource", source)
            .put("advanceId", slip.serverAdvanceId ?: JSONObject.NULL)
            .put("cardId", slip.serverCardId ?: JSONObject.NULL)
            .put("supplier", slip.supplier)
            .put("documentNumber", nullableText(slip.receiptNumber))
            .put("purchaseDate", slip.dateEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: JSONObject.NULL)
            .put("subtotalCents", slip.subtotalCents ?: JSONObject.NULL)
            .put("vatCents", slip.vatCents ?: JSONObject.NULL)
            .put("totalCents", slip.totalCents)
            .put("purpose", slip.purpose)
            .put("paymentReference", nullableText(slip.paymentReference))
            .put("ocrText", nullableText(slip.ocrText))
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("metadata", metadata.toString())
            .addFormDataPart("scan", "receipt-${slip.clientUuid}.jpg", image.asRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = authorized(session, "$base/sync/receipt")
            .header("Idempotency-Key", slip.clientUuid).post(multipart).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Receipt sync failed"))
            val result = JSONObject(payload)
            if (!result.optBoolean("documentStored")) throw ApiException(502, "The server did not confirm receipt storage")
            return result.getString("serverId")
        }
    }

    fun uploadMoneyRequest(session: ServerSession, item: MoneyRequest): String {
        val body = JSONObject()
            .put("clientUuid", item.clientUuid).put("projectId", JSONObject.NULL)
            .put("projectSite", nullableText(item.projectSite))
            .put("requestedCents", item.requestedCents)
            .put("requiredDate", nullableText(item.requiredDate))
            .put("purpose", item.purpose).put("employeeNote", nullableText(item.employeeNote))
        val request = authorized(session, "$base/sync/money-request")
            .header("Idempotency-Key", item.clientUuid).post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Money request sync failed"))
            return JSONObject(payload).getString("serverId")
        }
    }

    fun companyCardDetails(session: ServerSession): CompanyCardDetails {
        val request = authorized(session, "$base/employee/company-cards").get().build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Could not load company cards"))
            val root = JSONObject(payload)
            val cardsJson = root.optJSONArray("cards")
            val cards = buildList {
                if (cardsJson != null) for (i in 0 until cardsJson.length()) cardsJson.getJSONObject(i).let { o -> add(CompanyCard(
                    serverId = o.getString("id"), name = o.getString("name"),
                    reference = optionalString(o, "card_reference"),
                    balanceCents = o.optString("balance_cents", "0").toLongOrNull() ?: 0L
                )) }
            }
            val requestsJson = root.optJSONArray("requests")
            val requests = buildList {
                if (requestsJson != null) for (i in 0 until requestsJson.length()) requestsJson.getJSONObject(i).let { o -> add(CompanyCardFundingRequest(
                    id = o.getString("id"), cardId = o.getString("card_id"), cardName = o.getString("card_name"),
                    requestedCents = o.optString("requested_cents", "0").toLongOrNull() ?: 0L,
                    approvedCents = optionalString(o, "approved_cents").toLongOrNull(),
                    purpose = o.getString("purpose"), status = o.getString("status"), submittedAt = o.getString("submitted_at")
                )) }
            }
            val updatesJson = root.optJSONArray("updateRequests")
            val updates = buildList {
                if (updatesJson != null) for (i in 0 until updatesJson.length()) updatesJson.getJSONObject(i).let { o -> add(CompanyCardUpdateRequest(
                    id = o.getString("id"), cardName = o.getString("card_name"),
                    bankBalanceCents = optionalString(o, "bank_balance_cents").toLongOrNull(),
                    message = optionalString(o, "message"), requestedAt = o.getString("requested_at"),
                    acknowledged = !o.isNull("acknowledged_at")
                )) }
            }
            return CompanyCardDetails(cards, requests, updates)
        }
    }

    fun requestCardFunds(session: ServerSession, cardId: String, amountCents: Long, purpose: String) {
        val body = JSONObject().put("cardId", cardId).put("requestedCents", amountCents).put("purpose", purpose)
        postAuthorized(session, "$base/employee/company-cards/requests", body, "Card funding request failed")
    }

    fun acknowledgeCardUpdate(session: ServerSession, id: String) =
        postAuthorized(session, "$base/employee/company-cards/update-requests/$id/acknowledge", JSONObject(), "Could not acknowledge update")

    fun resolveCardUpdate(session: ServerSession, id: String) =
        postAuthorized(session, "$base/employee/company-cards/update-requests/$id/resolve", JSONObject(), "Could not complete update")

    private fun postAuthorized(session: ServerSession, url: String, body: JSONObject, fallback: String) {
        val request = authorized(session, url).post(body.toString().toRequestBody(jsonType)).build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, fallback))
        }
    }

    private fun authorized(session: ServerSession, url: String) = Request.Builder()
        .url(url).header("Cookie", session.cookie).header("Accept", "application/json")

    private fun nullableText(value: String): Any = value.trim().takeIf { it.isNotEmpty() } ?: JSONObject.NULL

    private fun optionalString(source: JSONObject, name: String): String =
        if (source.isNull(name)) "" else source.optString(name).takeUnless { it == "null" } ?: ""

    private fun sessionFrom(user: JSONObject, cookie: String) = ServerSession(
        cookie = cookie, userId = user.getString("id"),
        employeeId = optionalString(user, "employeeId"), email = user.getString("email"),
        displayName = user.getString("displayName"), companyId = user.getString("companyId"),
        companyName = user.getString("companyName"), reportCode = optionalString(user, "reportCode"),
        mustChangePassword = user.optBoolean("mustChangePassword")
    ).also { require(it.employeeId.isNotBlank()) { "This account has no employee profile" } }

    private fun errorMessage(payload: String, fallback: String): String = runCatching {
        JSONObject(payload).optString("error").replace('_', ' ').ifBlank { fallback }
    }.getOrDefault(fallback)
}
