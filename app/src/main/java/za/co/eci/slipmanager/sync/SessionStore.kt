package za.co.eci.slipmanager.sync

import android.content.Context
import org.json.JSONObject
import za.co.eci.slipmanager.data.ServerSession

class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eci_server_session", Context.MODE_PRIVATE)

    fun load(): ServerSession? = runCatching {
        val raw = prefs.getString("session", null) ?: return null
        val o = JSONObject(raw)
        ServerSession(
            cookie = o.getString("cookie"), userId = o.getString("userId"),
            employeeId = o.getString("employeeId"), email = o.getString("email"),
            displayName = o.getString("displayName"), companyId = o.getString("companyId"),
            companyName = o.getString("companyName"), reportCode = o.optString("reportCode"),
            mustChangePassword = o.optBoolean("mustChangePassword")
        )
    }.getOrNull()

    fun save(value: ServerSession) {
        val o = JSONObject()
            .put("cookie", value.cookie).put("userId", value.userId)
            .put("employeeId", value.employeeId).put("email", value.email)
            .put("displayName", value.displayName).put("companyId", value.companyId)
            .put("companyName", value.companyName).put("reportCode", value.reportCode)
            .put("mustChangePassword", value.mustChangePassword)
        prefs.edit().putString("session", o.toString()).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
