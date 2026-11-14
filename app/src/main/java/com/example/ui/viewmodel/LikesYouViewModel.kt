package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.DiscoveryApi
import com.example.model.PromptCatalog
import com.example.data.ReceivedLike
import com.example.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LikesYouUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val likes: List<ReceivedLike> = emptyList(),
    /** The person whose full profile is open, or null for the list. */
    val openId: String? = null,
    /** Someone is being liked back or passed: their buttons wait. */
    val workingOn: String? = null,
    val errorKey: String? = null
) {
    val open: ReceivedLike? get() = likes.firstOrNull { it.profile.id == openId }
}

/**
 * Likes You (migration 045): everyone who liked me, with Like back (an instant match, free) and
 * Pass. [onMatched] shows the match celebration; [onChanged] refreshes the counts and badges.
 */
class LikesYouViewModel(
    private val api: DiscoveryApi?,
    private val onMatched: suspend (UserProfile) -> Unit = {},
    private val onChanged: () -> Unit = {}
) : ViewModel() {
    private val _state = MutableStateFlow(LikesYouUiState())
    val state: StateFlow<LikesYouUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads the list; with [quiet] the list stays on screen while it refreshes. */
    fun load(quiet: Boolean = false) {
        val api = api ?: run { _state.update { it.copy(loading = false, loadFailed = true) }; return }
        if (!quiet) _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val likes = api.receivedLikes()
                _state.update { s ->
                    s.copy(loading = false, loadFailed = false, likes = likes,
                        openId = s.openId?.takeIf { id -> likes.any { it.profile.id == id } })
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadFailed = !quiet || it.likes.isEmpty()) }
            }
        }
    }

    fun open(userId: String) = _state.update { it.copy(openId = userId, errorKey = null) }

    fun close() = _state.update { it.copy(openId = null) }

    fun clearError() = _state.update { it.copy(errorKey = null) }

    fun likeBack(userId: String, comment: String? = null) {
        val api = api ?: return
        val like = _state.value.likes.firstOrNull { it.profile.id == userId } ?: return
        if (_state.value.workingOn != null) return
        _state.update { it.copy(workingOn = userId, errorKey = null) }
        viewModelScope.launch {
            try {
                val result = api.likeBack(userId, comment?.trim()?.take(MAX_COMMENT))
                remove(userId)
                if (result.matched) onMatched(like.profile)
                onChanged()
            } catch (e: Exception) {
                // The like was taken back, the person was blocked or banned meanwhile: it is gone.
                val gone = e.message.orEmpty().let { it.contains("no_open_like") || it.contains("not_allowed") }
                if (gone) remove(userId)
                _state.update { it.copy(workingOn = null, errorKey = if (gone) "likes_you_gone" else "likes_you_failed") }
                if (gone) onChanged()
            }
        }
    }

    fun pass(userId: String) {
        val api = api ?: return
        if (_state.value.workingOn != null) return
        _state.update { it.copy(workingOn = userId, errorKey = null) }
        viewModelScope.launch {
            try {
                api.pass(userId)
                remove(userId)
                onChanged()
            } catch (e: Exception) {
                _state.update { it.copy(workingOn = null, errorKey = "likes_you_failed") }
            }
        }
    }

    private fun remove(userId: String) = _state.update { s ->
        s.copy(likes = s.likes.filterNot { it.profile.id == userId }, workingOn = null,
            openId = s.openId?.takeIf { it != userId })
    }

    companion object {
        const val MAX_COMMENT = 200
    }
}

/** The texts of the Likes You tab, in both languages. */
object LikesYouText {
    /** "1 person liked you" / "3 people liked you". */
    fun count(count: Int, lang: AppLanguage): String =
        if (count == 1) Localization.t("likes_you_count_one", lang)
        else String.format(Localization.t("likes_you_count_many", lang), count)

    /** What they liked: a photo, an answer (with its question), the Signature Card or the profile. */
    fun likedWhat(like: ReceivedLike, lang: AppLanguage): String {
        val t: (String) -> String = { Localization.t(it, lang) }
        return when (like.targetType) {
            "photo" -> t("likes_you_liked_photo")
            "card" -> t("likes_you_liked_card")
            "prompt" -> {
                val question = PromptCatalog.find(like.promptKey.orEmpty())
                    ?.let { if (lang == AppLanguage.DE) it.questionDe else it.questionEn }
                if (question != null) String.format(t("likes_you_liked_answer_to"), question) else t("likes_you_liked_answer")
            }
            else -> t("likes_you_liked_profile")
        }
    }
}
