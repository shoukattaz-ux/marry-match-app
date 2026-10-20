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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUiState(
    val stats: AdminStats? = null,
    val reports: List<AdminReport> = emptyList(),
    val reportStatus: String = "open",
    val users: List<AdminUser> = emptyList(),
    val userQuery: String = "",
    val audit: List<AuditEntry> = emptyList(),
    val loading: Boolean = false,
    /** Id of the report or user an action is running for. */
    val workingOn: String? = null,
    /** Localization key of the last error, or null. */
    val errorKey: String? = null,
    /** Localization key of the last success message, or null. */
    val doneKey: String? = null
)

/** Moderator panel (blueprint Phase 9): numbers, reports, user search and the audit log. */
class AdminViewModel(private val api: AdminApi?) : ViewModel() {

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
        val u = a.searchUsers(_state.value.userQuery)
        _state.update { it.copy(users = u) }
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

    fun clearMessages() = _state.update { it.copy(errorKey = null, doneKey = null) }

    /** Runs one moderation action, then reloads what it changed. */
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
            val users = if (s.users.isNotEmpty()) admin.searchUsers(s.userQuery) else s.users
            val stats = admin.stats()
            _state.update { it.copy(reports = reports, users = users, stats = stats) }
        } catch (e: Throwable) {
            // The action itself is done; the lists update on the next reload.
        }
    }
}
