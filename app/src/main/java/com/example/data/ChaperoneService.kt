package com.example.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A fresh invite code for a chaperone; valid until [expiresAt], usable once. */
data class ChaperoneInvite(val id: String, val code: String, val expiresAt: String)

/** The member's own chaperone: an open invitation ("pending") or a chaperone who reads along ("active"). */
@Serializable
data class MyChaperone(
    val id: String,
    val status: String,
    val name: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null
) {
    val isActive: Boolean get() = status == "active"
}

/** A member this user is chaperone for, with the member's matches. */
@Serializable
data class Ward(
    val id: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("member_name") val memberName: String,
    val since: String? = null,
    val matches: List<WardMatch> = emptyList()
)

@Serializable
data class WardMatch(
    @SerialName("match_id") val matchId: String,
    val name: String,
    val age: Int = 0,
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

/** A message in a member's chat as the chaperone sees it: read-only, sent after they joined. */
@Serializable
data class WardMessage(
    val id: String,
    @SerialName("from_member") val fromMember: Boolean,
    val body: String,
    @SerialName("sent_at") val sentAt: String
)

/** Chaperone mode on the server (migration 020). An interface so the screen logic can be tested. */
interface ChaperoneApi {
    suspend fun invite(): ChaperoneInvite
    /** Accepts a code with the chaperone's own name; returns the member's name. */
    suspend fun accept(code: String, yourName: String): String
    /** Ends an invitation or chaperone; the member and the chaperone can both do this. */
    suspend fun end(id: String)
    suspend fun myChaperone(): MyChaperone?
    suspend fun myWards(): List<Ward>
    suspend fun wardMessages(matchId: String): List<WardMessage>

    companion object {
        /** Error codes the server raises; anything else is shown as a general error. */
        val KNOWN_ERRORS = listOf(
            "invalid_code", "own_code", "too_many_members", "name_required",
            "already_has_chaperone", "profile_required", "not_allowed"
        )

        /** Localization key for a failed call, e.g. "chaperone_error_invalid_code". */
        fun errorKey(e: Throwable): String {
            val text = e.message.orEmpty()
            return KNOWN_ERRORS.firstOrNull { text.contains(it) }?.let { "chaperone_error_$it" } ?: "chaperone_error_general"
        }
    }
}

@Serializable
private data class InviteDto(val id: String, val code: String, @SerialName("expires_at") val expiresAt: String)

@Serializable
private data class AcceptDto(val id: String, @SerialName("member_name") val memberName: String? = null)

class SupabaseChaperoneService(private val client: SupabaseClient) : ChaperoneApi {

    override suspend fun invite(): ChaperoneInvite {
        val dto = client.postgrest.rpc("guardian_invite").decodeAs<InviteDto>()
        return ChaperoneInvite(dto.id, dto.code, dto.expiresAt)
    }

    override suspend fun accept(code: String, yourName: String): String =
        client.postgrest.rpc("guardian_accept", buildJsonObject {
            put("p_code", code)
            put("p_name", yourName)
        }).decodeAs<AcceptDto>().memberName.orEmpty()

    override suspend fun end(id: String) {
        client.postgrest.rpc("guardian_end", buildJsonObject { put("p_id", id) })
    }

    override suspend fun myChaperone(): MyChaperone? {
        // The function returns SQL null when there is nothing, which arrives as JSON null.
        val json = client.postgrest.rpc("get_my_chaperone").decodeAs<JsonElement>()
        if (json is JsonNull) return null
        return lenient.decodeFromJsonElement(MyChaperone.serializer(), json)
    }

    override suspend fun myWards(): List<Ward> =
        client.postgrest.rpc("get_my_wards").decodeAs<List<Ward>>()

    override suspend fun wardMessages(matchId: String): List<WardMessage> =
        client.postgrest.rpc("get_ward_messages", buildJsonObject { put("p_match_id", matchId) })
            .decodeAs<List<WardMessage>>()

    private companion object {
        val lenient = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
