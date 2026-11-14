package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminApi
import com.example.data.AdminReport
import com.example.data.AdminStats
import com.example.data.AdminUser
import com.example.data.AuditEntry
import com.example.data.ModerationAction
import com.example.data.ModerationException
import com.example.data.SupportApi
import com.example.data.SupportThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Create account" form: pasted text, what was understood, photos, and the result. */
data class CreateAccountState(
    val text: String = "",
    /** What the last "Check" understood; null until checked or after the text changed. */
    val checked: com.example.core.admin.AccountTemplate.Result? = null,
    val photos: List<com.example.data.NewAccountPhoto> = emptyList(),
    val preparingPhotos: Boolean = false,
    /** Boost the new profile from the start for this many hours (0: no boost). */
    val boostHours: Int = 0,
    /** Created with the verified badge (the admin confirmed the person matches the photos). */
    val verified: Boolean = false,
    val creating: Boolean = false,
    /** Localization key of the last error, or null. */
    val errorKey: String? = null,
    /** Login details of the account just created (shown with a copy button). */
    val created: CreatedAccount? = null
) {
    val canCreate: Boolean
        get() = checked?.ok == true && photos.size in MIN_PHOTOS..MAX_PHOTOS && !creating && !preparingPhotos

    companion object {
        const val MIN_PHOTOS = 3
        const val MAX_PHOTOS = 6
        /** Boost lengths offered in the admin panel, in hours: 6 hours, 1, 3 and 7 days. */
        val BOOST_CHOICES = listOf(6, 24, 72, 168)
    }
}

data class CreatedAccount(val name: String, val email: String, val password: String, val verified: Boolean = false)

data class AdminUiState(
    val stats: AdminStats? = null,
    val reports: List<AdminReport> = emptyList(),
    val reportStatus: String = "open",
    val users: List<AdminUser> = emptyList(),
    val userQuery: String = "",
    /** Search only accounts that have signed in at least once. */
    val signedInOnly: Boolean = false,
    val audit: List<AuditEntry> = emptyList(),
    val loading: Boolean = false,
    /** Id of the report or user an action is running for. */
    val workingOn: String? = null,
    /** Localization key of the last error, or null. */
    val errorKey: String? = null,
    /** Localization key of the last success message, or null. */
    val doneKey: String? = null,
    /** Admin messages: every conversation between an admin and a member. */
    val supportThreads: List<SupportThread> = emptyList(),
    /** The conversation shown on screen, or null. */
    val openThread: SupportThread? = null,
    /** The "Create account" form, or null while it is closed. */
    val create: CreateAccountState? = null
)

/** Moderator panel (blueprint Phase 9): numbers, reports, user search and the audit log. */
class AdminViewModel(private val api: AdminApi?, private val support: SupportApi? = null) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        loadStats()
    }

    private fun load(block: suspend (AdminApi) -> Unit) {
        val admin = api ?: run {
            _state.update { it.copy(errorKey = "admin_load_failed") }
            return
        }
        _state.update { it.copy(loading = true, errorKey = null) }
        viewModelScope.launch {
            try {
                block(admin)
                _state.update { it.copy(loading = false) }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, errorKey = "admin_load_failed") }
            }
        }
    }

    fun loadStats() = load { a -> val s = a.stats(); _state.update { it.copy(stats = s) } }

    fun loadReports(status: String = _state.value.reportStatus) = load { a ->
        val r = a.reports(status)
        _state.update { it.copy(reports = r, reportStatus = status) }
    }

    fun setUserQuery(query: String) = _state.update { it.copy(userQuery = query) }

    fun searchUsers() = load { a ->
        val u = a.searchUsers(_state.value.userQuery, _state.value.signedInOnly)
        _state.update { it.copy(users = u) }
    }

    fun setSignedInOnly(only: Boolean) {
        _state.update { it.copy(signedInOnly = only) }
        searchUsers()
    }

    /** Makes [user] an admin or a normal user again; the screen has already asked to confirm. */
    fun setAdmin(user: AdminUser, admin: Boolean) =
        act(user.userId, if (admin) "admin_done_made_admin" else "admin_done_removed_admin") {
            it.setAdmin(user.userId, admin)
        }

    fun loadAudit() = load { a -> val l = a.auditLog(); _state.update { it.copy(audit = l) } }

    fun dismissReport(report: AdminReport) = act(report.reportId, "admin_done_dismissed") {
        it.moderate(ModerationAction.DISMISS, reportId = report.reportId)
    }

    fun warnReported(report: AdminReport) = act(report.reportId, "admin_done_warned") {
        it.moderate(ModerationAction.WARN, reportId = report.reportId)
    }

    fun banReported(report: AdminReport, note: String?) = act(report.reportId, "admin_done_banned") {
        it.moderate(ModerationAction.BAN, userId = report.reportedId, reportId = report.reportId, note = note ?: report.reason)
    }

    fun setBanned(user: AdminUser, banned: Boolean, note: String?) =
        act(user.userId, if (banned) "admin_done_banned" else "admin_done_unbanned") {
            it.moderate(if (banned) ModerationAction.BAN else ModerationAction.UNBAN, userId = user.userId, note = note)
        }

    /** The location check switch: the screen has already asked the admin to confirm switching it off. */
    fun setLocationCheck(enabled: Boolean) =
        act("location_check", if (enabled) "admin_done_location_on" else "admin_done_location_off") {
            it.setLocationCheck(enabled)
        }

    /** Pausing new accounts from men: the screen has already asked the admin to confirm. */
    fun setMaleSignupsPaused(paused: Boolean) =
        act("male_signups", if (paused) "admin_done_male_paused" else "admin_done_male_open") {
            it.setMaleSignupsPaused(paused)
        }

    /** How long "Recently online" is shown after "Online" ends. */
    fun setRecentMinutes(minutes: Int) =
        act("recent_minutes", "admin_done_recent_minutes") { it.setRecentMinutes(minutes.coerceIn(MIN_RECENT_MINUTES, MAX_RECENT_MINUTES)) }

    /** How long members stay "Online" after leaving the app. */
    fun setOnlineMinutes(minutes: Int) =
        act("online_minutes", "admin_done_online_minutes") { it.setOnlineMinutes(minutes.coerceIn(MIN_ONLINE_MINUTES, MAX_ONLINE_MINUTES)) }

    /** Free likes per day for every account (0 = no limit). */
    fun setLikeLimit(limit: Int) =
        act("like_limit", "admin_done_like_limit") { it.setLikeLimit(limit.coerceIn(0, MAX_LIKE_LIMIT)) }

    fun clearMessages() = _state.update { it.copy(errorKey = null, doneKey = null) }

    fun loadSupportThreads() {
        val s = support ?: return
        _state.update { it.copy(loading = true, errorKey = null) }
        viewModelScope.launch {
            try {
                val threads = s.adminThreads()
                _state.update { it.copy(loading = false, supportThreads = threads) }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, errorKey = "admin_load_failed") }
            }
        }
    }

    /** Opens (or starts) this admin's own conversation with [user]. */
    fun messageUser(user: AdminUser) {
        val s = support ?: return
        if (_state.value.workingOn != null) return
        _state.update { it.copy(workingOn = user.userId, errorKey = null, doneKey = null) }
        viewModelScope.launch {
            try {
                val id = s.openThread(user.userId)
                val threads = runCatching { s.adminThreads() }.getOrNull() ?: _state.value.supportThreads
                // A new conversation has no message yet, so it is not in the list until the first one.
                val thread = threads.firstOrNull { it.threadId == id } ?: SupportThread(
                    threadId = id,
                    otherId = user.userId,
                    name = user.displayName.ifBlank { user.email },
                    email = user.email,
                    mine = true
                )
                _state.update { it.copy(workingOn = null, supportThreads = threads, openThread = thread) }
            } catch (e: Throwable) {
                _state.update { it.copy(workingOn = null, errorKey = "support_error_open") }
            }
        }
    }

    fun openCreate() = _state.update { it.copy(create = CreateAccountState()) }

    fun closeCreate() = _state.update { it.copy(create = null) }

    fun setCreateText(text: String) = updateCreate { it.copy(text = text, checked = null, errorKey = null) }

    /** Reads the pasted text and shows what was understood. */
    fun checkCreate(today: java.time.LocalDate = java.time.LocalDate.now()) = updateCreate {
        it.copy(checked = com.example.core.admin.AccountTemplate.parse(it.text, today), errorKey = null)
    }

    fun setCreateBoost(hours: Int) = updateCreate { it.copy(boostHours = hours) }

    fun setCreateVerified(verified: Boolean) = updateCreate { it.copy(verified = verified) }

    /** Gives [user] more visibility in Discover for [hours]; 0 ends a running boost. */
    fun setBoost(user: AdminUser, hours: Int) =
        act(user.userId, if (hours > 0) "admin_done_boosted" else "admin_done_boost_ended") { it.setBoost(user.userId, hours) }

    fun setPreparingPhotos(busy: Boolean) = updateCreate { it.copy(preparingPhotos = busy) }

    fun addCreatePhotos(photos: List<com.example.data.NewAccountPhoto>, failed: Boolean = false) = updateCreate {
        val all = it.photos + photos
        it.copy(
            photos = all.take(CreateAccountState.MAX_PHOTOS),
            preparingPhotos = false,
            errorKey = when {
                failed -> "create_err_photo"
                all.size > CreateAccountState.MAX_PHOTOS -> "create_err_photo_limit"
                else -> null
            }
        )
    }

    fun removeCreatePhoto(index: Int) = updateCreate { s -> s.copy(photos = s.photos.filterIndexed { i, _ -> i != index }) }

    /** Creates the account from the checked text and the photos. */
    fun createAccount(today: java.time.LocalDate = java.time.LocalDate.now()) {
        val admin = api ?: return
        val form = _state.value.create ?: return
        // Read again: what is created is exactly what the box says now.
        val details = com.example.core.admin.AccountTemplate.parse(form.text, today)
        if (!details.ok) {
            updateCreate { it.copy(checked = details) }
            return
        }
        if (!form.canCreate) return
        updateCreate { it.copy(creating = true, errorKey = null) }
        viewModelScope.launch {
            val error = try {
                admin.createAccount(com.example.data.NewAccount(details, form.photos, form.boostHours, form.verified))
                null
            } catch (e: ModerationException) {
                CREATE_ERRORS[e.code] ?: "create_srv_failed"
            } catch (e: Throwable) {
                "create_srv_failed"
            }
            updateCreate {
                if (error == null) {
                    it.copy(creating = false, created = CreatedAccount(details.name, details.email, details.password, form.verified))
                } else {
                    it.copy(creating = false, errorKey = error)
                }
            }
            if (error == null && _state.value.users.isNotEmpty()) {
                runCatching { admin.searchUsers(_state.value.userQuery, _state.value.signedInOnly) }
                    .getOrNull()?.let { users -> _state.update { it.copy(users = users) } }
            }
        }
    }

    /** After a success: an empty form for the next account. */
    fun createAnother() = _state.update { it.copy(create = CreateAccountState()) }

    private fun updateCreate(change: (CreateAccountState) -> CreateAccountState) =
        _state.update { s -> s.create?.let { s.copy(create = change(it)) } ?: s }

    fun openSupportThread(thread: SupportThread) = _state.update { it.copy(openThread = thread) }

    /** Back from a conversation: the list shows its newest message and unread count. */
    fun closeSupportThread() {
        _state.update { it.copy(openThread = null) }
        loadSupportThreads()
    }

    /** Runs one moderation action, then reloads what it changed. */
    companion object {
        const val MAX_LIKE_LIMIT = 100
        const val MIN_RECENT_MINUTES = 3
        const val MAX_RECENT_MINUTES = 1440
        const val MIN_ONLINE_MINUTES = 1
        const val MAX_ONLINE_MINUTES = 240

        /** Server reasons from admin-create-account to texts. */
        val CREATE_ERRORS = mapOf(
            "email_exists" to "create_srv_email_exists",
            "invalid_email" to "create_err_email",
            "weak_password" to "create_err_password",
            "underage" to "create_err_underage",
            "photos_count" to "create_err_photo_count",
            "invalid_photo" to "create_err_photo",
            "too_many" to "create_srv_too_many",
            "not_allowed" to "create_srv_not_allowed"
        )
    }

    private fun act(id: String, doneKey: String, call: suspend (AdminApi) -> Unit) {
        val admin = api ?: return
        if (_state.value.workingOn != null) return
        _state.update { it.copy(workingOn = id, errorKey = null, doneKey = null) }
        viewModelScope.launch {
            val error = try {
                call(admin)
                null
            } catch (e: ModerationException) {
                when (e.code) {
                    "cannot_moderate_admin" -> "admin_error_admin"
                    "report_not_open" -> "admin_error_already_done"
                    "cannot_remove_self" -> "admin_error_remove_self"
                    "target_is_owner" -> "admin_error_target_owner"
                    "owner_only" -> "admin_error_owner_only"
                    "email_not_confirmed" -> "admin_error_not_confirmed"
                    "user_banned" -> "admin_error_banned"
                    else -> "admin_action_failed"
                }
            } catch (e: Throwable) {
                "admin_action_failed"
            }
            _state.update { it.copy(workingOn = null, errorKey = error, doneKey = if (error == null) doneKey else null) }
            refreshAfterAction(admin)
        }
    }

    private suspend fun refreshAfterAction(admin: AdminApi) {
        try {
            val s = _state.value
            val reports = admin.reports(s.reportStatus)
            val users = if (s.users.isNotEmpty()) admin.searchUsers(s.userQuery, s.signedInOnly) else s.users
            val stats = admin.stats()
            _state.update { it.copy(reports = reports, users = users, stats = stats) }
        } catch (e: Throwable) {
            // The action itself is done; the lists update on the next reload.
        }
    }
}
