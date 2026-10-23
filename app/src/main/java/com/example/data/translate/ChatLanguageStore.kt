package com.example.data.translate

import android.content.Context

/** The language each chat is shown in, kept on this phone (each person chooses their own). */
class ChatLanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("chat_translation", Context.MODE_PRIVATE)

    /** Language code for [chatId], or null for "show messages as written". */
    fun get(chatId: String): String? = prefs.getString(KEY_PREFIX + chatId, null)

    fun set(chatId: String, code: String?) {
        prefs.edit().apply { if (code == null) remove(KEY_PREFIX + chatId) else putString(KEY_PREFIX + chatId, code) }.apply()
    }

    /** After Log Out: the next account starts with every chat as written. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_PREFIX = "chat_"
    }
}
