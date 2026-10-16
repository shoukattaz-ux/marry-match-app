package com.example.ui.screens

import android.content.Intent
import android.provider.Settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.data.NotificationSettings
import com.example.push.PushNotifications
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    onOpenSafety: () -> Unit,
    onOpenPrivacySettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()

    var legalPage by remember { mutableStateOf<LegalPage?>(null) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val (exporting, startExport) = rememberDataExport(repository) { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val context = LocalContext.current
    val notifications by repository.notifications.collectAsState()
    LaunchedEffect(Unit) { repository.loadNotifications() }

    fun changeNotifications(update: (NotificationSettings) -> NotificationSettings) {
        scope.launch {
            repository.updateNotifications(update).onFailure {
                snackbar.showSnackbar(Localization.t("privacy_save_failed", lang))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("settings_title", lang), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            // Language Selection
            Text(Localization.t("language", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(if (lang == AppLanguage.EN) "English" else "Deutsch", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { repository.toggleLanguage() },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (lang == AppLanguage.EN) "Zu Deutsch wechseln" else "Switch to English", fontSize = 12.sp)
                    }
                }
            }

            // Push notifications: which kinds the server sends (saved on the profile).
            Text(Localization.t("settings_notifications", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!PushNotifications.canNotify(context)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(Localization.t("notifications_blocked", lang), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }) { Text(Localization.t("notifications_open_settings", lang)) }
                        HorizontalDivider()
                    }
                    NotificationToggleRow(
                        title = Localization.t("notify_matches", lang),
                        checked = notifications.matches,
                        onCheckedChange = { on -> changeNotifications { it.copy(matches = on) } }
                    )
                    HorizontalDivider()
                    NotificationToggleRow(
                        title = Localization.t("notify_messages", lang),
                        checked = notifications.messages,
                        onCheckedChange = { on -> changeNotifications { it.copy(messages = on) } }
                    )
                    HorizontalDivider()
                    NotificationToggleRow(
                        title = Localization.t("notify_likes", lang),
                        checked = notifications.likes,
                        onCheckedChange = { on -> changeNotifications { it.copy(likes = on) } }
                    )
                    Text(Localization.t("notifications_no_text", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                }
            }

            // Privacy, legal pages and GDPR rights
            Text(Localization.t("gdpr_settings", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingNavRow(icon = Icons.Default.Visibility, title = Localization.t("privacy_settings_title", lang), onClick = onOpenPrivacySettings)
                    HorizontalDivider()
                    SettingNavRow(icon = Icons.Default.Lock, title = Localization.t("privacy_link", lang), onClick = { legalPage = LegalPage.PRIVACY })
                    HorizontalDivider()
                    SettingNavRow(icon = Icons.Default.Gavel, title = Localization.t("terms_link", lang), onClick = { legalPage = LegalPage.TERMS })
                    HorizontalDivider()
                    SettingNavRow(icon = Icons.Default.Policy, title = Localization.t("impressum_title", lang), onClick = { legalPage = LegalPage.IMPRESSUM })
                    HorizontalDivider()
                    SettingNavRow(icon = Icons.Default.Security, title = Localization.t("safety_tips_title", lang), onClick = onOpenSafety)
                    HorizontalDivider()
                    SettingNavRow(
                        icon = Icons.Default.Download,
                        title = Localization.t(if (exporting) "export_running" else "export_data", lang),
                        onClick = startExport
                    )
                }
            }

            Text(
                "${AppConfig.APP_VERSION} • ${AppConfig.PROD_REGION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.muted
            )

            // Log Out Button
            FilledTonalButton(
                onClick = {
                    repository.signOut()
                    onBack()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_logout_btn")
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Localization.t("logout", lang), fontWeight = FontWeight.SemiBold)
            }

            // Delete Account (GDPR Art. 17 Right to Erasure)
            OutlinedButton(
                onClick = { showDeleteAccount = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(Localization.t("delete_account", lang))
            }
        }
    }

    legalPage?.let { LegalPageDialog(it, lang) { legalPage = null } }
    if (showDeleteAccount) {
        DeleteAccountDialog(repository, lang, onDismiss = { showDeleteAccount = false }, onDeleted = { showDeleteAccount = false })
    }
}

@Composable
fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Text("›", fontSize = 20.sp, color = MaterialTheme.extendedColors.muted)
    }
}
