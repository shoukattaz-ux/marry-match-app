package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.data.PrivacySettings
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

/**
 * Privacy settings. They are saved on the server, which applies them to what other members
 * see: incognito and "verified only" control the daily suggestions; hidden fields are left out
 * of the profile others receive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val privacy by repository.privacy.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showDeleteAccount by remember { mutableStateOf(false) }
    val (exporting, startExport) = rememberDataExport(repository) { msg -> scope.launch { snackbar.showSnackbar(msg) } }

    LaunchedEffect(Unit) { repository.loadPrivacy() }

    fun change(update: (PrivacySettings) -> PrivacySettings) {
        scope.launch {
            repository.updatePrivacy(update).onFailure {
                snackbar.showSnackbar(Localization.t("privacy_save_failed", lang))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("privacy_settings_title", lang), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Localization.t("back", lang))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(Localization.t("privacy_intro", lang), style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(Localization.t("privacy_section_visibility", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PrivacyCard {
                PrivacyToggleRow(
                    title = Localization.t("privacy_incognito", lang),
                    description = Localization.t("privacy_incognito_desc", lang),
                    checked = privacy.incognito,
                    onCheckedChange = { on -> change { it.copy(incognito = on) } },
                    testTag = "toggle_incognito"
                )
                HorizontalDivider()
                PrivacyToggleRow(
                    title = Localization.t("privacy_verified_only", lang),
                    description = Localization.t("privacy_verified_only_desc", lang),
                    checked = privacy.verifiedViewersOnly,
                    onCheckedChange = { on -> change { it.copy(verifiedViewersOnly = on) } },
                    testTag = "toggle_visible_only_verified"
                )
                HorizontalDivider()
                PrivacyToggleRow(
                    title = Localization.t("privacy_show_activity", lang),
                    description = Localization.t("privacy_show_activity_desc", lang),
                    checked = !privacy.hideActivity,
                    onCheckedChange = { on -> change { it.copy(hideActivity = !on) } },
                    testTag = "toggle_show_activity"
                )
            }

            Text(Localization.t("privacy_section_fields", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PrivacyCard {
                PrivacyToggleRow(
                    title = Localization.t("privacy_show_city", lang),
                    description = if (privacy.hideCity) {
                        String.format(Localization.t("privacy_city_hidden", lang), currentUser.region)
                    } else {
                        String.format(Localization.t("privacy_city_shown", lang), listOf(currentUser.city, currentUser.region).filter { it.isNotBlank() }.joinToString(", "))
                    },
                    checked = !privacy.hideCity,
                    onCheckedChange = { on -> change { it.copy(hideCity = !on) } },
                    testTag = "toggle_show_city"
                )
                HorizontalDivider()
                PrivacyToggleRow(
                    title = Localization.t("privacy_show_religion", lang),
                    description = Localization.t("privacy_show_religion_desc", lang),
                    checked = !privacy.hideReligion,
                    onCheckedChange = { on -> change { it.copy(hideReligion = !on) } },
                    testTag = "toggle_show_religion"
                )
                HorizontalDivider()
                PrivacyToggleRow(
                    title = Localization.t("privacy_show_occupation", lang),
                    description = Localization.t("privacy_show_occupation_desc", lang),
                    checked = !privacy.hideOccupation,
                    onCheckedChange = { on -> change { it.copy(hideOccupation = !on) } },
                    testTag = "toggle_show_occupation"
                )
            }

            Text(
                Localization.t("privacy_no_tracking", lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.muted
            )

            Text(Localization.t("privacy_section_rights", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RightsRow(
                        icon = { Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        title = Localization.t(if (exporting) "export_running" else "export_data", lang),
                        description = Localization.t("export_data_desc", lang),
                        onClick = startExport
                    )
                    HorizontalDivider()
                    RightsRow(
                        icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = Localization.t("delete_account", lang),
                        description = Localization.t("delete_account_desc", lang),
                        onClick = { showDeleteAccount = true }
                    )
                }
            }
        }
    }

    if (showDeleteAccount) {
        DeleteAccountDialog(repository, lang, onDismiss = { showDeleteAccount = false }, onDeleted = { showDeleteAccount = false })
    }
}

@Composable
private fun PrivacyCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun RightsRow(icon: @Composable () -> Unit, title: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon()
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
        }
    }
}

@Composable
fun PrivacyToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted, lineHeight = 18.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
        )
    }
}
