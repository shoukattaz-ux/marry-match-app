package com.example.data

import com.example.core.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
data class AdminStats(
    @SerialName("users_total") val usersTotal: Int = 0,
    @SerialName("new_today") val newToday: Int = 0,
    @SerialName("active_7d") val active7d: Int = 0,
    @SerialName("profiles_done") val profilesDone: Int = 0,
    val verified: Int = 0,
    @SerialName("pending_selfies") val pendingSelfies: Int = 0,
    @SerialName("open_reports") val openReports: Int = 0,
    val banned: Int = 0,
    @SerialName("needs_review") val needsReview: Int = 0,
    /** Couples where both confirmed "We met", and meetings someone marked unsafe (last 30 days). */
    @SerialName("met_couples") val metCouples: Int = 0,
    @SerialName("met_unsafe_30d") val metUnsafe30d: Int = 0
)

@Serializable
data class AdminReport(
    @SerialName("report_id") val reportId: String,
    val reason: String,
    val details: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("reporter_id") val reporterId: String = "",
    @SerialName("reporter_name") val reporterName: String = "",
    @SerialName("reported_id") val reportedId: String,
    @SerialName("reported_name") val reportedName: String = "",
    @SerialName("reported_age") val reportedAge: Int = 0,
    @SerialName("reported_city") val reportedCity: String = "",
    @SerialName("reported_banned") val reportedBanned: Boolean = false,
    @SerialName("reported_open_reports") val reportedOpenReports: Int = 0,
    @SerialName("message_body") val messageBody: String? = null
)

@Serializable
data class AdminUser(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String = "",
    val email: String = "",
    val age: Int = 0,
    val city: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("selfie_verified") val selfieVerified: Boolean = false,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("needs_review") val needsReview: Boolean = false,
    @SerialName("respect_score") val respectScore: Int = 100,
    @SerialName("open_reports") val openReports: Int = 0
)

@Serializable
data class AuditEntry(
    val id: Long,
    @SerialName("created_at") val createdAt: String = "",
    val action: String,
    @SerialName("actor_name") val actorName: String = "",
    @SerialName("target_user") val targetUser: String? = null,
    @SerialName("target_name") val targetName: String = "",
    val details: JsonObject? = null
) {
    /** Short extra info for the log line: the reason or note, if any. */
    val note: String?
        get() = details?.let { d ->
            (d["reason"] ?: d["note"])?.jsonPrimitive?.contentOrNull
        }
}

/** Moderator actions; see the moderate-user Edge Function. */
enum class ModerationAction(val wire: String) { DISMISS("dismiss"), WARN("warn"), BAN("ban"), UNBAN("unban") }

/** Admin panel data and actions. An interface so the panel logic can be tested with a fake. */
interface AdminApi {
    suspend fun stats(): AdminStats
    suspend fun reports(status: String = "open"): List<AdminReport>
    suspend fun searchUsers(query: String): List<AdminUser>
    suspend fun auditLog(limit: Int = 100): List<AuditEntry>
    suspend fun moderate(action: ModerationAction, userId: String? = null, reportId: String? = null, note: String? = null)
}

/** A moderation call refused by the server; [code] is e.g. "report_not_open" or "cannot_moderate_admin". */
class ModerationException(val code: String) : Exception(code)

class SupabaseAdminService(private val client: SupabaseClient) : AdminApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun stats(): AdminStats =
        client.postgrest.rpc("admin_stats").decodeAs<AdminStats>()

    override suspend fun reports(status: String): List<AdminReport> =
        client.postgrest.rpc("admin_reports", buildJsonObject { put("p_status", status) }).decodeAs<List<AdminReport>>()

    override suspend fun searchUsers(query: String): List<AdminUser> =
        client.postgrest.rpc("admin_search_users", buildJsonObject { put("p_query", query.trim()) }).decodeAs<List<AdminUser>>()

    override suspend fun auditLog(limit: Int): List<AuditEntry> =
        client.postgrest.rpc("admin_audit_log", buildJsonObject { put("p_limit", limit) }).decodeAs<List<AuditEntry>>()

    override suspend fun moderate(action: ModerationAction, userId: String?, reportId: String?, note: String?) {
        val token = client.auth.currentSessionOrNull()?.accessToken ?: throw IllegalStateException("Not signed in")
        val body = buildJsonObject {
            put("action", action.wire)
            if (userId != null) put("user_id", userId)
            if (reportId != null) put("report_id", reportId)
            if (!note.isNullOrBlank()) put("note", note.trim().take(500))
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${AppConfig.SUPABASE_URL}/functions/v1/moderate-user")
            .addHeader("apikey", AppConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = runCatching {
                        Json.parseToJsonElement(response.body?.string().orEmpty())
                            .jsonObject["error"]?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    throw ModerationException(code ?: "http_${response.code}")
                }
            }
        }
    }
}
