package com.example.data

import com.example.model.ChatMessage
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
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Chat on the server. An interface so the chat logic can be tested with a fake. */
interface ChatApi {
    suspend fun loadMessages(matchId: String): List<ChatMessage>
    suspend fun send(matchId: String, body: String): ChatMessage
    suspend fun markRead(matchId: String)
    /** New messages and read-state changes in one chat, live, until the collector stops. */
    fun changes(matchId: String): Flow<ChatMessage>
}

@Serializable
internal data class MessageRow(
    val id: String,
    @SerialName("match_id") val matchId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null
) {
    fun toChatMessage() = ChatMessage(
        id = id,
        matchId = matchId,
        senderId = senderId,
        body = body,
        timestamp = ChatTime.format(createdAt),
        isRead = readAt != null,
        createdAt = createdAt
    )
}

class SupabaseChatService(
    private val client: SupabaseClient,
    private val currentUserId: () -> String
) : ChatApi {

    override suspend fun loadMessages(matchId: String): List<ChatMessage> =
        client.from("messages")
            .select(columns = Columns.list(MESSAGE_COLUMNS)) {
                filter { eq("match_id", matchId) }
                order("created_at", Order.DESCENDING)
                limit(PAGE_SIZE)
            }
            .decodeList<MessageRow>()
            .map { it.toChatMessage() }
            .reversed()

    override suspend fun send(matchId: String, body: String): ChatMessage =
        client.from("messages")
            .insert(buildJsonObject {
                put("match_id", matchId)
                put("sender_id", currentUserId())
                put("body", body)
            }) { select(Columns.list(MESSAGE_COLUMNS)) }
            .decodeSingle<MessageRow>()
            .toChatMessage()

    override suspend fun markRead(matchId: String) {
        client.postgrest.rpc("mark_messages_read", buildJsonObject { put("p_match", matchId) })
    }

    override fun changes(matchId: String): Flow<ChatMessage> = flow {
        val channel = client.channel("chat-$matchId")
        // The flow must be set up before subscribing so no early event is missed.
        val updates = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("match_id", FilterOperator.EQ, matchId)
        }
        channel.subscribe()
        try {
            emitAll(updates.mapNotNull { action ->
                when (action) {
                    is PostgresAction.Insert -> action.decodeRecord<MessageRow>().toChatMessage()
                    is PostgresAction.Update -> action.decodeRecord<MessageRow>().toChatMessage()
                    else -> null
                }
            })
        } finally {
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }

    companion object {
        const val PAGE_SIZE = 200L
        private val MESSAGE_COLUMNS = listOf("id", "match_id", "sender_id", "body", "created_at", "read_at")
    }
}

/** Formats server timestamps for chat: "14:20" today, "12.10." earlier this year, else "12.10.25". */
object ChatTime {
    fun format(iso: String, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val instant = parse(iso) ?: return ""
        val local = instant.atZone(zone)
        return when {
            local.toLocalDate() == today -> local.format(DateTimeFormatter.ofPattern("HH:mm"))
            local.year == today.year -> local.format(DateTimeFormatter.ofPattern("dd.MM."))
            else -> local.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
        }
    }

    fun parse(iso: String): Instant? =
        runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(iso) }.getOrNull()
            // Postgres can send "2026-10-15 18:46:09.12+00" in realtime records
            ?: runCatching { OffsetDateTime.parse(iso.replace(' ', 'T').let { if (Regex("[+-]\\d\\d$").containsMatchIn(it)) "$it:00" else it }).toInstant() }.getOrNull()
}
