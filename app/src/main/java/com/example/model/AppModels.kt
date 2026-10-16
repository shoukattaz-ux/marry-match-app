package com.example.model

data class MatchItem(
    val id: String,
    val matchedUser: UserProfile,
    val matchedAt: String,
    val lastMessage: String,
    val lastMessageTime: String,
    val unread: Boolean = false
)

data class ChatMessage(
    val id: String,
    val matchId: String,
    val senderId: String,
    val body: String,
    val timestamp: String,
    val isRead: Boolean = true,
    val createdAt: String = ""  // server time (ISO), used for ordering
)
