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
    @SerialName("met_unsafe_30d") val metUnsafe30d: Int = 0,
    /** The Germany location check at sign-up. Off only for testing; on by default (migration 024). */
    @SerialName("location_check") val locationCheck: Boolean = true,
    /** Profiles by gender, in total and created in the last 7 days (migration 033). */
    val men: Int = 0,
    val women: Int = 0,
    @SerialName("men_7d") val men7d: Int = 0,
    @SerialName("women_7d") val women7d: Int = 0,
    /** New accounts from men are paused to keep the balance; existing members are not affected. */
    @SerialName("male_signups_paused") val maleSignupsPaused: Boolean = false
) {
    /** Men per woman, e.g. 2.5; null while there are no women yet. */
    val menPerWoman: Double? get() = if (women > 0) men.toDouble() / women else null
}

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
    @SerialName("open_reports") val openReports: Int = 0,
    @SerialName("last_sign_in_at") val lastSignInAt: String? = null,
    @SerialName("email_confirmed") val emailConfirmed: Boolean = true,
    /** The one account above the admins; nobody can take its rights away. */
    @SerialName("is_owner") val isOwner: Boolean = false,
    /** True only when the person searching is the owner and this is another admin. */
    @SerialName("can_remove_admin") val canRemoveAdmin: Boolean = false
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
    /** Accounts by e-mail, name or id; [signedInOnly] leaves out accounts that never signed in. */
    suspend fun searchUsers(query: String, signedInOnly: Boolean = false): List<AdminUser>
    /** Makes an account an admin, or a normal user again; the server logs it. */
    suspend fun setAdmin(userId: String, admin: Boolean)
    /** Creates a complete account (admin-create-account); throws [ModerationException] with the server's reason. */
    suspend fun createAccount(account: NewAccount) {
        throw ModerationException("not_available")
    }
    suspend fun auditLog(limit: Int = 100): List<AuditEntry>
    suspend fun moderate(action: ModerationAction, userId: String? = null, reportId: String? = null, note: String? = null)
    /** Switches the Germany location check on or off; the server allows admins only and logs it. */
    suspend fun setLocationCheck(enabled: Boolean) {
        throw UnsupportedOperationException("admin_set_location_check")
    }
    /** Pauses or reopens new accounts from men; admins only, logged by the server. */
    suspend fun setMaleSignupsPaused(paused: Boolean) {
        throw UnsupportedOperationException("admin_set_male_signups")
    }
}

/** A moderation call refused by the server; [code] is e.g. "report_not_open" or "cannot_moderate_admin". */
class ModerationException(val code: String) : Exception(code)

/** One profile photo prepared on the phone (as for the normal profile): photo, small copy, blurred copy. */
class NewAccountPhoto(val jpeg: ByteArray, val thumb: ByteArray?, val blurred: ByteArray?, val focusX: Float, val focusY: Float, val zoom: Float)

/** An account an admin creates for someone who asked for it (admin panel → Create). */
class NewAccount(val details: com.example.core.admin.AccountTemplate.Result, val photos: List<NewAccountPhoto>)

/** Reasons the database gives for refusing to change admin rights. */
private val ADMIN_ERRORS = listOf("target_is_owner", "owner_only", "cannot_remove_self", "email_not_confirmed", "user_banned", "user_not_found", "not_allowed")

class SupabaseAdminService(private val client: SupabaseClient) : AdminApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Creating an account uploads its photos before answering.
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    override suspend fun stats(): AdminStats =
        client.postgrest.rpc("admin_stats").decodeAs<AdminStats>()

    override suspend fun reports(status: String): List<AdminReport> =
        client.postgrest.rpc("admin_reports", buildJsonObject { put("p_status", status) }).decodeAs<List<AdminReport>>()

    override suspend fun searchUsers(query: String, signedInOnly: Boolean): List<AdminUser> =
        client.postgrest.rpc("admin_search_accounts", buildJsonObject {
            put("p_query", query.trim())
            put("p_signed_in_only", signedInOnly)
        }).decodeAs<List<AdminUser>>()

    override suspend fun setAdmin(userId: String, admin: Boolean) {
        try {
            client.postgrest.rpc("admin_set_admin", buildJsonObject {
                put("p_user", userId)
                put("p_admin", admin)
            })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The database names the reason in its error text.
            val code = ADMIN_ERRORS.firstOrNull { e.message?.contains(it) == true }
            throw ModerationException(code ?: "failed")
        }
    }

    override suspend fun auditLog(limit: Int): List<AuditEntry> =
        client.postgrest.rpc("admin_audit_log", buildJsonObject { put("p_limit", limit) }).decodeAs<List<AuditEntry>>()

    override suspend fun setLocationCheck(enabled: Boolean) {
        client.postgrest.rpc("admin_set_location_check", buildJsonObject { put("p_enabled", enabled) })
    }

    override suspend fun setMaleSignupsPaused(paused: Boolean) {
        client.postgrest.rpc("admin_set_male_signups", buildJsonObject { put("p_paused", paused) })
    }

    override suspend fun createAccount(account: NewAccount) {
        val d = account.details
        val b64 = java.util.Base64.getEncoder()
        val body = buildJsonObject {
            put("email", d.email)
            put("password", d.password)
            put("lang", d.language)
            put("terms_version", AppConfig.TERMS_VERSION)
            put("privacy_version", AppConfig.PRIVACY_VERSION)
            put("conduct_version", AppConfig.CONDUCT_VERSION)
            put("sensitive_version", AppConfig.SENSITIVE_PROFILE_VERSION)
            put("profile", buildJsonObject {
                put("display_name", d.name)
                put("gender", d.gender)
                put("birth_date", d.birthDate.toString())
                put("city", d.city)
                put("region", d.region)
                d.heightCm?.let { put("height_cm", it) }
                if (d.religion.isNotBlank()) put("religion", d.religion)
                if (d.education.isNotBlank()) put("education", d.education)
                if (d.occupation.isNotBlank()) put("occupation", d.occupation)
                if (d.bio.isNotBlank()) put("bio", d.bio)
                if (d.marriageTimeline.isNotBlank()) put("marriage_timeline", d.marriageTimeline)
                if (d.wantsChildren.isNotBlank()) put("wants_children", d.wantsChildren)
                d.traits.forEach { (column, value) -> put(column, value) }
                put("languages", kotlinx.serialization.json.JsonArray(d.languages.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                put("interests", kotlinx.serialization.json.JsonArray(d.interests.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            })
            put("photos", kotlinx.serialization.json.JsonArray(account.photos.map { ph ->
                buildJsonObject {
                    put("jpeg", b64.encodeToString(ph.jpeg))
                    ph.thumb?.let { put("thumb", b64.encodeToString(it)) }
                    ph.blurred?.let { put("blurred", b64.encodeToString(it)) }
                    put("focus_x", ph.focusX)
                    put("focus_y", ph.focusY)
                    put("zoom", ph.zoom)
                }
            }))
        }
        callFunction("admin-create-account", body.toString())
    }

    override suspend fun moderate(action: ModerationAction, userId: String?, reportId: String?, note: String?) {
        val body = buildJsonObject {
            put("action", action.wire)
            if (userId != null) put("user_id", userId)
            if (reportId != null) put("report_id", reportId)
            if (!note.isNullOrBlank()) put("note", note.trim().take(500))
        }.toString()
        callFunction("moderate-user", body)
    }

    /** Calls an admin Edge Function as the signed-in admin; a refusal becomes [ModerationException]. */
    private suspend fun callFunction(name: String, json: String) {
        // This call does not go through the Supabase client, so renew a login that is about to expire here.
        val token = FreshSession.validToken(client) ?: throw IllegalStateException("Not signed in")
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${AppConfig.SUPABASE_URL}/functions/v1/$name")
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
