package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChaperoneApi
import com.example.data.ChaperoneCode
import com.example.data.ChaperoneInvite
import com.example.data.MyChaperone
import com.example.data.Ward
import com.example.data.WardMatch
import com.example.data.WardMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChaperoneUiState(
    /** The member's own chaperone or open invitation (only for members with a profile). */
    val mine: MyChaperone? = null,
    /** The code just created; shown until the screen closes (the server keeps only a hash). */
    val invite: ChaperoneInvite? = null,
    /** Members this user is chaperone for. */
    val wards: List<Ward> = emptyList(),
    /** The member's chat the chaperone has open, and its messages. */
    val openWard: Ward? = null,
    val openMatch: WardMatch? = null,
    val messages: List<WardMessage> = emptyList(),
    val loading: Boolean = false,
    /** True after the first successful load (a failed load must not look like "no members"). */
    val loaded: Boolean = false,
    val busy: Boolean = false,
    /** Localization key of the last error, or null. */
    val errorKey: String? = null,
    /** Name of the member whose code was just accepted, for a short confirmation. */
    val joinedMember: String? = null
)

/**
 * Chaperone mode: a member invites a family member with a one-time code; the chaperone can then
 * read the member's chats (read-only, only messages sent after they joined). [hasProfile] is false
 * for a chaperone-only account, which has no "your chaperone" part.
 */
class ChaperoneViewModel(private val api: ChaperoneApi?, private val hasProfile: Boolean) : ViewModel() {

    private val _state = MutableStateFlow(ChaperoneUiState())
    val state: StateFlow<ChaperoneUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Reloads from the server; [quiet] (background checks) keeps errors as they are and shows none. */
    fun refresh(quiet: Boolean = false) {
        val a = api ?: return
        if (_state.value.loading) return
        _state.update { if (quiet) it.copy(loading = true) else it.copy(loading = true, errorKey = null) }
        viewModelScope.launch {
            try {
                val mine = if (hasProfile) a.myChaperone() else null
                val wards = a.myWards()
                _state.update { s ->
                    s.copy(
                        loading = false,
                        loaded = true,
                        mine = mine,
                        // An invite code stays visible only while that invitation is still open.
                        invite = s.invite?.takeIf { mine?.id == it.id && !mine.isActive },
                        wards = wards
                    )
                }
            } catch (e: Throwable) {
                _state.update { if (quiet) it.copy(loading = false) else it.copy(loading = false, errorKey = ChaperoneApi.errorKey(e)) }
            }
        }
    }

    private fun act(block: suspend (ChaperoneApi) -> Unit) {
        val a = api ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, errorKey = null) }
        viewModelScope.launch {
            try {
                block(a)
                _state.update { it.copy(busy = false) }
            } catch (e: Throwable) {
                _state.update { it.copy(busy = false, errorKey = ChaperoneApi.errorKey(e)) }
            }
        }
    }

    fun createInvite() = act { a ->
        val invite = a.invite()
        val mine = a.myChaperone()
        _state.update { it.copy(invite = invite, mine = mine) }
    }

    /** Ends the member's own invitation or chaperone. */
    fun endMine() = act { a ->
        val id = _state.value.mine?.id ?: return@act
        a.end(id)
        _state.update { it.copy(mine = null, invite = null) }
    }

    /** Accepts a code; [onJoined] runs after success (e.g. to leave onboarding). */
    fun accept(code: String, yourName: String, onJoined: () -> Unit = {}) {
        val clean = ChaperoneCode.normalize(code)
        if (!ChaperoneCode.isComplete(clean)) {
            _state.update { it.copy(errorKey = "chaperone_error_code_format") }
            return
        }
        if (yourName.isBlank()) {
            _state.update { it.copy(errorKey = "chaperone_error_name_required") }
            return
        }
        act { a ->
            val member = a.accept(clean, yourName.trim())
            val wards = a.myWards()
            _state.update { it.copy(wards = wards, joinedMember = member) }
            onJoined()
        }
    }

    /** The chaperone stops reading along for [ward]. */
    fun leave(ward: Ward) = act { a ->
        a.end(ward.id)
        _state.update { s -> s.copy(wards = s.wards.filterNot { it.id == ward.id }) }
    }

    fun openChat(ward: Ward, match: WardMatch) {
        _state.update { it.copy(openWard = ward, openMatch = match, messages = emptyList()) }
        reloadChat()
    }

    fun reloadChat() {
        val matchId = _state.value.openMatch?.matchId ?: return
        act { a ->
            val messages = a.wardMessages(matchId)
            _state.update { if (it.openMatch?.matchId == matchId) it.copy(messages = messages) else it }
        }
    }

    fun closeChat() = _state.update { it.copy(openWard = null, openMatch = null, messages = emptyList()) }

    fun clearError() = _state.update { it.copy(errorKey = null) }

    fun clearJoined() = _state.update { it.copy(joinedMember = null) }
}
