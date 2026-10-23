package com.example.data

import com.example.model.PhotoFocus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * A conversation between an admin and a member (admin messages, migration 037). For the member,
 * the other side is the admin; for admins, the member.
 */
@Serializable
data class SupportThread(
    @SerialName("thread_id") val threadId: String,
    /** The member's id for admins, the admin's id for the member. */
    @SerialName("other_id") val otherId: String,
    val name: String = "",
    /** Admins only: the member's e-mail. */
    val email: String = "",
    /** Admins only: which admin writes in this conversation. */
    @SerialName("admin_name") val adminName: String = "",
    /** Admins only: this admin's own conversation (only then can they write in it). */
    val mine: Boolean = false,
    /** Storage path of the other side's main photo (signed by the screen). */
    val photo: String? = null,
    @SerialName("photo_focus") val photoFocus: PhotoFocus? = null,
    /** False once the admin lost admin rights: then nobody can write any more. */
    val open: Boolean = true,
    val muted: Boolean = false,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_has_photo") val lastHasPhoto: Boolean = false,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_from_me") val lastFromMe: Boolean = false,
    val unread: Int = 0
)

/** A conversation in the member's chat list, with the admin's photo link and a short time. */
data class SupportThreadItem(val thread: SupportThread, val photoUrl: String?, val time: String)

@Serializable
data class SupportMessage(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null
)

interface SupportApi {
    /** The member's conversations with admins (only ones with at least one message). */
    suspend fun myThreads(): List<SupportThread>
    /** Admins: every admin conversation. */
    suspend fun adminThreads(): List<SupportThread>
    /** Admins: the conversation with [memberId], made on first use; returns its id. */
    suspend fun openThread(memberId: String): String
    suspend fun messages(threadId: String): List<SupportMessage>
    /** Sends text and/or one JPEG photo; the photo goes into the member's folder. */
    suspend fun send(threadId: String, memberId: String, body: String?, photoJpeg: ByteArray?): SupportMessage
    suspend fun markRead(threadId: String)
    suspend fun setMuted(threadId: String, muted: Boolean)
    /** New messages (and read receipts) of one conversation while it is open. */
    fun changes(threadId: String): Flow<SupportMessage>
    /** A short-lived link to a message photo, or null. */
    suspend fun photoUrl(path: String): String?
}

/** Refused by the server; [code] is the reason it gave (e.g. "conversation_closed", "too_many"). */
class SupportException(val code: String) : Exception(code)

class SupabaseSupportService(private val client: SupabaseClient) : SupportApi {

    private val photoLinks = PhotoUrlSigner.forBucket(client, BUCKET)

    override suspend fun myThreads(): List<SupportThread> =
        client.postgrest.rpc("my_support_threads").decodeAs<List<SupportThread>>()

    override suspend fun adminThreads(): List<SupportThread> =
        client.postgrest.rpc("admin_support_threads").decodeAs<List<SupportThread>>()

    override suspend fun openThread(memberId: String): String = refusals {
        client.postgrest.rpc("admin_open_support", buildJsonObject { put("p_user", memberId) }).decodeAs<String>()
    }

    override suspend fun messages(threadId: String): List<SupportMessage> =
        client.from("support_messages")
            .select(columns = Columns.list(COLUMNS)) {
                filter { eq("thread_id", threadId) }
                order("created_at", Order.DESCENDING)
                limit(PAGE_SIZE)
            }
            .decodeList<SupportMessage>()
            .reversed()

    override suspend fun send(threadId: String, memberId: String, body: String?, photoJpeg: ByteArray?): SupportMessage = refusals {
        val path = photoJpeg?.let { bytes ->
            val p = "$memberId/${UUID.randomUUID()}.jpg"
            client.storage.from(BUCKET).upload(p, bytes) { contentType = io.ktor.http.ContentType.Image.JPEG }
            p
        }
        client.postgrest.rpc("support_send", buildJsonObject {
            put("p_thread", threadId)
            put("p_body", body?.trim()?.ifBlank { null })
            put("p_photo_path", path)
        }).decodeAs<SupportMessage>()
    }

    override suspend fun markRead(threadId: String) {
        client.postgrest.rpc("support_mark_read", buildJsonObject { put("p_thread", threadId) })
    }

    override suspend fun setMuted(threadId: String, muted: Boolean) {
        client.postgrest.rpc("support_set_muted", buildJsonObject {
            put("p_thread", threadId)
            put("p_muted", muted)
        })
    }

    override fun changes(threadId: String): Flow<SupportMessage> = flow {
        val channel = client.channel("support-$threadId")
        // The flow must be set up before subscribing so no early event is missed.
        val updates = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "support_messages"
            filter("thread_id", FilterOperator.EQ, threadId)
        }
        channel.subscribe()
        try {
            emitAll(updates.mapNotNull { action ->
                when (action) {
                    is PostgresAction.Insert -> action.decodeRecord<SupportMessage>()
                    is PostgresAction.Update -> action.decodeRecord<SupportMessage>()
                    else -> null
                }
            })
        } finally {
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }

    override suspend fun photoUrl(path: String): String? = photoLinks.url(path)

    /** Turns the database's refusal ("raise exception 'too_many'") into a [SupportException]. */
    private suspend fun <T> refusals(block: suspend () -> T): T = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        val code = REFUSALS.firstOrNull { e.message?.contains(it) == true }
        if (code != null) throw SupportException(code) else throw e
    }

    companion object {
        const val BUCKET = "support-media"
        private const val PAGE_SIZE = 200L
        private const val COLUMNS = "id,thread_id,sender_id,body,photo_path,created_at,read_at"
        private val REFUSALS = listOf(
            "conversation_closed", "too_many", "too_long", "empty_message", "not_yourself", "user_not_found", "not_allowed"
        )
    }
}
