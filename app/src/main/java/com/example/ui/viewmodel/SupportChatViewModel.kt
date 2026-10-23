package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SupportApi
import com.example.data.SupportException
import com.example.data.SupportMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SupportChatUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val messages: List<SupportMessage> = emptyList(),
    val sending: Boolean = false,
    /** Localization key of an error to show, or null. */
    val errorKey: String? = null,
    /** False once the admin lost admin rights: the conversation can be read but not answered. */
    val open: Boolean = true,
    /** Member only: notifications for this conversation are off. */
    val muted: Boolean = false
)

/**
 * One admin conversation (admin messages): loads the history, then receives new messages and
 * read receipts live. [canWrite] is false for an admin reading another admin's conversation.
 */
class SupportChatViewModel(
    private val threadId: String,
    /** The member of this conversation (photos go into their folder). */
    private val memberId: String,
    private val myUserId: String,
    private val api: SupportApi?,
    val canWrite: Boolean,
    open: Boolean = true,
    muted: Boolean = false
) : ViewModel() {

    private val _state = MutableStateFlow(SupportChatUiState(open = open, muted = muted))
    val state: StateFlow<SupportChatUiState> = _state.asStateFlow()

    /** Only the two people in it mark messages read (another admin only reads along). */
    private val takesPart get() = canWrite

    init {
        load()
    }

    fun load() {
        val support = api ?: run {
            _state.update { it.copy(loading = false, loadFailed = true) }
            return
        }
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val history = support.messages(threadId)
                _state.update { it.copy(loading = false, messages = history) }
                if (takesPart && history.any { it.senderId != myUserId && it.readAt == null }) markRead()
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
        viewModelScope.launch {
            support.changes(threadId).catch { }.collect { onIncoming(it) }
        }
    }

    /** A new message or a read receipt, from the live connection or from sending. */
    fun onIncoming(message: SupportMessage) {
        _state.update { s ->
            val i = s.messages.indexOfFirst { it.id == message.id }
            val list = if (i >= 0) s.messages.toMutableList().also { it[i] = message } else (s.messages + message).sortedBy { it.createdAt }
            s.copy(messages = list)
        }
        if (takesPart && message.senderId != myUserId && message.readAt == null) markRead()
    }

    private fun markRead() {
        val support = api ?: return
        viewModelScope.launch { runCatching { support.markRead(threadId) } }
    }

    /** Sends [text] and/or a prepared JPEG [photo]; returns false when there was nothing to send. */
    fun send(text: String, photo: ByteArray? = null): Boolean {
        val support = api ?: return false
        val body = text.trim()
        if (!canWrite || !_state.value.open || _state.value.sending) return false
        if (body.isEmpty() && photo == null) return false
        if (body.length > MAX_LENGTH) {
            _state.update { it.copy(errorKey = "support_error_too_long") }
            return false
        }
        _state.update { it.copy(sending = true, errorKey = null) }
        viewModelScope.launch {
            try {
                val sent = support.send(threadId, memberId, body.ifEmpty { null }, photo)
                _state.update { it.copy(sending = false) }
                onIncoming(sent)
            } catch (e: SupportException) {
                _state.update {
                    it.copy(
                        sending = false,
                        open = it.open && e.code != "conversation_closed",
                        errorKey = when (e.code) {
                            "conversation_closed" -> "support_closed"
                            "too_many" -> "support_error_too_many"
                            "too_long" -> "support_error_too_long"
                            else -> "support_error_send"
                        }
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(sending = false, errorKey = "support_error_send") }
            }
        }
        return true
    }

    /** Member only: turns notifications for this conversation off or on. */
    fun setMuted(muted: Boolean) {
        val support = api ?: return
        val before = _state.value.muted
        _state.update { it.copy(muted = muted) }
        viewModelScope.launch {
            try {
                support.setMuted(threadId, muted)
            } catch (e: Throwable) {
                _state.update { it.copy(muted = before, errorKey = "support_error_send") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorKey = null) }

    companion object {
        const val MAX_LENGTH = 2000
    }
}
