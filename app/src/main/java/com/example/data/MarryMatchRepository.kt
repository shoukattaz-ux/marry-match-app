package com.example.data

import android.util.Log
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.model.MatchItem
import com.example.model.ProfilePrompt
import com.example.model.PromptCatalog
import com.example.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

/** Sign-up needs a fresh pass from the location check; the user must verify their location again. */
class SignupPassRequiredException : IllegalStateException("A fresh location check is required before sign-up.")

@Serializable
private data class UserStatusRow(
    @SerialName("country_ok") val countryOk: Boolean = false,
    @SerialName("selfie_verified") val selfieVerified: Boolean = false,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("is_banned") val isBanned: Boolean = false
)

/** The account was banned by a moderator; the user is signed out. */
class AccountBannedException : IllegalStateException("Account banned")

@Serializable
private data class BlockRow(val blocked: String)

/** True for real account ids (UUIDs); the sample profiles use ids like "c1". */
internal fun isRealUserId(id: String): Boolean =
    runCatching { UUID.fromString(id) }.isSuccess && id.length == 36

class MarryMatchRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private var _supabase: SupabaseClient? = null
    val supabase: SupabaseClient? get() = _supabase

    private var _profileService: ProfileService? = null
    val profileService: ProfileService? get() = _profileService

    private var _discovery: DiscoveryApi? = null
    val discovery: DiscoveryApi? get() = _discovery

    private var _chat: ChatApi? = null
    val chat: ChatApi? get() = _chat

    // Signed links for the private profile photos, shared by all services.
    private var _photoLinks: PhotoUrlSigner? = null

    private var _verification: VerificationApi? = null
    val verification: VerificationApi? get() = _verification

    // The user's own selfie check (none, pending, approved, rejected), shown on the profile.
    private val _selfieStatus = MutableStateFlow(SelfieStatus(SelfieStatus.NONE))
    val selfieStatus: StateFlow<SelfieStatus> = _selfieStatus.asStateFlow()

    private val _isConfigured = MutableStateFlow(AppConfig.isConfigured)
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLanguage.EN)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    // App must start at Welcome screen, or Home (LOGGED_IN) if a real session exists
    private val _authState = MutableStateFlow(AuthState.WELCOME)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isPostLoginLocationGate = MutableStateFlow(false)
    val isPostLoginLocationGate: StateFlow<Boolean> = _isPostLoginLocationGate.asStateFlow()

    // One-time pass from check-germany, required by the server to create an account.
    private var signupPass: String? = null
    private var signupPassIssuedAtMs: Long = 0L

    // Default current user is an empty, unauthenticated user with isAdmin = false
    private val _currentUser = MutableStateFlow(createEmptyUser())
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _dailyProfiles = MutableStateFlow(SampleDataProvider.getDailyProfiles())
    val dailyProfiles: StateFlow<List<UserProfile>> = _dailyProfiles.asStateFlow()

    // Real matches from the server, with the last message and unread state for the chat list.
    private val _matches = MutableStateFlow<List<MatchItem>>(emptyList())
    val matches: StateFlow<List<MatchItem>> = _matches.asStateFlow()

    // How many people liked the user without a match yet (shown blurred on the Likes tab).
    private val _receivedLikesCount = MutableStateFlow(0)
    val receivedLikesCount: StateFlow<Int> = _receivedLikesCount.asStateFlow()


    private val _blockedUserIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedUserIds: StateFlow<Set<String>> = _blockedUserIds.asStateFlow()

    private var _admin: AdminApi? = null
    /** Moderator tools; the server refuses every call from accounts without is_admin. */
    val admin: AdminApi? get() = _admin

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

    // Privacy settings, stored on the server and applied there to what others see.
    private val _privacy = MutableStateFlow(PrivacySettings())
    val privacy: StateFlow<PrivacySettings> = _privacy.asStateFlow()

    private var _account: AccountApi? = null
    val account: AccountApi? get() = _account

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
                    install(Storage)
                    install(Realtime)
                }
                val photoLinks = PhotoUrlSigner.forBucket(_supabase!!, ProfileService.PHOTO_BUCKET)
                _photoLinks = photoLinks
                _profileService = ProfileService(_supabase!!, photoLinks)
                _discovery = SupabaseDiscoveryService(_supabase!!, photoLinks) { _currentUser.value.id }
                _chat = SupabaseChatService(_supabase!!) { _currentUser.value.id }
                _admin = SupabaseAdminService(_supabase!!)
                _account = SupabaseAccountService(_supabase!!) { _currentUser.value.id }
                _verification = SupabaseVerificationService(_supabase!!, _profileService!!, photoLinks) { _currentUser.value.id }
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
                applyAuthUser(user.id, user.email ?: "", user.userMetadata)
                routeAfterLogin(client, user.id)
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
        val pass = signupPass
        if (pass == null || System.currentTimeMillis() - signupPassIssuedAtMs > SIGNUP_PASS_MAX_AGE_MS) {
            signupPass = null
            return Result.failure(SignupPassRequiredException())
        }
        return try {
            // The server checks every value below again (handle_new_user trigger) and
            // refuses the account if the pass, age, email domain or consents are not valid.
            client.auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password.trim()
                data = buildJsonObject {
                    put("signup_pass", pass)
                    put("birth_date", birthDate)
                    put("terms_version", AppConfig.TERMS_VERSION)
                    put("privacy_version", AppConfig.PRIVACY_VERSION)
                    put("conduct_version", AppConfig.CONDUCT_VERSION)
                    put("location_version", AppConfig.LOCATION_NOTICE_VERSION)
                }
            }
            signupPass = null
            // Retain user registration data locally (still unverified until email confirmation)
            val y = birthDate.split("-").firstOrNull()?.toIntOrNull() ?: (java.time.LocalDate.now().year - age)
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
            // A refusal by the sign-up trigger arrives as a generic database error. The app already
            // checked age, email domain and consents, so the likely cause is the pass: ask for a new one.
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("database error saving new user") || msg.contains("unexpected_failure")) {
                signupPass = null
                Result.failure(SignupPassRequiredException())
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun resendSignupEmail(email: String): Result<Unit> {
        val client = _supabase ?: return Result.failure(
            IllegalStateException("Supabase is not configured.")
        )
        return try {
            client.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e("MarryMatchRepository", "Supabase resend email failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sends the user home, or to the location check when the server has no Germany check on
     * record yet (accounts created before the sign-up pass existed). Travellers who passed at
     * sign-up are never asked again.
     */
    private suspend fun routeAfterLogin(client: SupabaseClient, userId: String): Boolean {
        var banned = false
        val countryOk = try {
            val status = client.from("user_status")
                .select(columns = Columns.list("country_ok", "selfie_verified", "phone_verified", "is_admin", "is_banned")) {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<UserStatusRow>()
            if (status != null) applyStatus(status)
            banned = status?.isBanned == true
            status?.countryOk ?: false
        } catch (e: Throwable) {
            // The server enforces country_ok on every write anyway, so a failed read must not lock the user out.
            Log.w("MarryMatchRepository", "Could not read user_status: ${e.message}")
            true
        }
        if (banned) {
            signOut()
            return false
        }
        loadBlockedIds(client, userId)
        refreshSelfieStatus()
        if (countryOk) {
            _isPostLoginLocationGate.value = false
            routeByProfile(userId)
        } else {
            _isPostLoginLocationGate.value = true
            _authState.value = AuthState.LOCATION_GATE
        }
        return true
    }

    /** Badges and the admin flag come only from the server (user_status), never from the app. */
    private fun applyStatus(status: UserStatusRow) {
        _currentUser.value = _currentUser.value.copy(
            selfieVerified = status.selfieVerified,
            phoneVerified = status.phoneVerified,
            isAdmin = status.isAdmin
        )
    }

    /** Reloads the selfie check state and the blue tick, e.g. after submitting or on the profile screen. */
    suspend fun loadSelfieStatus() {
        val api = _verification ?: return
        val client = _supabase ?: return
        val userId = _currentUser.value.id.ifBlank { return }
        try {
            _selfieStatus.value = api.myStatus()
            client.from("user_status")
                .select(columns = Columns.list("country_ok", "selfie_verified", "phone_verified", "is_admin", "is_banned")) {
                    filter { eq("user_id", userId) }
                }
                .decodeSingleOrNull<UserStatusRow>()
                ?.let { applyStatus(it) }
        } catch (e: Throwable) {
            Log.w("MarryMatchRepository", "Could not load selfie status: ${e.message}")
        }
    }

    fun refreshSelfieStatus() {
        repositoryScope.launch { loadSelfieStatus() }
    }

    /** Starts the account fresh from the signed-in auth user (birth date comes from the sign-up data). */
    private fun applyAuthUser(id: String, email: String, metadata: JsonObject?) {
        val birthDate = metadata?.get("birth_date")?.jsonPrimitive?.contentOrNull.orEmpty()
        _currentUser.value = createEmptyUser().copy(
            id = id,
            email = email,
            displayName = metadata?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            birthDate = birthDate,
            birthYear = birthDate.take(4).toIntOrNull() ?: 0,
            age = ageFromBirthDate(birthDate)
        )
    }

    /** Opens onboarding until the profile is complete, otherwise loads it and opens the app. */
    private suspend fun routeByProfile(userId: String) {
        val service = _profileService
        val profile = try {
            service?.loadMyProfile(userId)
        } catch (e: Throwable) {
            // Do not lock the user out on a network error; the profile screen can be reloaded later.
            Log.w("MarryMatchRepository", "Could not load profile: ${e.message}")
            _authState.value = AuthState.LOGGED_IN
            return
        }
        if (profile == null || !profile.row.onboardingDone) {
            _authState.value = AuthState.ONBOARDING
        } else {
            applyProfile(profile)
            _authState.value = AuthState.LOGGED_IN
            refreshMatches()
        }
    }

    /** Reloads matches and the "likes you" count from the server. */
    suspend fun loadMatches() {
        val api = _discovery ?: return
        if (_currentUser.value.id.isBlank()) return
        try {
            _matches.value = api.getMatches().map { m ->
                MatchItem(
                    id = m.matchId,
                    matchedUser = m.user,
                    matchedAt = m.matchedAt,
                    lastMessage = m.lastMessage.orEmpty(),
                    lastMessageTime = m.lastMessageAt?.let { ChatTime.format(it) }.orEmpty(),
                    unread = m.unreadCount > 0
                )
            }
            _receivedLikesCount.value = api.receivedLikesCount()
        } catch (e: Throwable) {
            Log.w("MarryMatchRepository", "Could not load matches: ${e.message}")
        }
    }

    fun refreshMatches() {
        repositoryScope.launch { loadMatches() }
    }

    /** Called after a mutual like: loads the new match, then shows the celebration. */
    suspend fun onNewMatch(person: UserProfile) {
        loadMatches()
        _matchCelebration.value = person
    }

    /** Reloads the user's own profile from the server, e.g. after onboarding or editing. */
    suspend fun reloadMyProfile(): Result<Unit> {
        val service = _profileService ?: return Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = _currentUser.value.id.ifBlank { return Result.failure(IllegalStateException("Not signed in.")) }
        return try {
            service.loadMyProfile(userId)?.let { applyProfile(it) }
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /** Reloads the profile in the background (steps saved before closing the editor show up). */
    fun refreshMyProfile() {
        repositoryScope.launch { reloadMyProfile() }
    }

    fun onOnboardingFinished() {
        _authState.value = AuthState.LOGGED_IN
    }

    private fun applyProfile(profile: MyProfile) {
        val row = profile.row
        _currentUser.value = _currentUser.value.copy(
            displayName = row.displayName,
            birthDate = row.birthDate,
            birthYear = row.birthDate.take(4).toIntOrNull() ?: 0,
            age = ageFromBirthDate(row.birthDate),
            gender = row.gender,
            lookingFor = row.lookingFor,
            city = row.city.orEmpty(),
            region = row.region.orEmpty(),
            bio = row.bio.orEmpty(),
            marriageTimeline = row.marriageTimeline.orEmpty(),
            wantsChildren = row.wantsChildren.orEmpty(),
            religion = row.religion.orEmpty(),
            smoking = row.smoking.orEmpty(),
            education = row.education.orEmpty(),
            occupation = row.occupation.orEmpty(),
            languages = row.languages,
            interests = emptyList(),
            heightCm = row.heightCm ?: 0,
            onboardingDone = row.onboardingDone,
            photos = profile.photos.map { it.url },
            prompts = profile.prompts.map { answer ->
                val question = PromptCatalog.find(answer.key)
                ProfilePrompt(
                    promptKey = answer.key,
                    questionEn = question?.questionEn ?: answer.key,
                    questionDe = question?.questionDe ?: answer.key,
                    answer = answer.answer
                )
            }
        )
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
                applyAuthUser(user.id, user.email ?: email.trim(), user.userMetadata)
                if (routeAfterLogin(client, user.id)) Result.success(Unit) else Result.failure(AccountBannedException())
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
            _photoLinks?.clear()
            try {
                _supabase?.auth?.signOut()
            } catch (e: Throwable) {
                Log.w("MarryMatchRepository", "Supabase signOut error: ${e.message}")
            }
        }
        _isPostLoginLocationGate.value = false
        _currentUser.value = createEmptyUser()
        _blockedUserIds.value = emptySet()
        _matches.value = emptyList()
        _receivedLikesCount.value = 0
        _matchCelebration.value = null
        _selfieStatus.value = SelfieStatus(SelfieStatus.NONE)
        _privacy.value = PrivacySettings()
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

    fun onLocationGatePassed(signupPass: String? = null) {
        if (_isPostLoginLocationGate.value) {
            _isPostLoginLocationGate.value = false
            _authState.value = AuthState.LOGGED_IN
            val userId = _currentUser.value.id
            if (userId.isNotBlank()) {
                repositoryScope.launch { routeByProfile(userId) }
            }
        } else {
            this.signupPass = signupPass
            signupPassIssuedAtMs = System.currentTimeMillis()
            _authState.value = AuthState.SIGNUP
        }
    }

    fun hasValidSignupPass(): Boolean =
        signupPass != null && System.currentTimeMillis() - signupPassIssuedAtMs <= SIGNUP_PASS_MAX_AGE_MS

    /** Loads the privacy settings, e.g. when the settings screen opens. */
    suspend fun loadPrivacy() {
        val api = _account ?: return
        if (_currentUser.value.id.isBlank()) return
        try {
            _privacy.value = api.loadPrivacy()
        } catch (e: Throwable) {
            Log.w("MarryMatchRepository", "Could not load privacy settings: ${e.message}")
        }
    }

    /** Shows the change right away and saves it; on failure the old settings come back. */
    suspend fun updatePrivacy(change: (PrivacySettings) -> PrivacySettings): Result<Unit> {
        val api = _account ?: return Result.failure(IllegalStateException("Supabase is not configured."))
        val before = _privacy.value
        val after = change(before)
        _privacy.value = after
        return try {
            api.savePrivacy(after)
            Result.success(Unit)
        } catch (e: Throwable) {
            _privacy.value = before
            Result.failure(e)
        }
    }

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

    fun dismissCelebration() {
        _matchCelebration.value = null
    }

    /** Removes the match for both people (the server deletes the chat with it). */
    fun unmatch(matchId: String) {
        _matches.value = _matches.value.filter { it.id != matchId }

        val api = _discovery ?: return
        if (!isRealUserId(matchId)) return
        repositoryScope.launch {
            try {
                api.unmatch(matchId)
            } catch (e: Throwable) {
                Log.e("MarryMatchRepository", "Unmatch failed: ${e.message}", e)
                loadMatches()  // show the match again if the server still has it
            }
        }
    }

    /**
     * Hides the person everywhere right away and stores the block on the server. The server
     * then hides both people from each other (profiles, photos, likes) and deletes their match.
     */
    fun blockUser(userId: String) {
        _blockedUserIds.value = _blockedUserIds.value + userId
        _matches.value = _matches.value.filter { it.matchedUser.id != userId }
        _dailyProfiles.value = _dailyProfiles.value.filter { it.id != userId }

        val client = _supabase ?: return
        val me = _currentUser.value.id
        if (!isRealUserId(userId) || me.isBlank()) return  // sample profiles exist only on the phone
        repositoryScope.launch {
            try {
                client.from("blocks").insert(buildJsonObject {
                    put("blocker", me)
                    put("blocked", userId)
                })
            } catch (e: Throwable) {
                // Blocking the same person twice hits the primary key; anything else is logged.
                if (e.message?.contains("duplicate key") != true) {
                    Log.e("MarryMatchRepository", "Saving block failed: ${e.message}", e)
                }
            }
        }
    }

    /** Loads who the user has blocked, so they stay hidden after a restart. */
    private suspend fun loadBlockedIds(client: SupabaseClient, userId: String) {
        try {
            val rows = client.from("blocks")
                .select(columns = Columns.list("blocked")) { filter { eq("blocker", userId) } }
                .decodeList<BlockRow>()
            _blockedUserIds.value = rows.map { it.blocked }.toSet()
        } catch (e: Throwable) {
            Log.w("MarryMatchRepository", "Could not load blocks: ${e.message}")
        }
    }

    /**
     * Sends a report to the moderation queue. [reason] is one of the values allowed by the
     * reports table: fake, harassment, scam, underage, photo, other.
     */
    suspend fun submitReport(
        reportedUserId: String,
        reportedUserName: String,
        reason: String,
        details: String,
        messageId: String? = null
    ): Result<Unit> {
        val client = _supabase
        val me = _currentUser.value.id
        if (client != null && isRealUserId(reportedUserId) && me.isNotBlank()) {
            try {
                client.from("reports").insert(buildJsonObject {
                    put("reporter_id", me)
                    put("reported_id", reportedUserId)
                    put("reason", reason)
                    if (details.isNotBlank()) put("details", details.trim().take(1000))
                    if (messageId != null && isRealUserId(messageId)) put("message_id", messageId)
                })
            } catch (e: Throwable) {
                Log.e("MarryMatchRepository", "Report failed: ${e.message}", e)
                return Result.failure(e)
            }
        }
        return Result.success(Unit)
    }

    fun setVerifiedFilter(enabled: Boolean) {
        _verifiedOnlyFilter.value = enabled
    }

    fun setRegionFilter(region: String) {
        _selectedRegionFilter.value = region
    }

    /** Everything the server stores about the user, as JSON (GDPR Art. 15 and 20). */
    suspend fun exportMyData(): Result<String> {
        val api = _account ?: return Result.failure(IllegalStateException("Supabase is not configured."))
        return try {
            Result.success(api.exportData())
        } catch (e: Throwable) {
            Log.e("MarryMatchRepository", "Export failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Deletes the account on the server for good, then clears everything on the phone. */
    suspend fun deleteAccount(): Result<Unit> {
        val api = _account ?: return Result.failure(IllegalStateException("Supabase is not configured."))
        return try {
            api.deleteAccount()
            signOut()
            Result.success(Unit)
        } catch (e: Throwable) {
            Log.e("MarryMatchRepository", "Account deletion failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    companion object {
        // The server accepts a pass for 15 minutes; stop one minute early to allow for clock drift.
        private const val SIGNUP_PASS_MAX_AGE_MS = 14 * 60 * 1000L

        /** Full years since [birthDate] (YYYY-MM-DD), or 0 when it is missing or invalid. */
        fun ageFromBirthDate(birthDate: String): Int = try {
            java.time.Period.between(java.time.LocalDate.parse(birthDate), java.time.LocalDate.now()).years
        } catch (e: Exception) {
            0
        }

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
