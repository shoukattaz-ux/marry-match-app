package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    onExportData: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val isIncognito by repository.isIncognito.collectAsState()
    val visibleOnlyToVerified by repository.visibleOnlyToVerified.collectAsState()
    val showExactCity by repository.showExactCity.collectAsState()
    val showReligion by repository.showReligionOnProfile.collectAsState()
    val showOccupation by repository.showOccupationOnProfile.collectAsState()
    val sendReadReceipts by repository.sendReadReceipts.collectAsState()
    val analyticsConsent by repository.analyticsConsent.collectAsState()
    val currentUser by repository.currentUser.collectAsState()

    var showSnackbarMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (lang == AppLanguage.EN) "Privacy & Data Visibility" else "Privatsphäre & Sichtbarkeit",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("privacy_settings_back_btn")
                    ) {
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
            // Privacy Header Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            text = if (lang == AppLanguage.EN) "Privacy-First Matching" else "Datenschutz an erster Stelle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == AppLanguage.EN)
                                "You maintain full sovereignty over your data under the GDPR. Choose who discovers your profile and what information is visible."
                            else
                                "Sie behalten gemäß DSGVO die volle Kontrolle über Ihre Daten. Bestimmen Sie, wer Sie sieht und welche Angaben sichtbar sind.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                    }
                }
            }

            // Section 1: Discovery Visibility
            Text(
                text = if (lang == AppLanguage.EN) "Discovery & Profile Exposure" else "Sichtbarkeit bei der Partnersuche",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Incognito Mode
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Pause Discovery (Incognito)" else "Sichtbarkeit pausieren (Inkognito)",
                        description = if (lang == AppLanguage.EN)
                            "Hide your profile from new daily sets. You will still see and chat with your existing matches."
                        else
                            "Versteckt Ihr Profil vor neuen täglichen Vorschlägen. Bestehende Matches und Chats bleiben aktiv.",
                        checked = isIncognito,
                        onCheckedChange = {
                            repository.setIncognito(it)
                            showSnackbarMessage = if (it) "Incognito mode activated" else "Profile active in daily discovery"
                        },
                        testTag = "toggle_incognito"
                    )

                    HorizontalDivider()

                    // Verified Members Only
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Visible Only to Verified Members" else "Nur für verifizierte Mitglieder sichtbar",
                        description = if (lang == AppLanguage.EN)
                            "Only show your profile to members who have completed the selfie verification with a blue tick."
                        else
                            "Zeigt Ihr Profil nur Personen, die die Selfie-Verifizierung mit blauem Häkchen erfolgreich abgeschlossen haben.",
                        checked = visibleOnlyToVerified,
                        onCheckedChange = {
                            repository.setVisibleOnlyToVerified(it)
                        },
                        testTag = "toggle_visible_only_verified"
                    )
                }
            }

            // Section 2: Data Minimization & Field Granularity
            Text(
                text = if (lang == AppLanguage.EN) "Data Minimization & Profile Fields" else "Datenminimierung & Profilangaben",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Location Granularity
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Display Exact City" else "Genaue Stadt anzeigen",
                        description = if (showExactCity) {
                            if (lang == AppLanguage.EN) "Currently showing: ${currentUser.city}, ${currentUser.region}" else "Aktuell sichtbar: ${currentUser.city}, ${currentUser.region}"
                        } else {
                            if (lang == AppLanguage.EN) "City is hidden. Only region is shown: ${currentUser.region}" else "Stadt ist verborgen. Nur Bundesland sichtbar: ${currentUser.region}"
                        },
                        checked = showExactCity,
                        onCheckedChange = { repository.setShowExactCity(it) },
                        testTag = "toggle_show_city"
                    )

                    HorizontalDivider()

                    // Religion (GDPR Art. 9 Special Category Data)
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Show Religion & Worldview" else "Religion & Weltanschauung anzeigen",
                        description = if (lang == AppLanguage.EN)
                            "Under GDPR Art. 9, this is special-category data. You can hide or erase it at any moment."
                        else
                            "Gemäß Art. 9 DSGVO sind dies besondere Datenkategorien. Sie können diese jederzeit ausblenden.",
                        checked = showReligion,
                        onCheckedChange = { repository.setShowReligionOnProfile(it) },
                        testTag = "toggle_show_religion"
                    )

                    HorizontalDivider()

                    // Occupation
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Show Occupation & Career" else "Beruf & Karriere anzeigen",
                        description = if (lang == AppLanguage.EN) "Display your job title on your public profile card." else "Zeigt Ihren Beruf im Profil an.",
                        checked = showOccupation,
                        onCheckedChange = { repository.setShowOccupationOnProfile(it) },
                        testTag = "toggle_show_occupation"
                    )
                }
            }

            // Section 3: Chat & Communication Privacy
            Text(
                text = if (lang == AppLanguage.EN) "Chat & Interaction Privacy" else "Chat- & Interaktions-Datenschutz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Send Read Receipts" else "Lesebestätigungen senden",
                        description = if (lang == AppLanguage.EN)
                            "Let matches see the double checkmark when you read their message."
                        else
                            "Lässt Matches sehen, wenn Sie Nachrichten gelesen haben.",
                        checked = sendReadReceipts,
                        onCheckedChange = { repository.setSendReadReceipts(it) },
                        testTag = "toggle_read_receipts"
                    )
                }
            }

            // Section 4: Analytics & Telemetry (GDPR Opt-in)
            Text(
                text = if (lang == AppLanguage.EN) "Analytics & App Diagnostics (Opt-in)" else "Analyse & App-Diagnose (Opt-in)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrivacyToggleRow(
                        title = if (lang == AppLanguage.EN) "Anonymous Crash Reports & Diagnostics" else "Anonyme Absturzberichte & Diagnose",
                        description = if (lang == AppLanguage.EN)
                            "Helps us identify bugs and fix crashes. In the EU, this is strictly opt-in and disabled by default until you consent."
                        else
                            "Hilft uns, Fehler zu beheben. In der EU standardmäßig deaktiviert, bis Sie einwilligen.",
                        checked = analyticsConsent,
                        onCheckedChange = { repository.setAnalyticsConsent(it) },
                        testTag = "toggle_analytics_consent"
                    )
                }
            }

            // Section 5: GDPR Statutory Rights Actions
            Text(
                text = if (lang == AppLanguage.EN) "GDPR Rights & Data Management" else "DSGVO-Betroffenenrechte",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Export Data
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExportData)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(
                                    text = Localization.t("export_data", lang),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (lang == AppLanguage.EN) "Receive a machine-readable JSON archive of all your stored data." else "Maschinenlesbare JSON-Datei Ihrer gespeicherten Daten herunterladen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.extendedColors.muted
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Delete Account
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDeleteAccount)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column {
                                Text(
                                    text = Localization.t("delete_account", lang),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (lang == AppLanguage.EN) "Irrevocably erase your profile, photos, matches, and chats under Art. 17 GDPR." else "Profil, Fotos, Matches und Chats gemäß Art. 17 DSGVO unwiderruflich löschen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.extendedColors.muted
                                )
                            }
                        }
                    }
                }
            }

            if (showSnackbarMessage != null) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showSnackbarMessage = null }) {
                            Text("OK", color = Color.White)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(showSnackbarMessage!!)
                }
            }
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.muted,
                lineHeight = 18.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
        )
    }
}
