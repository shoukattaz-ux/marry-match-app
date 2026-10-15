package com.example.data

import android.util.Log
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.model.ChatMessage
import com.example.model.MatchItem
import com.example.model.ReportItem
import com.example.model.UserProfile
import com.example.model.VerificationRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class AuthState {
    WELCOME,
    SIGNUP,
    LOGIN,
    FORGOT_PASSWORD,
    LOCATION_GATE,
    LOCATION_REJECTED,
    ONBOARDING,
    LOGGED_IN
}

class MarryMatchRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private var _supabase: SupabaseClient? = null
    val supabase: SupabaseClient? get() = _supabase

    private val _isConfigured = MutableStateFlow(AppConfig.isConfigured)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.EN)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // App must start at Welcome screen, or Home (LOGGED_IN) if a real session exists
    private val _authState = MutableStateFlow(AuthState.WELCOME)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isPostLoginLocationGate = MutableStateFlow(false)
    val isPostLoginLocationGate: StateFlow<Boolean> = _isPostLoginLocationGate.asStateFlow()

    // Default current user is an empty, unauthenticated user with isAdmin = false
    private val _currentUser = MutableStateFlow(createEmptyUser())
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _dailyProfiles = MutableStateFlow(SampleDataProvider.getDailyProfiles())
    val dailyProfiles: StateFlow<List<UserProfile>> = _dailyProfiles.asStateFlow()

    private val _currentProfileIndex = MutableStateFlow(0)
    val currentProfileIndex: StateFlow<Int> = _currentProfileIndex.asStateFlow()

    private val _matches = MutableStateFlow(SampleDataProvider.getInitialMatches())
    val matches: StateFlow<List<MatchItem>> = _matches.asStateFlow()

    private val _chatMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(
        mapOf(
            "match_1" to SampleDataProvider.getInitialMessages("match_1"),
            "match_2" to SampleDataProvider.getInitialMessages("match_2")
        )
    )
    val chatMessages: StateFlow<Map<String, List<ChatMessage>>> = _chatMessages.asStateFlow()

    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private val _verificationQueue = MutableStateFlow(SampleDataProvider.getPendingVerifications())
    val verificationQueue: StateFlow<List<VerificationRequest>> = _verificationQueue.asStateFlow()

    private val _reports = MutableStateFlow(SampleDataProvider.getInitialReports())
    val reports: StateFlow<List<ReportItem>> = _reports.asStateFlow()

    private val _matchCelebration = MutableStateFlow<UserProfile?>(null)
    val matchCelebration: StateFlow<UserProfile?> = _matchCelebration.asStateFlow()

    private val _verifiedOnlyFilter = MutableStateFlow(false)
    val verifiedOnlyFilter: StateFlow<Boolean> = _verifiedOnlyFilter.asStateFlow()

    private val _selectedRegionFilter = MutableStateFlow("All")
    val selectedRegionFilter: StateFlow<String> = _selectedRegionFilter.asStateFlow()

    // Notification toggles
    var notifyNewMatch: Boolean = true
    var notifyNewMessage: Boolean = true
    var notifyLikes: Boolean = true
    var notifyDailyProfiles: Boolean = true

    // Privacy & Visibility Settings (GDPR compliance)
    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _visibleOnlyToVerified = MutableStateFlow(false)
    val visibleOnlyToVerified: StateFlow<Boolean> = _visibleOnlyToVerified.asStateFlow()

    private val _showExactCity = MutableStateFlow(true)
    val showExactCity: StateFlow<Boolean> = _showExactCity.asStateFlow()

    private val _showReligionOnProfile = MutableStateFlow(true)
    val showReligionOnProfile: StateFlow<Boolean> = _showReligionOnProfile.asStateFlow()

    private val _showOccupationOnProfile = MutableStateFlow(true)
    val showOccupationOnProfile: StateFlow<Boolean> = _showOccupationOnProfile.asStateFlow()

    private val _sendReadReceipts = MutableStateFlow(true)
    val sendReadReceipts: StateFlow<Boolean> = _sendReadReceipts.asStateFlow()

    private val _analyticsConsent = MutableStateFlow(false)
    val analyticsConsent: StateFlow<Boolean> = _analyticsConsent.asStateFlow()

    init {
        initializeSupabase()
    }

    fun initializeSupabase() {
        val configured = AppConfig.isConfigured
        _isConfigured.value = configured
        if (configured) {
            try {
                _supabase = createSupabaseClient(
                    supabaseUrl = AppConfig.SUPABASE_URL,
                    supabaseKey = AppConfig.SUPABASE_ANON_KEY
                ) {
                    install(Auth)
                    install(Postgrest)
                }
                repositoryScope.launch {
                    checkInitialSession()
                }
            } catch (e: Throwable) {
                Log.e("MarryMatchRepository", "Failed to create Supabase client: ${e.message}", e)
            }
        }
    }

    fun checkSupabaseConfiguration() {
        initializeSupabase()
    }

    suspend fun checkInitialSession() {
        val client = _supabase ?: return
        try {
            val session = client.auth.currentSessionOrNull()
            if (session != null && session.user != null) {
                val user = session.user!!
                val userEmail = user.email ?: ""
                _currentUser.value = _currentUser.value.copy(
                    id = user.id,
                    email = userEmail,
                    displayName = user.userMetadata?.get("display_name")?.toString()?.replace("\"", "") ?: userEmail.substringBefore("@"),
                    isAdmin = false,
                    onboardingDone = true
                )
                _authState.value = AuthState.LOGGED_IN
            } else {
                _authState.value = AuthState.WELCOME
            }
        } catch (e: Throwable) {
            Log.w("MarryMatchRepository", "Session restoration check failed: ${e.message}")
            _authState.value = AuthState.WELCOME
        }
    }

    suspend fun signUpWithSupabase(
        email: String,
        password: String,
        birthDate: String,
        age: Int
    ): Result<Unit> {
        val client = _supabase ?: return Result.failure(
            IllegalStateException("Supabase is not configured. Please add SUPABASE_URL and SUPABASE_ANON_KEY.")
        )
        return try {
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password.trim()
            }
            // Retain user registration data locally (still unverified until email confirmation)
            val y = birthDate.split("-").firstOrNull()?.toIntOrNull() ?: (2026 - age)
            _currentUser.value = _currentUser.value.copy(
                email = email.trim(),
                birthDate = birthDate,
                birthYear = y,
                age = age,
                isAdmin = false,
                selfieVerified = false,
                phoneVerified = false
            )
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e("MarryMatchRepository", "Supabase signUpWith failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithSupabase(email: String, password: String): Result<Unit> {
        val client = _supabase ?: return Result.failure(
            IllegalStateException("Supabase is not configured. Please add SUPABASE_URL and SUPABASE_ANON_KEY.")
        )
        return try {
            client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password.trim()
            }
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                val userEmail = user.email ?: email.trim()
                _currentUser.value = _currentUser.value.copy(
                    id = user.id,
                    email = userEmail,
                    displayName = user.userMetadata?.get("display_name")?.toString()?.replace("\"", "") ?: userEmail.substringBefore("@"),
                    isAdmin = false,
                    onboardingDone = true
                )
                _isPostLoginLocationGate.value = true
                _authState.value = AuthState.LOCATION_GATE
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("No valid user session returned by Supabase."))
            }
        } catch (e: Throwable) {
            Log.e("MarryMatchRepository", "Supabase signInWith failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun resetPasswordForEmail(email: String): Result<Unit> {
        val client = _supabase ?: return Result.failure(
            IllegalStateException("Supabase is not configured.")
        )
        return try {
            client.auth.resetPasswordForEmail(email.trim())
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e("MarryMatchRepository", "Supabase reset password failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun signOut() {
        repositoryScope.launch {
            try {
                _supabase?.auth?.signOut()
            } catch (e: Throwable) {
                Log.w("MarryMatchRepository", "Supabase signOut error: ${e.message}")
            }
        }
        _isPostLoginLocationGate.value = false
        _currentUser.value = createEmptyUser()
        _authState.value = AuthState.WELCOME
    }

    fun getCurrentAccessToken(): String? {
        return try {
            _supabase?.auth?.currentSessionOrNull()?.accessToken
        } catch (e: Throwable) {
            null
        }
    }

    fun setPostLoginLocationGate(value: Boolean) {
        _isPostLoginLocationGate.value = value
    }

    fun onLocationGatePassed() {
        if (_isPostLoginLocationGate.value) {
            _isPostLoginLocationGate.value = false
            _authState.value = AuthState.LOGGED_IN
        } else {
            _authState.value = AuthState.SIGNUP
        }
    }

    fun setIncognito(enabled: Boolean) { _isIncognito.value = enabled }
    fun setVisibleOnlyToVerified(enabled: Boolean) { _visibleOnlyToVerified.value = enabled }
    fun setShowExactCity(enabled: Boolean) { _showExactCity.value = enabled }
    fun setShowReligionOnProfile(enabled: Boolean) { _showReligionOnProfile.value = enabled }
    fun setShowOccupationOnProfile(enabled: Boolean) { _showOccupationOnProfile.value = enabled }
    fun setSendReadReceipts(enabled: Boolean) { _sendReadReceipts.value = enabled }
    fun setAnalyticsConsent(enabled: Boolean) { _analyticsConsent.value = enabled }

    fun setLanguage(lang: AppLanguage) {
        _appLanguage.value = lang
    }

    fun toggleLanguage() {
        _appLanguage.value = if (_appLanguage.value == AppLanguage.EN) AppLanguage.DE else AppLanguage.EN
    }

    fun setAuthState(state: AuthState) {
        if (state != AuthState.LOCATION_GATE && state != AuthState.LOCATION_REJECTED) {
            _isPostLoginLocationGate.value = false
        }
        _authState.value = state
    }

    fun updateCurrentUser(updater: (UserProfile) -> UserProfile) {
        _currentUser.value = updater(_currentUser.value)
    }

    fun passCurrentProfile() {
        if (_currentProfileIndex.value < _dailyProfiles.value.size) {
            _currentProfileIndex.value += 1
        }
    }

    fun likeCurrentProfile(candidate: UserProfile, comment: String? = null) {
        if (candidate.id == "c1" || candidate.id == "c2") {
            val newMatch = MatchItem(
                id = "match_${UUID.randomUUID()}",
                matchedUser = candidate,
                matchedAt = "Just now",
                lastMessage = comment?.takeIf { it.isNotBlank() } ?: "Matched with you!",
                lastMessageTime = "Now",
                unread = true
            )
            _matches.value = listOf(newMatch) + _matches.value
            _matchCelebration.value = candidate
        }
        _currentProfileIndex.value += 1
    }

    fun dismissCelebration() {
        _matchCelebration.value = null
    }

    fun sendMessage(matchId: String, text: String) {
        val currentList = _chatMessages.value[matchId] ?: emptyList()
        val newMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            matchId = matchId,
            senderId = _currentUser.value.id,
            body = text,
            timestamp = "Just now"
        )
        val updated = currentList + newMessage
        _chatMessages.value = _chatMessages.value + (matchId to updated)
    }

    fun unmatch(matchId: String) {
        _matches.value = _matches.value.filter { it.id != matchId }
        val updatedMap = _chatMessages.value.toMutableMap()
        updatedMap.remove(matchId)
        _chatMessages.value = updatedMap
    }

    fun blockUser(userId: String) {
        _blockedUserIds.value = _blockedUserIds.value + userId
        _matches.value = _matches.value.filter { it.matchedUser.id != userId }
        _dailyProfiles.value = _dailyProfiles.value.filter { it.id != userId }
    }

    fun submitReport(reportedUserId: String, reportedUserName: String, reason: String, details: String) {
        val report = ReportItem(
            id = "rep_${UUID.randomUUID()}",
            reporterId = _currentUser.value.id,
            reportedUserId = reportedUserId,
            reportedUserName = reportedUserName,
            reason = reason,
            details = details,
            status = "open"
        )
        _reports.value = listOf(report) + _reports.value
    }

    fun submitSelfieVerification(pose: String, photoUri: String? = null, autoApprove: Boolean = false) {
        val request = VerificationRequest(
            id = "ver_${UUID.randomUUID()}",
            userId = _currentUser.value.id,
            userName = _currentUser.value.displayName,
            userAge = _currentUser.value.age,
            userCity = _currentUser.value.city,
            profilePhoto = _currentUser.value.photos.firstOrNull() ?: "",
            pose = pose,
            selfiePhotoUrl = photoUri ?: (_currentUser.value.photos.firstOrNull() ?: ""),
            status = if (autoApprove) "approved" else "pending"
        )
        if (autoApprove) {
            _currentUser.value = _currentUser.value.copy(selfieVerified = true)
            _dailyProfiles.value = _dailyProfiles.value.map {
                if (it.id == _currentUser.value.id) it.copy(selfieVerified = true) else it
            }
        }
        _verificationQueue.value = listOf(request) + _verificationQueue.value
    }

    fun completeSelfieVerification(pose: String, photoUri: String? = null) {
        submitSelfieVerification(pose = pose, photoUri = photoUri, autoApprove = true)
    }

    fun reviewSelfie(requestId: String, approve: Boolean, reason: String? = null) {
        val req = _verificationQueue.value.find { it.id == requestId } ?: return
        if (approve) {
            if (req.userId == _currentUser.value.id) {
                _currentUser.value = _currentUser.value.copy(selfieVerified = true)
            }
            _dailyProfiles.value = _dailyProfiles.value.map {
                if (it.id == req.userId) it.copy(selfieVerified = true) else it
            }
        }
        _verificationQueue.value = _verificationQueue.value.filter { it.id != requestId }
    }

    fun setVerifiedFilter(enabled: Boolean) {
        _verifiedOnlyFilter.value = enabled
    }

    fun setRegionFilter(region: String) {
        _selectedRegionFilter.value = region
    }

    fun exportDataJson(): String {
        val user = _currentUser.value
        return """
        {
          "gdpr_export": {
            "account_id": "${user.id}",
            "email": "${user.email}",
            "display_name": "${user.displayName}",
            "birth_date": "${user.birthDate}",
            "age": ${user.age},
            "gender": "${user.gender}",
            "city": "${user.city}",
            "region": "${user.region}",
            "marriage_timeline": "${user.marriageTimeline}",
            "wants_children": "${user.wantsChildren}",
            "religion": "${user.religion}",
            "occupation": "${user.occupation}",
            "languages": ${user.languages.joinToString(prefix = "[\"", separator = "\", \"", postfix = "\"]")},
            "selfie_verified": ${user.selfieVerified},
            "phone_verified": ${user.phoneVerified},
            "respect_score": ${user.respectScore},
            "exported_at": "${System.currentTimeMillis()}"
          }
        }
        """.trimIndent()
    }

    fun deleteAccount() {
        signOut()
        _currentUser.value = createEmptyUser().copy(
            displayName = "Deleted Account",
            isBanned = true
        )
        _matches.value = emptyList()
        _chatMessages.value = emptyMap()
        _authState.value = AuthState.WELCOME
    }

    companion object {
        fun createEmptyUser(): UserProfile = UserProfile(
            id = "",
            email = "",
            displayName = "",
            birthDate = "",
            birthYear = 1998,
            age = 0,
            gender = "man",
            lookingFor = "woman",
            city = "",
            region = "",
            bio = "",
            marriageTimeline = "1_2y",
            wantsChildren = "yes",
            selfieVerified = false,
            phoneVerified = false,
            isAdmin = false,
            onboardingDone = false
        )
    }
}
