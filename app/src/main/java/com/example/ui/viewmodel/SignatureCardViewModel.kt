package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SignatureCardApi
import com.example.data.SignatureCardException
import com.example.model.SignatureCard
import com.example.model.SignatureCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignatureCardUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** For the example text ("A kind man …" / "A kind woman …"). */
    val woman: Boolean = false,
    /** A selfie-verified member: they may save a card and it is shown. */
    val unlocked: Boolean = false,
    /** What the server has; null when she has no card yet. */
    val saved: SignatureCard? = null,
    /** What the editor shows (the live preview). */
    val draft: SignatureCard = SignatureCard(),
    val saving: Boolean = false,
    val errorKey: String? = null,
    val doneKey: String? = null
) {
    val problem: String? get() = SignatureCatalog.problem(draft)
    val changed: Boolean get() = saved?.copy(id = "") != draft.copy(id = "")
}

/** Settings → Signature Card: load, edit with a live preview, save or remove (migration 043). */
class SignatureCardViewModel(private val api: SignatureCardApi?) : ViewModel() {
    private val _state = MutableStateFlow(SignatureCardUiState())
    val state: StateFlow<SignatureCardUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val api = api ?: run { _state.update { it.copy(loading = false, loadFailed = true) }; return }
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val mine = api.mine()
                _state.update {
                    it.copy(
                        loading = false, woman = mine.woman, unlocked = mine.unlocked,
                        saved = mine.card, draft = mine.card ?: SignatureCard()
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    private fun edit(change: (SignatureCard) -> SignatureCard) =
        _state.update { it.copy(draft = change(it.draft), errorKey = null, doneKey = null) }

    fun setTemplate(template: String) {
        if (template in SignatureCatalog.TEMPLATES) edit { it.copy(template = template) }
    }

    fun setLookingFor(text: String) = edit { it.copy(lookingFor = text.take(SignatureCatalog.MAX_LOOKING_FOR)) }

    /** Adds or removes a tag; a fourth one is refused with a hint. */
    fun toggleReason(key: String) {
        if (SignatureCatalog.reason(key) == null) return
        val current = _state.value.draft.reasons
        when {
            key in current -> edit { it.copy(reasons = current - key) }
            current.size >= SignatureCatalog.MAX_REASONS -> _state.update { it.copy(errorKey = "card_max_reasons") }
            else -> edit { it.copy(reasons = current + key) }
        }
    }

    fun setVibeEmoji(emoji: String) {
        if (emoji in SignatureCatalog.EMOJIS) edit { it.copy(vibeEmoji = emoji) }
    }

    fun setVibe(text: String) = edit { it.copy(vibe = text.take(SignatureCatalog.MAX_VIBE)) }

    fun save() {
        val api = api ?: return
        val s = _state.value
        if (!s.unlocked || s.saving) return
        s.problem?.let { key -> _state.update { it.copy(errorKey = key) }; return }
        _state.update { it.copy(saving = true, errorKey = null, doneKey = null) }
        viewModelScope.launch {
            try {
                val mine = api.save(s.draft)
                _state.update {
                    it.copy(saving = false, unlocked = mine.unlocked, saved = mine.card, draft = mine.card ?: it.draft, doneKey = "card_saved")
                }
            } catch (e: SignatureCardException) {
                _state.update { it.copy(saving = false, errorKey = e.code, unlocked = e.code != "card_needs_verification" && it.unlocked) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errorKey = "card_save_failed") }
            }
        }
    }

    fun delete() {
        val api = api ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, errorKey = null, doneKey = null) }
        viewModelScope.launch {
            try {
                api.delete()
                _state.update { it.copy(saving = false, saved = null, draft = SignatureCard(), doneKey = "card_removed") }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, errorKey = "card_save_failed") }
            }
        }
    }
}
