package com.example.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.media.VoiceMusic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Where a chat's background music comes from. */
sealed class ChatMusic {
    /** The setting for all chats applies (or nothing, when that is off). */
    data object FollowAll : ChatMusic()
    /** This chat sends voice messages without music, even when all chats have music. */
    data object Off : ChatMusic()
    data class Own(val music: VoiceMusic) : ChatMusic()
}

/**
 * Background music for voice messages, kept on this phone: one setting for all chats and an
 * optional setting per chat that wins over it. Songs are copied into app storage, so they keep
 * working when the original file moves; copies nobody uses any more are deleted.
 */
class VoiceMusicStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("voice_music", Context.MODE_PRIVATE)
    private val songsDir = File(app.filesDir, "voice_music")

    private val _version = MutableStateFlow(0)
    /** Changes whenever a setting changes, so screens can read again. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun allChats(): VoiceMusic? = prefs.getString(KEY_ALL, null)?.let(::decode)

    fun chat(matchId: String): ChatMusic = when (val raw = prefs.getString(chatKey(matchId), null)) {
        null -> ChatMusic.FollowAll
        OFF -> ChatMusic.Off
        else -> decode(raw)?.let { ChatMusic.Own(it) } ?: ChatMusic.FollowAll
    }

    /** The music a voice message in [matchId] gets, or null for none (also when its song file is gone). */
    fun effective(matchId: String): VoiceMusic? = resolve(chat(matchId), allChats())?.takeIf { File(it.songFile).isFile }

    fun setAllChats(music: VoiceMusic?) {
        prefs.edit().apply { if (music == null) remove(KEY_ALL) else putString(KEY_ALL, encode(music)) }.apply()
        changed()
    }

    fun setChat(matchId: String, music: ChatMusic) {
        prefs.edit().apply {
            when (music) {
                ChatMusic.FollowAll -> remove(chatKey(matchId))
                ChatMusic.Off -> putString(chatKey(matchId), OFF)
                is ChatMusic.Own -> putString(chatKey(matchId), encode(music.music))
            }
        }.apply()
        changed()
    }

    /**
     * Copies a song picked from the phone into app storage. Returns the copy and the song's
     * name, or null when it cannot be read or is larger than [MAX_SONG_BYTES].
     */
    fun importSong(uri: Uri): Pair<File, String>? = runCatching {
        val resolver = app.contentResolver
        var name = "Song"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: name
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        if (size > MAX_SONG_BYTES) return null
        songsDir.mkdirs()
        val target = File(songsDir, "${UUID.randomUUID()}.audio")
        var copied = 0L
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    copied += n
                    if (copied > MAX_SONG_BYTES) {
                        output.close()
                        target.delete()
                        return null
                    }
                    output.write(buffer, 0, n)
                }
            }
        } ?: return null
        target to name.take(60)
    }.getOrNull()

    /** Deletes song copies no setting uses (e.g. after a song was replaced or turned off). */
    fun cleanUp(keep: Set<String> = emptySet()) {
        val used = buildSet {
            addAll(keep)
            allChats()?.let { add(it.songFile) }
            prefs.all.forEach { (key, value) ->
                if (key.startsWith(CHAT_PREFIX) && value is String && value != OFF) decode(value)?.let { add(it.songFile) }
            }
        }
        songsDir.listFiles()?.forEach { if (it.absolutePath !in used) it.delete() }
    }

    /** After Log Out: the next account on this phone starts without the old one's songs and settings. */
    fun clearAll() {
        prefs.edit().clear().apply()
        songsDir.deleteRecursively()
        changed()
    }

    private fun changed() {
        _version.value++
    }

    companion object {
        const val MAX_SONG_BYTES = 50L * 1024 * 1024
        private const val KEY_ALL = "all_chats"
        private const val CHAT_PREFIX = "chat_"
        private const val OFF = "off"

        private fun chatKey(matchId: String) = CHAT_PREFIX + matchId

        /** A chat's own setting wins; otherwise the one for all chats (or none). */
        fun resolve(chat: ChatMusic, all: VoiceMusic?): VoiceMusic? = when (chat) {
            ChatMusic.FollowAll -> all
            ChatMusic.Off -> null
            is ChatMusic.Own -> chat.music
        }

        fun encode(m: VoiceMusic): String = JSONObject()
            .put("file", m.songFile).put("name", m.songName)
            .put("start", m.startMs).put("end", m.endMs).put("volume", m.volume.toDouble())
            .toString()

        fun decode(raw: String): VoiceMusic? = runCatching {
            val o = JSONObject(raw)
            VoiceMusic(
                songFile = o.getString("file"),
                songName = o.optString("name", "Song"),
                startMs = o.getLong("start"),
                endMs = o.getLong("end"),
                volume = o.getDouble("volume").toFloat().coerceIn(VoiceMusic.MIN_VOLUME, VoiceMusic.MAX_VOLUME)
            )
        }.getOrNull()
    }
}
