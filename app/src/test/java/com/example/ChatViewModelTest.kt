package com.example

import com.example.data.ChatApi
import com.example.data.ChatTime
import com.example.data.MatchExpiry
import com.example.data.PeerEvent
import com.example.model.ChatMessage
import com.example.ui.screens.ChatSafety
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private class FakeChatApi(history: List<ChatMessage>) : ChatApi {
    val stored = history.toMutableList()
    val live = MutableSharedFlow<ChatMessage>()
    var markReadCalls = 0
    var failSendWith: String? = null

    override suspend fun loadMessages(matchId: String) = stored.toList()
    override suspend fun send(matchId: String, body: String): ChatMessage {
        failSendWith?.let { throw IllegalStateException(it) }
        return msg("sent-${stored.size}", ME, body, "2026-10-15T10:0${stored.size}:00Z").also { stored += it }
    }
    override suspend fun markRead(matchId: String) { markReadCalls++ }
    override fun changes(matchId: String): Flow<ChatMessage> = live

    val peer = MutableSharedFlow<PeerEvent>()
    var peerWatched = false
    var typingSent = 0
    override fun peerActivity(matchId: String): Flow<PeerEvent> { peerWatched = true; return peer }
    override suspend fun sendTyping(matchId: String) { typingSent++ }
}

private const val ME = "me"
private const val ANNA = "anna"

private fun msg(id: String, sender: String, body: String, at: String, read: Boolean = false) =
    ChatMessage(id = id, matchId = "m1", senderId = sender, body = body, timestamp = "", isRead = read, createdAt = at)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @Before
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsHistoryAndMarksIncomingAsRead() {
        val api = FakeChatApi(listOf(msg("1", ANNA, "Hallo!", "2026-10-15T09:00:00Z")))
        val vm = ChatViewModel("m1", ME, api)
        assertEquals(listOf("Hallo!"), vm.state.value.messages.map { it.body })
        assertEquals(1, api.markReadCalls)
    }

    @Test
    fun sentMessageAppearsOnce() {
        val api = FakeChatApi(emptyList())
        val vm = ChatViewModel("m1", ME, api)
        vm.send("  Hi Anna  ")
        assertEquals(listOf("Hi Anna"), vm.state.value.messages.map { it.body })
        assertFalse(vm.state.value.sending)
    }

    @Test
    fun liveMessagesAreMergedAndRepeatsIgnored() = kotlinx.coroutines.test.runTest {
        val api = FakeChatApi(listOf(msg("1", ANNA, "Hallo!", "2026-10-15T09:00:00Z")))
        val vm = ChatViewModel("m1", ME, api)
        api.live.emit(msg("2", ANNA, "Wie geht's?", "2026-10-15T09:05:00Z"))
        api.live.emit(msg("2", ANNA, "Wie geht's?", "2026-10-15T09:05:00Z"))
        assertEquals(listOf("1", "2"), vm.state.value.messages.map { it.id })
        assertEquals(2, api.markReadCalls)
    }

    @Test
    fun readReceiptUpdatesMyMessage() = kotlinx.coroutines.test.runTest {
        val mine = msg("1", ME, "Hi", "2026-10-15T09:00:00Z")
        val api = FakeChatApi(listOf(mine))
        val vm = ChatViewModel("m1", ME, api)
        assertFalse(vm.state.value.messages.single().isRead)
        api.live.emit(mine.copy(isRead = true))
        assertTrue(vm.state.value.messages.single().isRead)
        assertEquals("My own messages are never marked read by me", 0, api.markReadCalls)
    }

    @Test
    fun failedSendShowsError() {
        val api = FakeChatApi(emptyList()).apply { failSendWith = "offline" }
        val vm = ChatViewModel("m1", ME, api)
        vm.send("Hi")
        assertEquals("chat_send_failed", vm.state.value.errorKey)
        assertTrue(vm.state.value.messages.isEmpty())
    }

    @Test
    fun rateLimitHasItsOwnMessage() {
        val api = FakeChatApi(emptyList()).apply { failSendWith = "message_rate_limit" }
        val vm = ChatViewModel("m1", ME, api)
        vm.send("Hi")
        assertEquals("chat_rate_limited", vm.state.value.errorKey)
    }

    @Test
    fun blankAndTooLongMessagesAreNotSent() {
        val api = FakeChatApi(emptyList())
        val vm = ChatViewModel("m1", ME, api)
        vm.send("   ")
        vm.send("x".repeat(2001))
        assertTrue(api.stored.isEmpty())
    }

    @Test
    fun safetyHintForMoneyContactsAndLinks() {
        assertTrue(ChatSafety.looksRisky("Can you send me money via PayPal?"))
        assertTrue(ChatSafety.looksRisky("Schreib mir auf WhatsApp"))
        assertTrue(ChatSafety.looksRisky("my number is 0151 2345 6789"))
        assertTrue(ChatSafety.looksRisky("look at https://example.com"))
        assertFalse(ChatSafety.looksRisky("Wie war dein Sonntag?"))
    }

    @Test
    fun chatTimeFormatting() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals("14:20", ChatTime.format("2026-10-15T14:20:00+00:00", today, ZoneOffset.UTC))
        assertEquals("12.10.", ChatTime.format("2026-10-12T14:20:00Z", today, ZoneOffset.UTC))
        assertEquals("12.10.25", ChatTime.format("2025-10-12T14:20:00Z", today, ZoneOffset.UTC))
        assertEquals("14:20", ChatTime.format("2026-10-15 14:20:00.123+00", today, ZoneOffset.UTC))
        assertEquals("", ChatTime.format("not a date", today, ZoneOffset.UTC))
    }

    @Test
    fun peerOnlineAndTypingAreShownAndTypingFades() = kotlinx.coroutines.test.runTest {
        val api = FakeChatApi(emptyList())
        val vm = ChatViewModel("m1", ME, api)
        api.peer.emit(PeerEvent.Online(true))
        assertTrue(vm.state.value.peerOnline)
        api.peer.emit(PeerEvent.Typing)
        assertTrue(vm.state.value.peerTyping)
        testScheduler.advanceTimeBy(ChatViewModel.TYPING_SHOWN_MS + 1)
        assertFalse("typing disappears after a few seconds", vm.state.value.peerTyping)
        api.peer.emit(PeerEvent.Typing)
        api.live.emit(msg("9", ANNA, "Hallo", "2026-10-15T10:00:00Z"))
        assertFalse("their message ends their typing", vm.state.value.peerTyping)
        api.peer.emit(PeerEvent.Online(false))
        assertFalse(vm.state.value.peerOnline)
    }

    @Test
    fun typingSignalIsThrottled() {
        var now = 0L
        val api = FakeChatApi(emptyList())
        val vm = ChatViewModel("m1", ME, api, nowMs = { now })
        now = 10_000; vm.onDraftChanged("H")
        now = 11_000; vm.onDraftChanged("Ha")
        now = 12_000; vm.onDraftChanged("Hal")
        assertEquals(1, api.typingSent)
        now = 13_500; vm.onDraftChanged("Hall")
        assertEquals(2, api.typingSent)
        vm.onDraftChanged("   ")
        assertEquals(2, api.typingSent)
    }

    @Test
    fun hiddenActivitySharesAndSeesNothing() {
        val api = FakeChatApi(emptyList())
        val vm = ChatViewModel("m1", ME, api, shareActivity = false, nowMs = { 99_000 })
        vm.onDraftChanged("Hallo")
        assertFalse("does not join presence", api.peerWatched)
        assertEquals(0, api.typingSent)
        assertFalse(vm.state.value.peerOnline)
    }

    @Test
    fun expiredMatchHasItsOwnMessage() {
        val api = FakeChatApi(emptyList()).apply { failSendWith = "match_expired" }
        val vm = ChatViewModel("m1", ME, api)
        vm.send("Hi")
        assertEquals("chat_match_expired", vm.state.value.errorKey)
    }

    @Test
    fun daysLeftBeforeASilentMatchExpires() {
        val now = java.time.Instant.parse("2026-10-16T12:00:00Z")
        assertEquals(7, MatchExpiry.daysLeft("2026-10-23T12:00:00Z", now))
        assertEquals(3, MatchExpiry.daysLeft("2026-10-18T13:00:00Z", now))
        assertEquals(1, MatchExpiry.daysLeft("2026-10-16T18:00:00+00:00", now))
        assertEquals(1, MatchExpiry.daysLeft("2026-10-16T11:00:00Z", now))
        assertEquals(null, MatchExpiry.daysLeft("not a date", now))
    }
}
