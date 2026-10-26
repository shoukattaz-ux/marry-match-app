package com.example.push

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The conversation (match or admin conversation) the user is looking at right now, while the app
 * is in the foreground. A push for it shows no notification; the conversation reloads instead, so
 * the message appears even when the live connection missed it.
 */
object OpenConversation {
    @Volatile
    var id: String? = null
        private set

    private val _arrived = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Ids of open conversations a push announced something new for. */
    val arrived: SharedFlow<String> = _arrived.asSharedFlow()

    fun opened(conversationId: String) {
        id = conversationId
    }

    fun closed(conversationId: String) {
        if (id == conversationId) id = null
    }

    /** True when [conversationId] is on screen: it is told to reload and no notification is needed. */
    fun onPush(conversationId: String?): Boolean {
        if (conversationId.isNullOrBlank() || conversationId != id) return false
        _arrived.tryEmit(conversationId)
        return true
    }
}
