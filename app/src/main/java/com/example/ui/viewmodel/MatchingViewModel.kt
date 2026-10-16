package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiscoveryApi
import com.example.data.DiscoveryPrefs
import com.example.data.MarryMatchRepository
import com.example.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the user liked: the whole profile, or one photo or prompt (optionally with a comment). */
data class LikeTarget(val type: String? = null, val id: String? = null, val label: String? = null)

data class DiscoverUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** How many people today's set had in total (for "7 of 10 left today"). */
    val total: Int = 0,
    /** Today's people not yet liked or passed, in the server's order. */
    val remaining: List<UserProfile> = emptyList(),
    val prefs: DiscoveryPrefs = DiscoveryPrefs(),
    val sending: Boolean = false,
    /** The person just passed, while "Undo" is offered; null otherwise. */
    val lastPassed: UserProfile? = null,
    /** Localization key of an error to show, or null. */
    val errorKey: String? = null
) {
    /** Profiles left after the user's filters; filters also shape tomorrow's set on the server. */
    val visible: List<UserProfile> get() = remaining.filter { prefs.accepts(it) }
    val current: UserProfile? get() = visible.firstOrNull()
    val doneForToday: Boolean get() = !loading && !loadFailed && visible.isEmpty()
}

/**
 * Daily discovery (blueprint Phase 6): today's curated set from the server, like (on a
 * photo or prompt, with an optional comment), pass, and the Discover filters.
 */
class MatchingViewModel(
    private val repository: MarryMatchRepository,
    private val api: DiscoveryApi? = repository.discovery,
    private val nowMs: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private var lastPassedAtMs = 0L

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        load()
        // A newly blocked person disappears from today's set right away
        viewModelScope.launch {
            repository.blockedUserIds.collect { blocked ->
                _uiState.update { s -> s.copy(remaining = s.remaining.filterNot { it.id in blocked }) }
            }
        }
    }

    fun load() {
        val discovery = api ?: run {
            _uiState.update { it.copy(loading = false, loadFailed = true) }
            return
        }
        _uiState.update { it.copy(loading = true, loadFailed = false, errorKey = null) }
        viewModelScope.launch {
            try {
                val prefs = discovery.loadPrefs()
                val set = discovery.getDailySet()
                val blocked = repository.blockedUserIds.value
                _uiState.update {
                    it.copy(
                        loading = false,
                        total = set.total,
                        remaining = set.profiles.filterNot { p -> p.id in blocked },
                        prefs = prefs
                    )
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun like(target: LikeTarget = LikeTarget(), comment: String? = null) {
        val person = _uiState.value.current ?: return
        val discovery = api ?: return
        if (_uiState.value.sending) return
        _uiState.update { it.copy(sending = true, errorKey = null) }
        viewModelScope.launch {
            try {
                val result = discovery.like(person.id, target.type, target.id, comment)
                removeFromSet(person)
                if (result.matched) repository.onNewMatch(person)
            } catch (e: Throwable) {
                _uiState.update { it.copy(sending = false, errorKey = "discover_action_failed") }
            }
        }
    }

    fun pass() {
        val person = _uiState.value.current ?: return
        val discovery = api ?: return
        if (_uiState.value.sending) return
        _uiState.update { it.copy(sending = true, errorKey = null) }
        viewModelScope.launch {
            try {
                discovery.pass(person.id)
                removeFromSet(person)
                lastPassedAtMs = nowMs()
                _uiState.update { it.copy(lastPassed = person) }
            } catch (e: Throwable) {
                _uiState.update { it.copy(sending = false, errorKey = "discover_action_failed") }
            }
        }
    }

    /** Brings the person just passed back to the front of today's set. */
    fun undoPass() {
        val person = _uiState.value.lastPassed ?: return
        val discovery = api ?: return
        if (_uiState.value.sending) return
        if (nowMs() - lastPassedAtMs > UNDO_WINDOW_MS) {
            _uiState.update { it.copy(lastPassed = null, errorKey = "undo_too_late") }
            return
        }
        _uiState.update { it.copy(sending = true, errorKey = null) }
        viewModelScope.launch {
            try {
                discovery.undoPass(person.id)
                _uiState.update { s ->
                    s.copy(sending = false, lastPassed = null, remaining = listOf(person) + s.remaining.filterNot { it.id == person.id })
                }
            } catch (e: Throwable) {
                val key = if (e.message?.contains("undo_too_late") == true) "undo_too_late" else "undo_failed"
                _uiState.update { it.copy(sending = false, lastPassed = null, errorKey = key) }
            }
        }
    }

    /** "Undo" is no longer offered (its time is up, or the user moved on). */
    fun dismissUndo() = _uiState.update { it.copy(lastPassed = null) }

    /** Saves the filters; they apply to today's remaining people now and to tomorrow's set. */
    fun applyPrefs(prefs: DiscoveryPrefs) {
        val before = _uiState.value.prefs
        _uiState.update { it.copy(prefs = prefs, errorKey = null) }
        val discovery = api ?: return
        viewModelScope.launch {
            try {
                discovery.savePrefs(prefs)
            } catch (e: Throwable) {
                _uiState.update { it.copy(prefs = before, errorKey = "filters_save_failed") }
            }
        }
    }

    companion object {
        /** Same limit as the server's undo_pass. */
        const val UNDO_WINDOW_MS = 10 * 60 * 1000L
    }

    private fun removeFromSet(person: UserProfile) {
        _uiState.update { s -> s.copy(sending = false, remaining = s.remaining.filterNot { it.id == person.id }) }
    }
}
