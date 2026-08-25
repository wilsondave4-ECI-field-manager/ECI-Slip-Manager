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
        val request = authorized(session, "$base/snapshot").get().build()
        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, errorMessage(payload, "Could not refresh company funding"))
            val snapshot = JSONObject(payload).getJSONObject("snapshot")
            val advancesJson = snapshot.optJSONArray("advances")
            val advances = buildList {
                if (advancesJson != null) for (i in 0 until advancesJson.length()) {
                    val o = advancesJson.getJSONObject(i)
                    add(Advance(
                        serverId = o.getString("id"),
                        dateEpochDay = LocalDate.parse(o.getString("paid_date")).toEpochDay(),
                        amountCents = o.optString("remaining_cents", "0").toLongOrNull() ?: 0L,
                        reference = optionalString(o, "payment_reference"),
                        project = optionalString(o, "project_name").ifBlank { optionalString(o, "project_site") },
                        notes = "Available balance synced from ${session.companyName}"
                    ))
                }
            }
            val cardsJson = snapshot.optJSONArray("companyCards")
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
