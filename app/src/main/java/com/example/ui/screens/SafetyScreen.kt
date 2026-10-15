package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import com.example.model.UserProfile
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCenterScreen(
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Localization.t("safety_tips_title", lang), style = MaterialTheme.typography.titleLarge) },
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
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            text = if (lang == AppLanguage.EN) "Our Trust & Safety Promise" else "Unser Sicherheits- & Vertrauensversprechen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == AppLanguage.EN) "Marriage-minded dating requires zero tolerance for fake identities, harassment, and financial scams."
                            else "Eheorientiertes Kennenlernen erfordert null Toleranz gegenüber gefälschten Profilen, Belästigung und Betrug.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                    }
                }
            }

            Text(
                text = if (lang == AppLanguage.EN) "Core Safety Guidelines" else "Wichtige Sicherheitsrichtlinien",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            SafetyTipCard(
                icon = "☕",
                title = if (lang == AppLanguage.EN) "Meet in Public Places" else "Treffen an öffentlichen Orten",
                description = if (lang == AppLanguage.EN) "For your first few meetings, choose bustling cafes, parks, or restaurants in daylight."
                else "Wählen Sie für die ersten Treffen belebte Cafés, Parks oder Restaurants bei Tageslicht."
            )

            SafetyTipCard(
                icon = "🛑",
                title = if (lang == AppLanguage.EN) "Never Send Money" else "Niemals Geld überweisen",
                description = if (lang == AppLanguage.EN) "Never transfer money, purchase gift cards, or share financial credentials, even for claimed emergencies."
                else "Überweisen Sie niemals Geld, Gutscheine oder Kontodaten – auch nicht bei angeblichen Notfällen."
            )

            SafetyTipCard(
                icon = "📱",
                title = if (lang == AppLanguage.EN) "Video Call First" else "Zuerst einen Videoanruf machen",
                description = if (lang == AppLanguage.EN) "A brief 5-minute video call before an in-person date confirms identity and establishes comfortable chemistry."
                else "Ein kurzer Videoanruf vor dem persönlichen Treffen bestätigt die Identität und schafft Vertrauen."
            )

            SafetyTipCard(
                icon = "👥",
                title = if (lang == AppLanguage.EN) "Tell a Trusted Friend" else "Freunde oder Familie informieren",
                description = if (lang == AppLanguage.EN) "Share your meeting location and expected time with someone close to you."
                else "Informieren Sie eine vertraute Person über den Treffpunkt und die geplante Uhrzeit."
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Community Code of Conduct
            Text(
                text = Localization.t("code_of_conduct_title", lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• " + if (lang == AppLanguage.EN) "Treat all members with dignity, respect, and clear matrimonial intent." else "Behandeln Sie alle Mitglieder mit Respekt und ernsthaften Heiratsabsichten.", style = MaterialTheme.typography.bodySmall)
                    Text("• " + if (lang == AppLanguage.EN) "Strictly 18+ only community across Germany." else "Ausschließlich für Erwachsene ab 18 Jahren mit Wohnsitz in Deutschland.", style = MaterialTheme.typography.bodySmall)
                    Text("• " + if (lang == AppLanguage.EN) "Zero tolerance for hate speech, harassment, or commercial promotion." else "Keine Diskriminierung, Belästigung oder gewerbliche Werbung.", style = MaterialTheme.typography.bodySmall)
                    Text("• " + if (lang == AppLanguage.EN) "Authentic, recent photos only. Stolen imagery results in instant termination." else "Nur authentische, aktuelle Fotos. Fremde Bilder führen zum sofortigen Ausschluss.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun SafetyTipCard(icon: String, title: String, description: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(icon, fontSize = 24.sp)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            }
        }
    }
}

@Composable
fun ReportProfileDialog(
    userToReport: UserProfile,
    repository: MarryMatchRepository,
    onDismiss: () -> Unit,
    messageId: String? = null
) {
    val lang by repository.appLanguage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedReason by remember { mutableStateOf("fake") }
    var detailsText by remember { mutableStateOf("") }
    var alsoBlock by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    // Keys match the reasons allowed by the reports table.
    val reasons = listOf("fake", "harassment", "scam", "underage", "photo", "other")

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text(Localization.t("report_title", lang))
            }
        },
        text = {
            if (!submitted) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = String.format(Localization.t("report_reporting", lang), userToReport.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    reasons.forEach { key ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = key }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedReason == key,
                                onClick = { selectedReason = key }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Localization.t("reason_$key", lang), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedTextField(
                        value = detailsText,
                        onValueChange = { if (it.length <= 1000) detailsText = it },
                        placeholder = { Text(Localization.t("report_details_hint", lang)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoBlock = !alsoBlock }
                    ) {
                        Checkbox(
                            checked = alsoBlock,
                            onCheckedChange = { alsoBlock = it },
                            modifier = Modifier.testTag("report_also_block")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Localization.t("report_also_block", lang), style = MaterialTheme.typography.bodySmall)
                    }

                    errorKey?.let { key ->
                        Text(
                            Localization.t(key, lang),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp))
                    Text(
                        text = Localization.t("report_submitted", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            if (!submitted) {
                Button(
                    onClick = {
                        isSending = true
                        errorKey = null
                        coroutineScope.launch {
                            val result = repository.submitReport(
                                reportedUserId = userToReport.id,
                                reportedUserName = userToReport.displayName,
                                reason = selectedReason,
                                details = detailsText,
                                messageId = messageId
                            )
                            isSending = false
                            if (result.isSuccess) {
                                if (alsoBlock) repository.blockUser(userToReport.id)
                                submitted = true
                            } else {
                                val limitHit = result.exceptionOrNull()?.message?.contains("report_limit") == true
                                errorKey = if (limitHit) "report_limit" else "report_failed"
                            }
                        }
                    },
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("report_submit_btn")
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(Localization.t("report_submit", lang), color = Color.White)
                    }
                }
            } else {
                Button(onClick = onDismiss) {
                    Text(Localization.t("done", lang))
                }
            }
        },
        dismissButton = {
            if (!submitted) {
                TextButton(onClick = onDismiss, enabled = !isSending) {
                    Text(Localization.t("cancel", lang))
                }
            }
        }
    )
}
