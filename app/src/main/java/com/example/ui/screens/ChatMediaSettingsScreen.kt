package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.data.media.AutoDownload
import com.example.data.media.MediaAutoDownloadSettings
import com.example.data.media.MediaSettingsStore
import com.example.data.media.MediaType
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

/** Settings > Chat media: when photos, voice messages and videos download by themselves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMediaSettingsScreen(repository: MarryMatchRepository, onBack: () -> Unit) {
    val lang by repository.appLanguage.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val context = LocalContext.current
    val store = remember { MediaSettingsStore(context) }
    val settings by store.settings.collectAsState(initial = MediaAutoDownloadSettings())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("media_settings_title")) },
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
            Text(t("media_settings_intro"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            listOf(
                MediaType.IMAGE to "media_settings_photos",
                MediaType.VOICE to "media_settings_voice",
                MediaType.VIDEO to "media_settings_videos"
            ).forEach { (type, titleKey) ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(t(titleKey), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf(
                            AutoDownload.NEVER to "media_auto_never",
                            AutoDownload.WIFI to "media_auto_wifi",
                            AutoDownload.ANY to "media_auto_any"
                        ).forEach { (option, labelKey) ->
                            val selected = settings.forType(type) == option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = selected, role = Role.RadioButton) { scope.launch { store.set(type, option) } }
                                    .padding(vertical = 6.dp)
                                    .testTag("media_auto_${type.wire}_${option.key}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(Modifier.width(10.dp))
                                Text(t(labelKey), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            Text(t("media_retention_info"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
        }
    }
}
