package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SelfieReviewItem
import com.example.data.VerificationApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelfieQueueUiState(
    val loading: Boolean = true,
    val items: List<SelfieReviewItem> = emptyList(),
    /** The request the moderator has open, with its short-lived selfie link. */
    val open: SelfieReviewItem? = null,
    val openSelfieUrl: String? = null,
    val deciding: Boolean = false,
    val errorKey: String? = null
)

/** Moderator selfie queue (blueprint Phase 9): list, open one, approve or reject with a reason. */
class SelfieQueueViewModel(private val api: VerificationApi?) : ViewModel() {

    private val _state = MutableStateFlow(SelfieQueueUiState())
    val state: StateFlow<SelfieQueueUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val verification = api ?: run {
            _state.update { it.copy(loading = false, errorKey = "admin_queue_failed") }
            return
        }
        _state.update { it.copy(loading = true, errorKey = null) }
        viewModelScope.launch {
            try {
                val items = verification.reviewQueue()
                _state.update { it.copy(loading = false, items = items) }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, errorKey = "admin_queue_failed") }
            }
        }
    }

    fun open(item: SelfieReviewItem) {
        val verification = api ?: return
        _state.update { it.copy(open = item, openSelfieUrl = null, errorKey = null) }
        viewModelScope.launch {
            try {
                val url = verification.selfieUrl(item)
                _state.update { if (it.open?.requestId == item.requestId) it.copy(openSelfieUrl = url) else it }
            } catch (e: Throwable) {
                _state.update { it.copy(errorKey = "admin_selfie_load_failed") }
            }
        }
    }

    fun close() = _state.update { it.copy(open = null, openSelfieUrl = null, errorKey = null) }

    /** [reason] must be one of SupabaseVerificationService.REJECT_REASONS when rejecting. */
    fun decide(approve: Boolean, reason: String? = null) {
        val verification = api ?: return
        val item = _state.value.open ?: return
        if (_state.value.deciding || (!approve && reason == null)) return
        _state.update { it.copy(deciding = true, errorKey = null) }
        viewModelScope.launch {
            try {
                verification.review(item.requestId, approve, if (approve) null else reason)
                _state.update { s ->
                    s.copy(
                        deciding = false,
                        open = null,
                        openSelfieUrl = null,
                        items = s.items.filter { it.requestId != item.requestId }
                    )
                }
            } catch (e: Throwable) {
                // 409: another moderator decided first; reload so the list is current.
                _state.update { it.copy(deciding = false) }
                reload()
                _state.update { it.copy(errorKey = "admin_decision_failed") }
            }
        }
    }
}
