package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.PreparedPhoto
import com.example.data.SelfieStatus
import com.example.data.SelfieTicket
import com.example.data.VerificationApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SelfieStep {
    LOADING,
    /** Explains the check and asks for the biometric consent. */
    INTRO,
    CAMERA,
    /** Shows the photo just taken: retake or submit. */
    REVIEW,
    UPLOADING,
    /** Waiting for a moderator. */
    PENDING,
    VERIFIED
}

data class SelfieUiState(
    val step: SelfieStep = SelfieStep.LOADING,
    val consentChecked: Boolean = false,
    val busy: Boolean = false,
    /** Pose key from the server, e.g. "touch_nose". */
    val pose: String? = null,
    val photo: PreparedPhoto? = null,
    /** Reason of the last rejection, shown on the intro. */
    val lastRejectReason: String? = null,
    /** Localization key of an error to show, or null. */
    val errorKey: String? = null
)

/**
 * Selfie verification (blueprint Phase 4): consent, a pose chosen by the server, a live photo
 * with exactly one face, then manual review by a moderator. The app never grants the badge.
 */
class SelfieViewModel(private val api: VerificationApi?) : ViewModel() {

    private val _state = MutableStateFlow(SelfieUiState())
    val state: StateFlow<SelfieUiState> = _state.asStateFlow()

    private var ticket: SelfieTicket? = null

    init {
        loadStatus()
    }

    fun loadStatus() {
        val verification = api ?: run {
            _state.update { it.copy(step = SelfieStep.INTRO, errorKey = "selfie_error_network") }
            return
        }
        viewModelScope.launch {
            val status = try {
                verification.myStatus()
            } catch (e: Throwable) {
                SelfieStatus(SelfieStatus.NONE)
            }
            _state.update {
                when (status.state) {
                    SelfieStatus.APPROVED -> it.copy(step = SelfieStep.VERIFIED)
                    SelfieStatus.PENDING -> it.copy(step = SelfieStep.PENDING)
                    SelfieStatus.REJECTED -> it.copy(step = SelfieStep.INTRO, lastRejectReason = status.rejectReason)
                    else -> it.copy(step = SelfieStep.INTRO)
                }
            }
        }
    }

    fun setConsent(checked: Boolean) = _state.update { it.copy(consentChecked = checked, errorKey = null) }

    /** Saves the consent and asks the server for a pose. Camera permission is handled by the screen. */
    fun start() {
        val verification = api ?: return
        val s = _state.value
        if (s.busy || !s.consentChecked) return
        _state.update { it.copy(busy = true, errorKey = null) }
        viewModelScope.launch {
            try {
                verification.giveConsent()
                val t = verification.startCheck()
                ticket = t
                _state.update { it.copy(busy = false, step = SelfieStep.CAMERA, pose = t.pose, photo = null) }
            } catch (e: Throwable) {
                val key = errorKeyFor(e)
                _state.update {
                    when (key) {
                        "already_pending" -> it.copy(busy = false, step = SelfieStep.PENDING)
                        "already_verified" -> it.copy(busy = false, step = SelfieStep.VERIFIED)
                        else -> it.copy(busy = false, errorKey = key)
                    }
                }
            }
        }
    }

    /** The screen shows this when the camera could not start or take the picture. */
    fun onCameraFailed() = _state.update { it.copy(errorKey = "selfie_error_camera") }

    /** A photo from the live camera, already resized and checked for faces on the phone. */
    fun onPhotoTaken(photo: PreparedPhoto) {
        val faces = photo.faceCount
        // null means the face detector is not available; the moderator still checks the photo.
        when {
            faces == 0 -> _state.update { it.copy(errorKey = "selfie_error_no_face") }
            faces != null && faces > 1 -> _state.update { it.copy(errorKey = "selfie_error_many_faces") }
            else -> _state.update { it.copy(step = SelfieStep.REVIEW, photo = photo, errorKey = null) }
        }
    }

    /** Back to the camera with the same pose (does not use up another attempt). */
    fun retake() = _state.update { it.copy(step = SelfieStep.CAMERA, photo = null, errorKey = null) }

    fun submit() {
        val verification = api ?: return
        val t = ticket ?: return
        val photo = _state.value.photo ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, step = SelfieStep.UPLOADING, errorKey = null) }
        viewModelScope.launch {
            try {
                verification.uploadAndSubmit(t, photo.jpeg)
                ticket = null
                _state.update { it.copy(busy = false, step = SelfieStep.PENDING, photo = null, pose = null) }
            } catch (e: Throwable) {
                val key = errorKeyFor(e)
                if (key == "selfie_error_expired") {
                    // The 30 minutes are over: start again with a new pose.
                    ticket = null
                    _state.update { it.copy(busy = false, step = SelfieStep.INTRO, photo = null, pose = null, errorKey = key) }
                } else {
                    _state.update { it.copy(busy = false, step = SelfieStep.REVIEW, errorKey = key) }
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorKey = null) }

    companion object {
        /** Maps a server error (raised by the database functions) to a localization key. */
        fun errorKeyFor(e: Throwable): String {
            val msg = e.message.orEmpty()
            return when {
                "already_pending" in msg -> "already_pending"
                "already_verified" in msg -> "already_verified"
                "selfie_attempt_limit" in msg -> "selfie_error_limit"
                "selfie_request_expired" in msg -> "selfie_error_expired"
                "consent_required" in msg -> "selfie_error_consent"
                "not_allowed" in msg -> "selfie_error_not_allowed"
                else -> "selfie_error_network"
            }
        }
    }
}
