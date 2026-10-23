package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.media.VoiceMusic
import com.example.data.media.ChatMusic
import com.example.data.media.VoiceMusicStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceMusicStoreTest {

    private val all = VoiceMusic("/songs/a.audio", "Song A", 10_000, 55_000, 0.15f)
    private val own = VoiceMusic("/songs/b.audio", "Song B", 0, 30_000, 0.5f)

    @Test
    fun aChatsOwnMusicWinsOverTheOneForAllChats() {
        assertEquals(all, VoiceMusicStore.resolve(ChatMusic.FollowAll, all))
        assertEquals(own, VoiceMusicStore.resolve(ChatMusic.Own(own), all))
        assertNull(VoiceMusicStore.resolve(ChatMusic.Off, all))  // switched off just for this chat
        assertNull(VoiceMusicStore.resolve(ChatMusic.FollowAll, null))
        assertEquals(own, VoiceMusicStore.resolve(ChatMusic.Own(own), null))
    }

    @Test
    fun everyTextExistsInBothLanguages() {
        listOf(
            "voice_music_title", "voice_music_choose", "voice_music_part", "voice_music_volume", "voice_music_where",
            "voice_music_all_chats", "voice_music_only_chat", "voice_music_test", "voice_music_done", "voice_music_failed",
            "voice_music_mixing", "voice_music_off_all", "voice_music_off_chat", "voice_music_unreadable",
            "voice_music_missing", "voice_music_rights", "voice_music_test_mic"
        ).forEach { key -> AppLanguage.entries.forEach { assertNotEquals("$it $key", key, Localization.t(key, it)) } }
        assertEquals("Music volume: 15%", String.format(Localization.t("voice_music_volume", AppLanguage.EN), 15))
    }
}
