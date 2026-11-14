package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.model.SignatureCatalog
import com.example.ui.components.CardFonts
import com.example.ui.components.SignatureCardView
import com.example.ui.components.cardStyle
import com.example.ui.viewmodel.SignatureCardViewModel

/**
 * Profile → Signature Card. A live preview of the real card on top, then the design, "I'm looking
 * for", "Why I'm here" (1-3 tags) and "My vibe". Locked with an explanation until she is
 * selfie-verified. For every member, men and women (migration 047).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SignatureCardEditorScreen(repository: MarryMatchRepository, onBack: () -> Unit, onOpenVerification: () -> Unit) {
    val lang by repository.appLanguage.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val german = lang == com.example.core.AppLanguage.DE
    val vm: SignatureCardViewModel = viewModel(key = "signature_card") { SignatureCardViewModel(repository.cards) }
    val state by vm.state.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("card_title"), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) } },
                actions = {
                    if (state.unlocked) {
                        TextButton(onClick = vm::save, enabled = !state.saving && state.changed, modifier = Modifier.testTag("card_save_top")) {
                            Text(t("card_save"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp).testTag("card_editor"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.loadFailed -> {
                    Text(t("card_load_failed"), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = vm::load) { Text(t("retry")) }
                }
                !state.unlocked -> {
                    // Shown so she knows what she unlocks; the saved card (if any) is hidden right now.
                    LockedNote(t("card_locked_title"), t(if (state.saved != null) "card_hidden_body" else "card_locked_body"))
                    Button(onClick = onOpenVerification, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(50.dp).testTag("card_verify")) {
                        Text(t("card_verify_now"), fontWeight = FontWeight.Bold)
                    }
                    SignatureCardView(card = state.draft.copy(lookingFor = state.draft.lookingFor.ifBlank { t(if (state.woman) "card_example_looking_for" else "card_example_looking_for_m") },
                        reasons = state.draft.reasons.ifEmpty { listOf("marriage_soon", "family_first") },
                        vibeEmoji = state.draft.vibeEmoji ?: "☕", vibe = state.draft.vibe ?: t("card_example_vibe")), lang = lang)
                }
                else -> {
                    // Live preview: exactly what men will see.
                    Text(t("card_preview"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SignatureCardView(
                        card = state.draft.copy(lookingFor = state.draft.lookingFor.ifBlank { t(if (state.woman) "card_example_looking_for" else "card_example_looking_for_m") }),
                        lang = lang
                    )

                    Section(t("card_design"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(SignatureCatalog.TEMPLATES) { template ->
                            TemplateSwatch(template, t("card_template_$template"), template == state.draft.template) { vm.setTemplate(template) }
                        }
                    }

                    Section(t("card_looking_for"))
                    OutlinedTextField(
                        value = state.draft.lookingFor,
                        onValueChange = vm::setLookingFor,
                        placeholder = { Text(t(if (state.woman) "card_example_looking_for" else "card_example_looking_for_m")) },
                        supportingText = { Text("${state.draft.lookingFor.length} / ${SignatureCatalog.MAX_LOOKING_FOR} · ${t("card_any_language")}") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().testTag("card_looking_for_field")
                    )

                    Section(t("card_why_here") + "  (${state.draft.reasons.size}/${SignatureCatalog.MAX_REASONS})")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SignatureCatalog.REASONS.forEach { reason ->
                            FilterChip(
                                selected = reason.key in state.draft.reasons,
                                onClick = { vm.toggleReason(reason.key) },
                                label = { Text(reason.label(german)) },
                                modifier = Modifier.testTag("card_reason_${reason.key}")
                            )
                        }
                    }

                    Section(t("card_my_vibe"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SignatureCatalog.EMOJIS.forEach { emoji ->
                            val selected = emoji == (state.draft.vibeEmoji ?: "")
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                    .clickable { vm.setVibeEmoji(emoji) }
                            ) { Text(emoji, fontSize = 20.sp) }
                        }
                    }
                    OutlinedTextField(
                        value = state.draft.vibe.orEmpty(),
                        onValueChange = vm::setVibe,
                        placeholder = { Text(t("card_example_vibe")) },
                        supportingText = { Text("${state.draft.vibe.orEmpty().length} / ${SignatureCatalog.MAX_VIBE} · ${t("card_optional")}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("card_vibe_field")
                    )

                    Text(t("card_rules"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    (state.errorKey ?: state.problem?.takeIf { state.changed && state.draft.lookingFor.isNotBlank() && state.draft.reasons.isNotEmpty() })?.let {
                        Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("card_error"))
                    }
                    state.doneKey?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(t(it), color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("card_done"))
                        }
                    }
                    if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Button(
                        onClick = vm::save,
                        enabled = !state.saving && state.changed,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("card_save")
                    ) { Text(t(if (state.saved == null) "card_publish" else "card_save"), fontWeight = FontWeight.Bold) }
                    if (state.saved != null) {
                        TextButton(onClick = { confirmRemove = true }, enabled = !state.saving, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(t("card_remove"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(t("card_remove_title")) },
            text = { Text(t("card_remove_body")) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; vm.delete() }) { Text(t("card_remove"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(t("cancel")) } }
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun LockedNote(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().testTag("card_locked")) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Column {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** A small tile in the template's colours with its name, outlined when chosen. */
@Composable
private fun TemplateSwatch(template: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val style = cardStyle(template)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp).clickable(onClick = onClick).testTag("card_template_$template")) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 96.dp, height = 64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(style.background))
                .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else style.panelBorder), RoundedCornerShape(14.dp))
        ) {
            Text("Aa", style = style.headline.copy(color = style.text, fontSize = 22.sp))
        }
        Spacer(Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelSmall.copy(fontFamily = CardFonts.Manrope), maxLines = 1)
    }
}
