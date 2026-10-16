package com.example.model

data class MatchItem(
    val id: String,
    val matchedUser: UserProfile,
    val matchedAt: String,
    val lastMessage: String,
    val lastMessageTime: String,
    val unread: Boolean = false,
    /** Days left before a match nobody has written to disappears (null once someone wrote). */
    val expiresInDays: Int? = null,
    /** One of the two has a chaperone (family member) who can read this chat. */
    val chaperoned: Boolean = false,
    /** "We met": I confirmed meeting in person / the other person did. */
    val iMet: Boolean = false,
    val theyMet: Boolean = false
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
