package com.example.data

import com.example.model.ChatMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the other person in an open chat is doing. Only the two people of a match see this. */
sealed interface PeerEvent {
    data class Online(val online: Boolean) : PeerEvent
    data object Typing : PeerEvent
}

/** Chat on the server. An interface so the chat logic can be tested with a fake. */
interface ChatApi {
    suspend fun loadMessages(matchId: String): List<ChatMessage>
    suspend fun send(matchId: String, body: String): ChatMessage
    suspend fun markRead(matchId: String)
    /** New messages and read-state changes in one chat, live, until the collector stops. */
    fun changes(matchId: String): Flow<ChatMessage>

    /**
     * Whether the other person has this chat open, and when they type, until the collector
     * stops. Collecting also shows me as online to them, so call it only if I share my activity.
     */
    fun peerActivity(matchId: String): Flow<PeerEvent> = emptyFlow()

    /** Tells the other person (if they have the chat open) that I am typing. */
    suspend fun sendTyping(matchId: String) {}
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

    // Open presence channels by match, so sendTyping can use the one the chat screen joined.
    private val presenceChannels = ConcurrentHashMap<String, RealtimeChannel>()

    override fun peerActivity(matchId: String): Flow<PeerEvent> = channelFlow {
        val me = currentUserId()
        // Private channel: the server only lets the two people of this match join (migration 016).
        val channel = client.channel("presence-$matchId") {
            isPrivate = true
            presence { key = me }
        }
        val online = mutableSetOf<String>()
        // Collect before subscribing so the first presence state is not missed.
        launch(start = CoroutineStart.UNDISPATCHED) {
            channel.presenceChangeFlow().collect { action ->
                online += action.joins.keys
                online -= action.leaves.keys
                send(PeerEvent.Online((online - me).isNotEmpty()))
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            channel.broadcastFlow<JsonObject>(TYPING_EVENT).collect { msg ->
                if (msg["user"]?.jsonPrimitive?.contentOrNull != me) send(PeerEvent.Typing)
            }
        }
        try {
            // Refused (e.g. activity hidden) or offline: stay quiet instead of waiting forever.
            val joined = withTimeoutOrNull(JOIN_TIMEOUT_MS) { channel.subscribe(blockUntilSubscribed = true) } != null
            if (joined) {
                channel.track(buildJsonObject { put("online", true) })
                presenceChannels[matchId] = channel
            }
            awaitCancellation()
        } finally {
            presenceChannels.remove(matchId)
            withContext(NonCancellable) { client.realtime.removeChannel(channel) }
        }
    }

    override suspend fun sendTyping(matchId: String) {
        val channel = presenceChannels[matchId] ?: return
        runCatching { channel.broadcast(TYPING_EVENT, buildJsonObject { put("user", currentUserId()) }) }
    }

    companion object {
        private const val TYPING_EVENT = "typing"
        private const val JOIN_TIMEOUT_MS = 10_000L
        const val PAGE_SIZE = 200L
        private val MESSAGE_COLUMNS = listOf("id", "match_id", "sender_id", "body", "created_at", "read_at")
    }
}

/** Days left for a match nobody has written to yet (rounded up, at least 1 while it exists). */
object MatchExpiry {
    fun daysLeft(expiresAtIso: String, now: Instant = Instant.now()): Int? {
        val end = ChatTime.parse(expiresAtIso) ?: return null
        val hours = java.time.Duration.between(now, end).toHours()
        return ((hours + 23) / 24).toInt().coerceAtLeast(1)
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
