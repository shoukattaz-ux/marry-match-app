package com.example.data

import com.example.model.SignatureCard
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The member's own card: [unlocked] while they are selfie-verified (migrations 043, 047); [woman] picks the example text. */
@Serializable
data class MyCardState(val woman: Boolean = false, val unlocked: Boolean = false, val card: SignatureCard? = null)

/** The server's refusal, e.g. "card_contact_info"; the code is also the localization key. */
class SignatureCardException(val code: String) : Exception(code) {
    companion object {
        val REFUSALS = listOf(
            "card_women_only", "card_needs_verification", "card_bad_template", "card_looking_for_length",
            "card_bad_reasons", "card_vibe_length", "card_bad_emoji", "card_contact_info"
        )
    }
}

interface SignatureCardApi {
    suspend fun mine(): MyCardState
    suspend fun save(card: SignatureCard): MyCardState
    suspend fun delete()
}

class SupabaseSignatureCardService(private val client: SupabaseClient) : SignatureCardApi {
    override suspend fun mine(): MyCardState = refused {
        client.postgrest.rpc("get_my_profile_card").decodeAs<MyCardState>()
    }

    override suspend fun save(card: SignatureCard): MyCardState = refused {
        client.postgrest.rpc("save_profile_card", buildJsonObject {
            put("p_template", card.template)
            put("p_looking_for", card.lookingFor.trim())
            putJsonArray("p_reasons") { card.reasons.forEach { add(JsonPrimitive(it)) } }
            put("p_vibe_emoji", card.vibeEmoji?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_vibe", card.vibe?.trim()?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }).decodeAs<MyCardState>()
    }

    override suspend fun delete() {
        refused { client.postgrest.rpc("delete_profile_card") }
    }

    private suspend fun <T> refused(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        val code = SignatureCardException.REFUSALS.firstOrNull { e.message?.contains(it) == true }
        if (code != null) throw SignatureCardException(code) else throw e
    }
}
