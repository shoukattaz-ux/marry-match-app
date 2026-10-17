package com.example.data.media

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** When a received file downloads by itself (Settings > Chat media). */
enum class AutoDownload(val key: String) {
    NEVER("never"),
    WIFI("wifi"),
    ANY("any");

    companion object {
        fun fromKey(key: String?, default: AutoDownload) = entries.firstOrNull { it.key == key } ?: default
    }
}

data class MediaAutoDownloadSettings(
    val photos: AutoDownload = AutoDownload.ANY,
    val voice: AutoDownload = AutoDownload.ANY,
    val videos: AutoDownload = AutoDownload.WIFI
) {
    fun forType(type: MediaType) = when (type) {
        MediaType.IMAGE -> photos
        MediaType.VOICE -> voice
        MediaType.VIDEO -> videos
    }
}

private val Context.chatMediaStore: DataStore<Preferences> by preferencesDataStore(name = "chat_media_settings")

/** Stored on this phone only (DataStore); nothing goes to the server. */
class MediaSettingsStore(context: Context) {
    private val store = context.applicationContext.chatMediaStore

    val settings: Flow<MediaAutoDownloadSettings> = store.data.map { p ->
        val defaults = MediaAutoDownloadSettings()
        MediaAutoDownloadSettings(
            photos = AutoDownload.fromKey(p[PHOTOS], defaults.photos),
            voice = AutoDownload.fromKey(p[VOICE], defaults.voice),
            videos = AutoDownload.fromKey(p[VIDEOS], defaults.videos)
        )
    }

    suspend fun current(): MediaAutoDownloadSettings = settings.first()

    suspend fun set(type: MediaType, value: AutoDownload) {
        store.edit { it[keyFor(type)] = value.key }
    }

    private fun keyFor(type: MediaType) = when (type) {
        MediaType.IMAGE -> PHOTOS
        MediaType.VOICE -> VOICE
        MediaType.VIDEO -> VIDEOS
    }

    private companion object {
        val PHOTOS = stringPreferencesKey("auto_photos")
        val VOICE = stringPreferencesKey("auto_voice")
        val VIDEOS = stringPreferencesKey("auto_videos")
    }
}
