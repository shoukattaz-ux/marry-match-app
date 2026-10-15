package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.ui.theme.extendedColors

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

    var notifyMatch by remember { mutableStateOf(repository.notifyNewMatch) }
    var notifyMessage by remember { mutableStateOf(repository.notifyNewMessage) }
    var notifyLikes by remember { mutableStateOf(repository.notifyLikes) }
    var notifyDaily by remember { mutableStateOf(repository.notifyDailyProfiles) }

    var showImpressumDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lang == AppLanguage.EN) "Settings" else "Einstellungen", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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

            // Push Notifications
            Text(if (lang == AppLanguage.EN) "Push Notifications" else "Benachrichtigungen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotificationToggleRow(
                        title = if (lang == AppLanguage.EN) "New Match" else "Neues Match",
                        checked = notifyMatch,
                        onCheckedChange = { notifyMatch = it; repository.notifyNewMatch = it }
                    )
                    HorizontalDivider()
                    NotificationToggleRow(
                        title = if (lang == AppLanguage.EN) "New Message" else "Neue Nachricht",
                        checked = notifyMessage,
                        onCheckedChange = { notifyMessage = it; repository.notifyNewMessage = it }
                    )
                    HorizontalDivider()
                    NotificationToggleRow(
                        title = if (lang == AppLanguage.EN) "Someone Liked You" else "Jemand hat Sie geliked",
                        checked = notifyLikes,
                        onCheckedChange = { notifyLikes = it; repository.notifyLikes = it }
                    )
                    HorizontalDivider()
                    NotificationToggleRow(
                        title = if (lang == AppLanguage.EN) "Daily Profiles Ready (06:00 Berlin)" else "Tägliche Vorschläge bereit (06:00 Uhr)",
                        checked = notifyDaily,
                        onCheckedChange = { notifyDaily = it; repository.notifyDailyProfiles = it }
                    )
                }
            }

            // Legal & GDPR Compliance
            Text(Localization.t("gdpr_settings", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingNavRow(
                        icon = Icons.Default.Visibility,
                        title = if (lang == AppLanguage.EN) "Privacy & Visibility Controls" else "Privatsphäre & Sichtbarkeit",
                        onClick = onOpenPrivacySettings
                    )
                    HorizontalDivider()
                    SettingNavRow(
                        icon = Icons.Default.Policy,
                        title = Localization.t("impressum_title", lang),
                        onClick = { showImpressumDialog = true }
                    )
                    HorizontalDivider()
                    SettingNavRow(
                        icon = Icons.Default.Lock,
                        title = Localization.t("privacy_link", lang),
                        onClick = { showPrivacyDialog = true }
                    )
                    HorizontalDivider()
                    SettingNavRow(
                        icon = Icons.Default.Security,
                        title = Localization.t("safety_tips_title", lang),
                        onClick = onOpenSafety
                    )
                    HorizontalDivider()
                    SettingNavRow(
                        icon = Icons.Default.Download,
                        title = Localization.t("export_data", lang),
                        onClick = { showExportDialog = true }
                    )
                }
            }

            // Backend & Config Status (Section 17)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (lang == AppLanguage.EN) "Cloud Hosting & Architecture" else "Cloud-Hosting & Architektur",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "• Database Region: ${AppConfig.PROD_REGION}\n• Version: ${AppConfig.APP_VERSION}\n• Supabase Status: ${if (AppConfig.isConfigured) "Connected" else "Local Prototype Mode (Placeholders Active)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            // Delete Account (GDPR Art. 17 Right to Erasure)
            OutlinedButton(
                onClick = { showDeleteAccountConfirm = true },
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

    // Impressum Dialog
    if (showImpressumDialog) {
        AlertDialog(
            onDismissRequest = { showImpressumDialog = false },
            title = { Text("Impressum (Legal Notice)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Angaben gemäß § 5 TMG:", fontWeight = FontWeight.Bold)
                    Text("MarryMatch Germany GmbH (i.G.)\nFriedrichstraße 123\n10117 Berlin, Deutschland")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Kontakt:", fontWeight = FontWeight.Bold)
                    Text("E-Mail: support@marrymatch.de\nWeb: https://marrymatch.de")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Registereintrag:", fontWeight = FontWeight.Bold)
                    Text("Amtsgericht Charlottenburg (Berlin)\nRegisternummer: HRB 987654 B")
                }
            },
            confirmButton = {
                TextButton(onClick = { showImpressumDialog = false }) { Text("Close") }
            }
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Datenschutzerklärung (Privacy)") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Verantwortliche Stelle & DSGVO:", fontWeight = FontWeight.Bold)
                    Text("Wir verarbeiten personenbezogene Daten streng im Einklang mit der Europäischen Datenschutz-Grundverordnung (DSGVO). Alle Datenbanken befinden sich in Frankfurt am Main (EU-Region).")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("2. Besondere Datenkategorien (Art. 9 DSGVO):", fontWeight = FontWeight.Bold)
                    Text("Angaben zur Religion und biometrische Selfie-Prüfungen erfolgen ausschließlich auf Basis Ihrer ausdrücklichen freiwilligen Einwilligung. Das Prüf-Selfie wird sofort nach der manuellen Moderation unwiderruflich gelöscht.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("3. Standortdaten:", fontWeight = FontWeight.Bold)
                    Text("Der Standort wird lediglich einmalig zur Prüfung des Aufenthalts in Deutschland abgefragt. Exakte GPS-Koordinaten werden zu keinem Zeitpunkt in der Datenbank gespeichert.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") }
            }
        )
    }

    // GDPR Export Dialog
    if (showExportDialog) {
        val jsonOutput = remember { repository.exportDataJson() }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("GDPR Article 20 Data Export") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Here is your complete personal data file in standard JSON format:")
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = jsonOutput,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Done") }
            }
        )
    }

    // Delete Account Confirmation
    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = { Text(Localization.t("delete_account", lang)) },
            text = {
                Text(
                    if (lang == AppLanguage.EN) "This action is irreversible. Under GDPR Article 17, your profile, photos, prompts, matches, and chat history will be permanently deleted immediately."
                    else "Diese Aktion kann nicht rückgängig gemacht werden. Gemäß DSGVO Artikel 17 werden Ihr Profil, alle Fotos, Prompts, Matches und Chat-Verläufe sofort unwiderruflich gelöscht."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirm = false
                        repository.deleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Permanently Delete", color = androidx.compose.ui.graphics.Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) { Text("Cancel") }
            }
        )
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
