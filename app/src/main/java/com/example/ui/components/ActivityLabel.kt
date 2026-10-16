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

private val ActiveGreen = Color(0xFF2E9E5B)

/** "Active today" / "Active this week"; [activity] is the server value "today" or "week". */
@Composable
fun ActivityLabel(activity: String, lang: AppLanguage, onDark: Boolean = false) {
    val key = when (activity) {
        "today" -> "active_today"
        "week" -> "active_week"
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (activity == "today") {
            Box(Modifier.size(7.dp).background(ActiveGreen, CircleShape))
        }
        Text(
            text = Localization.t(key, lang),
            style = MaterialTheme.typography.bodySmall,
            color = if (onDark) Color.White.copy(alpha = 0.9f) else MaterialTheme.extendedColors.muted
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
        online -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(8.dp).background(ActiveGreen, CircleShape))
            Text(Localization.t("chat_online", lang), style = MaterialTheme.typography.bodySmall, color = ActiveGreen)
        }
        activity == "today" || activity == "week" -> ActivityLabel(activity, lang)
        else -> Text(fallback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
    }
}
