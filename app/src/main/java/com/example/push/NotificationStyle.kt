package com.example.push

/** Sounds a notification can make; the three MarryMatch ones are in res/raw. */
enum class NotifySound(val key: String) {
    CHIME("chime"),
    BELL("bell"),
    MELODY("melody"),
    /** The phone's own notification sound. */
    SYSTEM("system"),
    SILENT("silent");

    companion object {
        fun of(key: String?, fallback: NotifySound) = entries.firstOrNull { it.key == key } ?: fallback
    }
}

/** Vibration patterns: off/on times in milliseconds, starting with a pause. */
enum class NotifyVibration(val key: String, val pattern: LongArray?) {
    HEARTBEAT("heartbeat", longArrayOf(0, 120, 100, 160)),
    GENTLE("gentle", longArrayOf(0, 180)),
    STRONG("strong", longArrayOf(0, 450, 150, 450)),
    OFF("off", null);

    companion object {
        fun of(key: String?, fallback: NotifyVibration) = entries.firstOrNull { it.key == key } ?: fallback
    }
}

/** How one kind of notification sounds and vibrates. */
data class NotifyStyle(val sound: NotifySound, val vibration: NotifyVibration)

/**
 * The three kinds of notification, each with its own sound and vibration. Every combination is
 * a separate Android channel ([channelId]), because Android fixes a channel's sound once created.
 */
enum class NotifyCategory(val key: String, val default: NotifyStyle) {
    MESSAGES("messages", NotifyStyle(NotifySound.CHIME, NotifyVibration.GENTLE)),
    MATCHES("matches", NotifyStyle(NotifySound.MELODY, NotifyVibration.HEARTBEAT)),
    LIKES("likes", NotifyStyle(NotifySound.BELL, NotifyVibration.GENTLE));

    /** Channel ids of this category start with this. */
    val channelPrefix: String get() = "mm_${key}_"

    fun channelId(style: NotifyStyle) = "$channelPrefix${style.sound.key}_${style.vibration.key}"

    companion object {
        /** Which category a push "kind" from send-push belongs to. */
        fun forKind(kind: String): NotifyCategory = when (kind) {
            "message", "your_turn" -> MESSAGES
            "match", "expiring" -> MATCHES
            else -> LIKES
        }
    }
}
