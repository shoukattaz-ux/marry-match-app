package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.LegalTexts
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Which legal page to show. */
enum class LegalPage { IMPRESSUM, PRIVACY, TERMS, CONDUCT }

/** Full-screen legal page with a warning while the texts are drafts or the operator details are missing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalPageDialog(page: LegalPage, lang: AppLanguage, onClose: () -> Unit) {
    val (titleKey, sections) = when (page) {
        LegalPage.IMPRESSUM -> "impressum_title" to LegalTexts.impressum(lang)
        LegalPage.PRIVACY -> LegalTexts.privacy.let { it.titleKey to it.sections(lang) }
        LegalPage.TERMS -> LegalTexts.terms.let { it.titleKey to it.sections(lang) }
        LegalPage.CONDUCT -> LegalTexts.conduct.let { it.titleKey to it.sections(lang) }
    }
    val warnings = buildList {
        if (!AppConfig.LEGAL_DETAILS_COMPLETE && page != LegalPage.CONDUCT) add(Localization.t("legal_details_missing", lang))
        if (!AppConfig.LEGAL_TEXTS_REVIEWED && page != LegalPage.IMPRESSUM) add(Localization.t("legal_draft", lang))
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(Localization.t(titleKey, lang)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = Localization.t("close", lang)) }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                warnings.forEach { w ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(w, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }
                sections.forEach { (heading, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/**
 * Data export (GDPR Art. 15 and 20): loads everything from the server, then lets the user
 * choose where to save the JSON file. Returns the function that starts it.
 */
@Composable
fun rememberDataExport(repository: MarryMatchRepository, onMessage: (String) -> Unit): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by repository.appLanguage.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri == null || json == null) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("no stream")
                }.isSuccess
            }
            busy = false
            onMessage(Localization.t(if (ok) "export_saved" else "export_failed", lang))
        }
    }

    val start: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                repository.exportMyData()
                    .onSuccess { json ->
                        pendingJson = json
                        saveLauncher.launch("marrymatch-data-${LocalDate.now()}.json")
                    }
                    .onFailure {
                        busy = false
                        onMessage(Localization.t("export_failed", lang))
                    }
            }
        }
    }
    return busy to start
}

/** Asks the user to type a word before the account is deleted for good. */
@Composable
fun DeleteAccountDialog(repository: MarryMatchRepository, lang: AppLanguage, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val word = Localization.t("delete_confirm_word", lang)
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(Localization.t("delete_account", lang)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Localization.t("delete_account_body", lang), style = MaterialTheme.typography.bodyMedium)
                Text(String.format(Localization.t("delete_type_word", lang), word), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = typed, onValueChange = { typed = it.take(20) }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                if (failed) Text(Localization.t("delete_failed", lang), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    busy = true
                    failed = false
                    scope.launch {
                        repository.deleteAccount()
                            .onSuccess { onDeleted() }
                            .onFailure { busy = false; failed = true }
                    }
                },
                enabled = !busy && typed.trim().equals(word, ignoreCase = true),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(Localization.t("delete_forever", lang)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(Localization.t("cancel", lang)) } }
    )
}
