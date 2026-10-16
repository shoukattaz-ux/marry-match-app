package com.example.data

import com.example.core.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Privacy settings stored on the profile; the server applies them to what others see. */
@Serializable
data class PrivacySettings(
    val incognito: Boolean = false,
    @SerialName("verified_viewers_only") val verifiedViewersOnly: Boolean = false,
    @SerialName("hide_city") val hideCity: Boolean = false,
    @SerialName("hide_religion") val hideReligion: Boolean = false,
    @SerialName("hide_occupation") val hideOccupation: Boolean = false
)

/** The user's own account: privacy settings, data export and deletion. */
interface AccountApi {
    suspend fun loadPrivacy(): PrivacySettings
    suspend fun savePrivacy(settings: PrivacySettings)
    /** All stored data about the user as pretty-printed JSON (GDPR Art. 15 and 20). */
    suspend fun exportData(): String
    /** Deletes the account on the server for good (GDPR Art. 17). */
    suspend fun deleteAccount()
}

class SupabaseAccountService(
    private val client: SupabaseClient,
    private val currentUserId: () -> String
) : AccountApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val pretty = Json { prettyPrint = true }

    override suspend fun loadPrivacy(): PrivacySettings =
        client.from("profiles")
            .select(columns = Columns.list(*COLUMNS.toTypedArray())) { filter { eq("id", currentUserId()) } }
            .decodeSingleOrNull<PrivacySettings>() ?: PrivacySettings()

    override suspend fun savePrivacy(settings: PrivacySettings) {
        client.from("profiles").update(buildJsonObject {
            put("incognito", settings.incognito)
            put("verified_viewers_only", settings.verifiedViewersOnly)
            put("hide_city", settings.hideCity)
            put("hide_religion", settings.hideReligion)
            put("hide_occupation", settings.hideOccupation)
        }) { filter { eq("id", currentUserId()) } }
    }

    override suspend fun exportData(): String {
        val data = client.postgrest.rpc("export_my_data").decodeAs<JsonElement>()
        return pretty.encodeToString(JsonElement.serializer(), data)
    }

    override suspend fun deleteAccount() {
        val token = client.auth.currentSessionOrNull()?.accessToken ?: throw IllegalStateException("Not signed in")
        val request = Request.Builder()
            .url("${AppConfig.SUPABASE_URL}/functions/v1/delete-account")
            .addHeader("apikey", AppConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .post("""{"confirm":true}""".toRequestBody("application/json".toMediaType()))
            .build()
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("delete-account failed: ${response.code}")
            }
        }
    }

    companion object {
        private val COLUMNS = listOf("incognito", "verified_viewers_only", "hide_city", "hide_religion", "hide_occupation")
    }
}
