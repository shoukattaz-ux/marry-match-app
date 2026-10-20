package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.push.NotifyCategory
import com.example.push.NotifySound
import com.example.push.NotifyStyle
import com.example.push.NotifyVibration
import com.example.push.PushNotifications
import com.example.ui.theme.extendedColors

/**
 * Settings > Sound & vibration: a sound and a vibration for messages, matches and likes. Tapping
 * a choice plays it at once; "Send a test" shows a real notification with it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationSoundScreen(repository: MarryMatchRepository, onBack: () -> Unit) {
    val lang by repository.appLanguage.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val context = LocalContext.current
    var styles by remember {
        mutableStateOf(NotifyCategory.entries.associateWith { PushNotifications.style(context, it) })
    }

    fun choose(category: NotifyCategory, style: NotifyStyle) {
        PushNotifications.setStyle(context, category, style)
        styles = styles + (category to style)
        PushNotifications.preview(context, style)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("notify_sound_title")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(t("notify_sound_intro"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            NotifyCategory.entries.forEach { category ->
                val style = styles.getValue(category)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth().testTag("notify_sound_${category.key}")
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t("notify_cat_${category.key}"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(t("notify_sound"), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NotifySound.entries.forEach { sound ->
                                FilterChip(
                                    selected = style.sound == sound,
                                    onClick = { choose(category, style.copy(sound = sound)) },
                                    label = { Text(t("notify_sound_${sound.key}")) },
                                    modifier = Modifier.testTag("notify_${category.key}_sound_${sound.key}")
                                )
                            }
                        }
                        Text(t("notify_vibration"), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NotifyVibration.entries.forEach { vibration ->
                                FilterChip(
                                    selected = style.vibration == vibration,
                                    onClick = { choose(category, style.copy(vibration = vibration)) },
                                    label = { Text(t("notify_vibration_${vibration.key}")) },
                                    modifier = Modifier.testTag("notify_${category.key}_vibration_${vibration.key}")
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                PushNotifications.show(
                                    context,
                                    t("notify_test_title"),
                                    t("notify_test_body_${category.key}"),
                                    tag = "test_${category.key}",
                                    kind = SAMPLE_KIND.getValue(category)
                                )
                            },
                            modifier = Modifier.testTag("notify_test_${category.key}")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("notify_test"))
                        }
                    }
                }
            }
            Text(t("notify_sound_dnd"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
        }
    }
}

/** A push kind of each category, for the test notification. */
private val SAMPLE_KIND = mapOf(
    NotifyCategory.MESSAGES to "message",
    NotifyCategory.MATCHES to "match",
    NotifyCategory.LIKES to "like"
)
