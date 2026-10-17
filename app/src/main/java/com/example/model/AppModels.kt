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
    /** The text, or a photo/video caption ("" when there is none). */
    val body: String,
    val timestamp: String,
    val isRead: Boolean = true,
    val createdAt: String = "",  // server time (ISO), used for ordering
    /** "text", "image", "voice" or "video" */
    val type: String = "text",
    /** Set for photos, voice messages and videos. */
    val media: MessageMedia? = null
)

/** Where a media message's file is on the server and its state (migration 023). */
data class MessageMedia(
    val path: String,
    val thumbPath: String?,
    val bytes: Long?,
    val durationMs: Long?,
    /** "uploaded", "downloaded" or "expired" */
    val status: String
)
