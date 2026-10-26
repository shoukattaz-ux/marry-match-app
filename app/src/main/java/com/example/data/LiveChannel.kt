package com.example.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A Realtime channel that repairs itself. When the server refuses the join (for example a login
 * that had just expired) the library only logs it and the channel stays silent, so new messages
 * never arrived in an open chat. Here a join that does not succeed in time, or a channel that is
 * closed later, is replaced by a new one. [events] sets up the channel's flows before it joins.
 */
internal fun <T> SupabaseClient.liveChannel(topic: String, events: RealtimeChannel.() -> Flow<T>): Flow<T> = channelFlow {
    var attempt = 0
    while (isActive) {
        val channel = channel(topic)
        // The flows must exist before joining so no early event is missed.
        val forward = launch { channel.events().collect { send(it) } }
        try {
            channel.subscribe()
            val joined = withTimeoutOrNull(JOIN_TIMEOUT_MS) {
                channel.status.first { it == RealtimeChannel.Status.SUBSCRIBED }
            } != null
            if (joined) {
                attempt = 0
                // Stay until the channel is closed (server restart, lost connection), then join again.
                channel.status.first { it == RealtimeChannel.Status.UNSUBSCRIBED }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Join again below.
        } finally {
            forward.cancel()
            withContext(NonCancellable) { runCatching { realtime.removeChannel(channel) } }
        }
        attempt++
        delay(minOf(RETRY_BASE_MS * attempt, RETRY_MAX_MS))
    }
}

private const val JOIN_TIMEOUT_MS = 10_000L
private const val RETRY_BASE_MS = 2_000L
private const val RETRY_MAX_MS = 30_000L
