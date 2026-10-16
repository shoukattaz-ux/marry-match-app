package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatApi
import com.example.data.ChatTime
import com.example.data.PeerEvent
import com.example.model.ChatMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    /** Localization key of an error to show, or null. */
    val errorKey: String? = null,
    /** The other person has this chat open right now. */
    val peerOnline: Boolean = false,
    /** The other person is typing (shown for a few seconds after each signal). */
    val peerTyping: Boolean = false
)

/**
 * One conversation (blueprint Phase 7): loads the history, then receives new messages and
 * read receipts live. Incoming messages are marked as read while the chat is open.
 */
class ChatViewModel(
    private val matchId: String,
    private val myUserId: String,
    private val api: ChatApi?,
    /** False when the user hides their activity: then nobody sees them online and they see no one. */
    private val shareActivity: Boolean = true,
    private val nowMs: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var liveStarted = false
    private var typingShownJob: Job? = null
    private var lastTypingSentMs = 0L

    init {
        load()
        if (shareActivity) watchPeer()
    }

    private fun watchPeer() {
        val chat = api ?: return
        viewModelScope.launch {
            chat.peerActivity(matchId)
                .catch { /* presence is a nice-to-have; the chat works without it */ }
                .collect { event ->
                    when (event) {
                        is PeerEvent.Online -> _state.update {
                            it.copy(peerOnline = event.online, peerTyping = it.peerTyping && event.online)
                        }
                        PeerEvent.Typing -> {
                            _state.update { it.copy(peerOnline = true, peerTyping = true) }
                            typingShownJob?.cancel()
                            typingShownJob = viewModelScope.launch {
                                delay(TYPING_SHOWN_MS)
                                _state.update { it.copy(peerTyping = false) }
                            }
                        }
                    }
                }
        }
    }

    /** Call on every change of the text field; sends at most one typing signal every few seconds. */
    fun onDraftChanged(text: String) {
        val chat = api ?: return
        if (!shareActivity || text.isBlank()) return
        val now = nowMs()
        if (now - lastTypingSentMs < TYPING_SEND_EVERY_MS) return
        lastTypingSentMs = now
        viewModelScope.launch { runCatching { chat.sendTyping(matchId) } }
    }

    fun load() {
        val chat = api ?: run {
            _state.update { it.copy(loading = false, loadFailed = true) }
            return
        }
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val history = chat.loadMessages(matchId)
                _state.update { s -> s.copy(loading = false, messages = merge(s.messages, history)) }
                markIncomingRead(history)
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
        if (liveStarted) return
        liveStarted = true
        viewModelScope.launch {
            chat.changes(matchId)
                .catch { /* live updates stop; the history stays and the user can reopen the chat */ }
                .collect { message ->
                    // A live update can repeat a message we already have; only new ones are marked read.
                    val isNew = _state.value.messages.none { it.id == message.id }
                    // A new message from them ends their "typing".
                    val fromPeer = isNew && message.senderId != myUserId
                    _state.update { s ->
                        s.copy(messages = merge(s.messages, listOf(message)), peerTyping = s.peerTyping && !fromPeer)
                    }
                    if (isNew) markIncomingRead(listOf(message))
                }
        }
    }

    fun send(text: String) {
        val body = text.trim()
        val chat = api ?: return
        if (body.isEmpty() || body.length > MAX_LENGTH || _state.value.sending) return
        _state.update { it.copy(sending = true, errorKey = null) }
        viewModelScope.launch {
            try {
                val sent = chat.send(matchId, body)
                _state.update { s -> s.copy(sending = false, messages = merge(s.messages, listOf(sent))) }
            } catch (e: Throwable) {
                val msg = e.message.orEmpty()
                val key = when {
                    "message_rate_limit" in msg -> "chat_rate_limited"
                    "match_expired" in msg -> "chat_match_expired"
                    else -> "chat_send_failed"
                }
                _state.update { it.copy(sending = false, errorKey = key) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorKey = null) }
    }

    private suspend fun markIncomingRead(messages: List<ChatMessage>) {
        if (messages.none { it.senderId != myUserId && !it.isRead }) return
        runCatching { api?.markRead(matchId) }
    }

    companion object {
        const val MAX_LENGTH = 2000
        const val TYPING_SHOWN_MS = 4_000L
        const val TYPING_SEND_EVERY_MS = 3_000L

        /** Adds or replaces messages by id (a live update can repeat a message we already have). */
        fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
            val byId = LinkedHashMap<String, ChatMessage>()
            current.forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            return byId.values.sortedBy { ChatTime.parse(it.createdAt) }
        }
    }
}
