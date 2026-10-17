package com.example.ui.chat

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class VoicePlayback(
    val messageId: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** Message ids whose file could not be played. */
    val broken: Set<String> = emptySet(),
    /** Voice messages this user has listened to (stored on this phone). */
    val listened: Set<String> = emptySet()
)

/**
 * One player for all voice messages of a chat. Plays only local files (never streams), pauses
 * when another app takes the audio (a call) and when the app goes to the background, and plays
 * the next voice message automatically.
 */
class VoicePlayer(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("voice_listened", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true
        )
        setHandleAudioBecomingNoisy(true)
    }
    private var ticker: Job? = null

    private val _state = MutableStateFlow(VoicePlayback(listened = prefs.getStringSet(KEY, emptySet()).orEmpty()))
    val state: StateFlow<VoicePlayback> = _state.asStateFlow()

    /** The voice messages that could play next, in chat order: (message id, local file). */
    var queue: () -> List<Pair<String, File>> = { emptyList() }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(playing = isPlaying) }
                if (isPlaying) startTicker() else ticker?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _state.update { it.copy(durationMs = player.duration.coerceAtLeast(0)) }
                }
                if (playbackState == Player.STATE_ENDED) playNext()
            }

            override fun onPlayerError(error: PlaybackException) {
                val id = _state.value.messageId ?: return
                _state.update { it.copy(playing = false, broken = it.broken + id) }
            }
        })
    }

    fun toggle(messageId: String, file: File) {
        val s = _state.value
        if (s.messageId == messageId) {
            if (player.isPlaying) player.pause()
            else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
            return
        }
        start(messageId, file)
    }

    private fun start(messageId: String, file: File) {
        _state.update { it.copy(messageId = messageId, positionMs = 0, durationMs = 0) }
        markListened(messageId)
        player.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
        player.playbackParameters = PlaybackParameters(_state.value.speed)
        player.prepare()
        player.play()
    }

    private fun playNext() {
        val current = _state.value.messageId
        val list = queue()
        val index = list.indexOfFirst { it.first == current }
        val next = if (index >= 0) list.getOrNull(index + 1) else null
        if (next != null) start(next.first, next.second)
        else _state.update { it.copy(playing = false, positionMs = 0) }
    }

    fun seekTo(messageId: String, fraction: Float) {
        if (_state.value.messageId != messageId) return
        val target = (player.duration.coerceAtLeast(0) * fraction).toLong()
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    /** 1x, 1.5x, 2x */
    fun cycleSpeed() {
        val next = when (_state.value.speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        player.playbackParameters = PlaybackParameters(next)
        _state.update { it.copy(speed = next) }
    }

    fun pause() = player.pause()

    private fun markListened(messageId: String) {
        if (messageId in _state.value.listened) return
        val updated = _state.value.listened + messageId
        prefs.edit().putStringSet(KEY, updated).apply()
        _state.update { it.copy(listened = updated) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                _state.update { it.copy(positionMs = player.currentPosition.coerceAtLeast(0)) }
                delay(100)
            }
        }
    }

    fun release() {
        scope.cancel()
        player.release()
    }

    private companion object {
        const val KEY = "listened_ids"
    }
}
