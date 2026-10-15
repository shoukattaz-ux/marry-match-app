package com.example.data

import com.example.core.AppConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/** A started check: the pose the user must copy, valid for 30 minutes. */
data class SelfieTicket(val requestId: String, val pose: String)

/** The user's own verification state. [rejectReason] is set for a rejected request. */
data class SelfieStatus(val state: String, val rejectReason: String? = null) {
    companion object {
        const val NONE = "none"
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val REJECTED = "rejected"
    }
}

/** One selfie waiting for a moderator. URLs are filled in by the service. */
data class SelfieReviewItem(
    val requestId: String,
    val userId: String,
    val displayName: String,
    val age: Int,
    val city: String,
    val pose: String,
    val submittedAt: String,
    val selfiePath: String,
    val profilePhotoUrls: List<String>
)

/** Selfie verification on the server. An interface so the flow can be tested with a fake. */
interface VerificationApi {
    suspend fun giveConsent()
    suspend fun startCheck(): SelfieTicket
    suspend fun uploadAndSubmit(ticket: SelfieTicket, jpeg: ByteArray)
    suspend fun myStatus(): SelfieStatus

    // Moderators (user_status.is_admin) only; the server refuses everyone else.
    suspend fun reviewQueue(): List<SelfieReviewItem>
    suspend fun selfieUrl(item: SelfieReviewItem): String
    suspend fun review(requestId: String, approve: Boolean, reason: String?)
}

@Serializable
private data class TicketDto(@SerialName("request_id") val requestId: String, val pose: String)

@Serializable
private data class StatusDto(val status: String = SelfieStatus.NONE, @SerialName("reject_reason") val rejectReason: String? = null)

@Serializable
private data class QueueDto(
    @SerialName("request_id") val requestId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val age: Int = 0,
    val city: String? = null,
    val pose: String,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("selfie_path") val selfiePath: String,
    val photos: List<String> = emptyList()
)

class SupabaseVerificationService(
    private val client: SupabaseClient,
    private val profiles: ProfileService,
    private val currentUserId: () -> String
) : VerificationApi {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun giveConsent() {
        profiles.saveConsent(currentUserId(), "selfie_biometric", AppConfig.SELFIE_CONSENT_VERSION)
    }

    override suspend fun startCheck(): SelfieTicket {
        val dto = client.postgrest.rpc("start_selfie_check").decodeAs<TicketDto>()
        return SelfieTicket(dto.requestId, dto.pose)
    }

    override suspend fun uploadAndSubmit(ticket: SelfieTicket, jpeg: ByteArray) {
        // The private bucket only accepts this exact name for the user's open request.
        try {
            client.storage.from(SELFIE_BUCKET).upload("${currentUserId()}/${ticket.requestId}.jpg", jpeg) {
                contentType = ContentType.Image.JPEG
                upsert = false
            }
        } catch (e: Throwable) {
            // A retry after a failed submit finds the file already there; submit it as it is.
            if (e.message?.contains("already exists", ignoreCase = true) != true) throw e
        }
        client.postgrest.rpc("submit_selfie", buildJsonObject { put("p_request", ticket.requestId) })
    }

    override suspend fun myStatus(): SelfieStatus {
        val dto = client.postgrest.rpc("my_selfie_status").decodeAs<StatusDto>()
        return SelfieStatus(dto.status, dto.rejectReason)
    }

    override suspend fun reviewQueue(): List<SelfieReviewItem> {
        val photoBucket = client.storage.from(ProfileService.PHOTO_BUCKET)
        return client.postgrest.rpc("get_selfie_queue").decodeAs<List<QueueDto>>().map { q ->
            SelfieReviewItem(
                requestId = q.requestId,
                userId = q.userId,
                displayName = q.displayName,
                age = q.age,
                city = q.city.orEmpty(),
                pose = q.pose,
                submittedAt = q.submittedAt,
                selfiePath = q.selfiePath,
                profilePhotoUrls = q.photos.map { photoBucket.publicUrl(it) }
            )
        }
    }

    /** Short-lived link, so a selfie is only visible while the moderator has it open. */
    override suspend fun selfieUrl(item: SelfieReviewItem): String =
        client.storage.from(SELFIE_BUCKET).createSignedUrl(item.selfiePath, 10.minutes)

    override suspend fun review(requestId: String, approve: Boolean, reason: String?) {
        val token = client.auth.currentSessionOrNull()?.accessToken ?: throw IllegalStateException("Not signed in")
        val body = buildJsonObject {
            put("request_id", requestId)
            put("approve", approve)
            if (reason != null) put("reason", reason)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${AppConfig.SUPABASE_URL}/functions/v1/review-selfie")
            .addHeader("apikey", AppConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()
        withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("review-selfie failed: ${response.code}")
            }
        }
    }

    companion object {
        const val SELFIE_BUCKET = "selfies"
        val REJECT_REASONS = listOf("face_not_clear", "pose_mismatch", "not_same_person", "other")
    }
}
