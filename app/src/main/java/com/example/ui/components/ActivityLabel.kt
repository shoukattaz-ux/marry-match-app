package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.ui.theme.extendedColors

/** Online: using the app, or left it less than the admin-set time ago (migration 046). */
val OnlineGreen = Color(0xFF22C55E)
/** Recently online: within the time admins set (20 minutes at first). */
val RecentGreen = Color(0xFF86EFAC)
/** Offline. */
val OfflineGray = Color(0xFF9CA3AF)

/** Dot colour and text of a server status ("online", "recent", "offline"); null shows nothing. */
fun activityStyle(activity: String): Pair<Color, String>? = when (activity) {
    "online" -> OnlineGreen to "status_online"
    "recent" -> RecentGreen to "status_recent"
    "offline" -> OfflineGray to "status_offline"
    // Older app data until the next reload.
    "today" -> RecentGreen to "active_today"
    "week" -> OfflineGray to "active_week"
    else -> null
}

/**
 * Online status with its dot: "Online" (bright green), "Recently online" (pale green) or
 * "Offline" (gray). Nothing when the person hides their activity (or the viewer does).
 */
@Composable
fun ActivityLabel(activity: String, lang: AppLanguage, onDark: Boolean = false) {
    val (color, key) = activityStyle(activity) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            text = Localization.t(key, lang),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                onDark -> Color.White.copy(alpha = 0.9f)
                activity == "online" -> OnlineGreen
                else -> MaterialTheme.extendedColors.muted
            }
        )
    }
}

/** Header line in an open chat: "typing…", "Online" (green dot) or the activity label. */
@Composable
fun ChatPeerStatus(typing: Boolean, online: Boolean, activity: String, fallback: String, lang: AppLanguage) {
    when {
        typing -> Text(
            Localization.t("chat_typing", lang),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        // Has the chat open right now (live presence): online in any case.
        online -> ActivityLabel("online", lang)
        activityStyle(activity) != null -> ActivityLabel(activity, lang)
        else -> Text(fallback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
    }
}
