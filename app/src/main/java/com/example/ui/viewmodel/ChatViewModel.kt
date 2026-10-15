package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatApi
import com.example.data.ChatTime
import com.example.model.ChatMessage
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
    val errorKey: String? = null
)

/**
 * One conversation (blueprint Phase 7): loads the history, then receives new messages and
 * read receipts live. Incoming messages are marked as read while the chat is open.
 */
class ChatViewModel(
    private val matchId: String,
    private val myUserId: String,
    private val api: ChatApi?
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var liveStarted = false

    init {
        load()
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
                    _state.update { s -> s.copy(messages = merge(s.messages, listOf(message))) }
                    markIncomingRead(listOf(message))
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
                val key = if (e.message?.contains("message_rate_limit") == true) "chat_rate_limited" else "chat_send_failed"
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

        /** Adds or replaces messages by id (a live update can repeat a message we already have). */
        fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
            val byId = LinkedHashMap<String, ChatMessage>()
            current.forEach { byId[it.id] = it }
            incoming.forEach { byId[it.id] = it }
            return byId.values.sortedBy { ChatTime.parse(it.createdAt) }
        }
    }
}
