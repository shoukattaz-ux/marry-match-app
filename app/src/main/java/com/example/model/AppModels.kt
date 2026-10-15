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

data class VerificationRequest(
    val id: String,
    val userId: String,
    val userName: String,
    val userAge: Int,
    val userCity: String,
    val profilePhoto: String,
    val pose: String,
    val selfiePhotoUrl: String,
    val status: String, // 'pending' | 'approved' | 'rejected'
    val rejectReason: String? = null,
    val createdAt: String = "Just now"
)

data class ReportItem(
    val id: String,
    val reporterId: String,
    val reportedUserId: String,
    val reportedUserName: String,
    val reason: String, // 'fake' | 'harassment' | 'scam' | 'underage' | 'other'
    val details: String,
    val status: String, // 'open' | 'actioned' | 'dismissed'
    val createdAt: String = "Just now"
)
