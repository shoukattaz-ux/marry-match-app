package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.ui.chat.ChatThemes
import com.example.ui.viewmodel.ChatViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChatThemesTest {

    @Test
    fun theAppOffersExactlyTheThemesTheServerAccepts() {
        val sql = File("../supabase/migrations/20261022193701_034_chat_themes.sql").readText()
        val accepted = Regex("theme in \\(([^)]*)\\)").find(sql)!!.groupValues[1]
            .split(",").map { it.trim().trim('\'') }.toSet()
        assertEquals(accepted, ChatThemes.ALL.map { it.key }.toSet())
        assertEquals(ChatViewModel.DEFAULT_THEME, ChatThemes.ALL.first().key)
    }

    @Test
    fun anUnknownThemeFallsBackToClassic() {
        assertEquals("classic", ChatThemes.find("disco").key)
        assertEquals("classic", ChatThemes.find(null).key)
        assertEquals("ocean", ChatThemes.find("ocean").key)
    }

    @Test
    fun everyThemeHasANameInBothLanguagesAndReadableBubbles() {
        for (theme in ChatThemes.ALL) {
            for (lang in AppLanguage.entries) assertNotEquals("chat_theme_${theme.key}", Localization.t("chat_theme_${theme.key}", lang))
            if (!theme.isClassic) {
                assertTrue(theme.key, theme.gradient.size >= 2)
                assertNotEquals(theme.key, theme.myBubble, theme.onMyBubble)
                assertNotEquals(theme.key, theme.theirBubble, theme.onTheirBubble)
            }
        }
    }
}
