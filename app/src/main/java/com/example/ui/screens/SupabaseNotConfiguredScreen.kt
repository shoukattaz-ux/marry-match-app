package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton

@Composable
fun SupabaseNotConfiguredScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { repository.toggleLanguage() },
                    modifier = Modifier.testTag("not_configured_lang_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Switch language",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (lang == AppLanguage.EN) "DE (Deutsch)" else "EN (English)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (lang == AppLanguage.EN) "Supabase Not Configured" else "Supabase nicht konfiguriert",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (lang == AppLanguage.EN)
                    "The app requires valid Supabase credentials to handle real authentication and data securely. Missing configuration was detected."
                else
                    "Die App benötigt gültige Supabase-Zugangsdaten für eine sichere Authentifizierung und Datenspeicherung. Fehlende Konfiguration wurde erkannt.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Configuration status card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == AppLanguage.EN) "Environment Status:" else "Status der Umgebung:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    ConfigStatusRow(
                        label = "SUPABASE_URL",
                        isSet = AppConfig.SUPABASE_URL.isNotBlank() && !AppConfig.SUPABASE_URL.contains("your-project"),
                        value = if (AppConfig.SUPABASE_URL.isBlank()) "Missing" else AppConfig.SUPABASE_URL
                    )

                    ConfigStatusRow(
                        label = "SUPABASE_ANON_KEY",
                        isSet = AppConfig.SUPABASE_ANON_KEY.isNotBlank() && !AppConfig.SUPABASE_ANON_KEY.startsWith("PASTE"),
                        value = if (AppConfig.SUPABASE_ANON_KEY.isBlank()) "Missing" else "sb_publishable_***"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Setup Instructions Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (lang == AppLanguage.EN) "How to Fix This:" else "So beheben Sie das Problem:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "1. Open your project root directory.\n2. In the `.env` file (or AI Studio Secrets panel), set:\n   SUPABASE_URL=https://<your-project>.supabase.co\n   SUPABASE_ANON_KEY=<your-anon-publishable-key>\n3. Rebuild or reload the app."
                        else
                            "1. Öffnen Sie das Projektverzeichnis.\n2. Hinterlegen Sie in der `.env`-Datei (oder im Secrets-Bereich):\n   SUPABASE_URL=https://<ihr-projekt>.supabase.co\n   SUPABASE_ANON_KEY=<ihr-anon-key>\n3. Starten Sie die App neu.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            MarryPrimaryButton(
                text = if (lang == AppLanguage.EN) "Retry Connection" else "Erneut prüfen",
                onClick = {
                    repository.checkSupabaseConfiguration()
                },
                testTag = "supabase_retry_btn"
            )
        }
    }
}

@Composable
private fun ConfigStatusRow(label: String, isSet: Boolean, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isSet) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = if (isSet) "OK" else "MISSING",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isSet) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
