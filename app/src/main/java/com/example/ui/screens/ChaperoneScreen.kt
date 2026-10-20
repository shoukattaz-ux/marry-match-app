package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.ChaperoneCode
import com.example.data.ChatTime
import com.example.data.MarryMatchRepository
import com.example.data.Ward
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.ChaperoneViewModel
import kotlinx.coroutines.delay

/**
 * Chaperone mode. A member invites a family member with a one-time code and can remove them at
 * any time. A chaperone sees the chats of the members they read along for, read-only.
 * With [chaperoneOnly] the screen is the whole app for an account without a profile; it then
 * offers language, sign-out and account deletion instead of a back button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaperoneScreen(
    repository: MarryMatchRepository,
    onBack: (() -> Unit)? = null,
    chaperoneOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val vm: ChaperoneViewModel = viewModel(key = "chaperone_$chaperoneOnly") {
        ChaperoneViewModel(repository.chaperone, hasProfile = !chaperoneOnly)
    }
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAccept by remember { mutableStateOf(false) }
    var confirmEndMine by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf<Ward?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorKey, showAccept) {
        // While the code dialog is open, it shows the error itself.
        if (!showAccept) state.errorKey?.let {
            snackbar.showSnackbar(t(it))
            vm.clearError()
        }
    }
    LaunchedEffect(state.joinedMember) {
        state.joinedMember?.let {
            showAccept = false
            snackbar.showSnackbar(String.format(t("chaperone_joined"), it))
            vm.clearJoined()
        }
    }
    // Fresh state each time the screen opens (the view model outlives the screen).
    LaunchedEffect(Unit) { vm.refresh() }
    // While a code is waiting to be used, check now and then so the member sees the chaperone join.
    val waitingForChaperone = state.mine?.let { !it.isActive } == true
    LaunchedEffect(waitingForChaperone) {
        while (waitingForChaperone) {
            delay(10_000)
            vm.refresh(quiet = true)
        }
    }
    // A chaperone-only account that no longer reads along for anyone goes back to sign-up.
    LaunchedEffect(chaperoneOnly, state.loaded, state.wards.isEmpty()) {
        if (chaperoneOnly && state.loaded && state.wards.isEmpty()) {
            repository.onNoLongerChaperone()
        }
    }

    val openMatch = state.openMatch
    if (openMatch != null) {
        BackHandler { vm.closeChat() }
        WardChat(
            memberName = state.openWard?.memberName.orEmpty(),
            otherName = openMatch.name,
            messages = state.messages,
            loading = state.busy,
            lang = lang,
            onBack = vm::closeChat,
            onRefresh = vm::reloadChat
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("chaperone_title"), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chaperone_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back"))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = t("chaperone_refresh")) }
                    if (chaperoneOnly) {
                        TextButton(onClick = { repository.toggleLanguage() }) {
                            Text(if (lang == AppLanguage.EN) "DE" else "EN", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = null) }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(t("logout")) }, onClick = { showMenu = false; repository.signOut() })
                            DropdownMenuItem(
                                text = { Text(t("delete_account"), color = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showDeleteAccount = true }
                            )
                        }
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
                    Icon(Icons.Default.FamilyRestroom, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Text(t(if (chaperoneOnly) "chaperone_intro_guardian" else "chaperone_intro"), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.loading && state.wards.isEmpty() && state.mine == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }

            if (!chaperoneOnly) {
                Text(t("chaperone_yours"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ChaperoneCard {
                    val mine = state.mine
                    when {
                        mine == null -> {
                            Text(t("chaperone_none"), style = MaterialTheme.typography.bodyMedium)
                            Button(
                                onClick = vm::createInvite,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("chaperone_invite_btn")
                            ) { Text(t("chaperone_create_code")) }
                        }
                        mine.isActive -> {
                            Text(
                                String.format(t("chaperone_active"), mine.name.orEmpty(), mine.acceptedAt?.let { ChatTime.format(it) }.orEmpty()),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(t("chaperone_active_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                            OutlinedButton(
                                onClick = { confirmEndMine = true },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("chaperone_remove_btn")
                            ) { Text(t("chaperone_remove"), color = MaterialTheme.colorScheme.error) }
                        }
                        else -> InviteCodeBlock(
                            code = state.invite?.code,
                            expiresAt = mine.expiresAt,
                            busy = state.busy,
                            lang = lang,
                            onNewCode = vm::createInvite,
                            onCancel = vm::endMine
                        )
                    }
                }
            }

            Text(t("chaperone_for_others"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.wards.isEmpty()) {
                Text(t("chaperone_for_none"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            }
            state.wards.forEach { ward ->
                ChaperoneCard {
                    Text(ward.memberName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        String.format(t("chaperone_since"), ward.since?.let { ChatTime.format(it) }.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.muted
                    )
                    if (ward.matches.isEmpty()) {
                        Text(t("chaperone_no_matches"), style = MaterialTheme.typography.bodySmall)
                    }
                    ward.matches.forEach { match ->
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.openChat(ward, match) }
                                .padding(vertical = 8.dp)
                                .testTag("chaperone_match_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (match.age > 0) "${match.name}, ${match.age}" else match.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    match.lastMessageAt?.let { String.format(t("chaperone_last_message"), ChatTime.format(it)) }
                                        ?: t("chaperone_no_messages_yet"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.extendedColors.muted
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                    TextButton(onClick = { confirmLeave = ward }, enabled = !state.busy) {
                        Text(t("chaperone_leave"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            OutlinedButton(
                onClick = { showAccept = true },
                modifier = Modifier.fillMaxWidth().testTag("chaperone_enter_code_btn")
            ) { Text(t("chaperone_enter_code")) }
        }
    }

    if (showAccept) {
        AcceptChaperoneCodeDialog(
            lang = lang,
            busy = state.busy,
            errorText = state.errorKey?.let(t),
            onDismiss = { showAccept = false; vm.clearError() }
        ) { code, name ->
            vm.accept(code, name)
        }
    }
    if (confirmEndMine) {
        AlertDialog(
            onDismissRequest = { confirmEndMine = false },
            title = { Text(t("chaperone_remove")) },
            text = { Text(t("chaperone_remove_confirm")) },
            confirmButton = { TextButton(onClick = { confirmEndMine = false; vm.endMine() }) { Text(t("chaperone_remove"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEndMine = false }) { Text(t("cancel")) } }
        )
    }
    confirmLeave?.let { ward ->
        AlertDialog(
            onDismissRequest = { confirmLeave = null },
            title = { Text(t("chaperone_leave")) },
            text = { Text(String.format(t("chaperone_leave_confirm"), ward.memberName)) },
            confirmButton = { TextButton(onClick = { confirmLeave = null; vm.leave(ward) }) { Text(t("chaperone_leave"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmLeave = null }) { Text(t("cancel")) } }
        )
    }
    if (showDeleteAccount) {
        DeleteAccountDialog(repository, lang, onDismiss = { showDeleteAccount = false }, onDeleted = { showDeleteAccount = false })
    }
}

@Composable
private fun ChaperoneCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** The open invitation: the code (only right after creating it; the server keeps just a hash). */
@Composable
private fun InviteCodeBlock(
    code: String?,
    expiresAt: String?,
    busy: Boolean,
    lang: AppLanguage,
    onNewCode: () -> Unit,
    onCancel: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val context = LocalContext.current
    if (code != null) {
        val shown = code.chunked(4).joinToString("-")
        Text(t("chaperone_code_label"), style = MaterialTheme.typography.bodyMedium)
        SelectionContainer {
            Text(
                shown,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    .padding(vertical = 12.dp)
                    .testTag("chaperone_code")
            )
        }
        expiresAt?.let {
            Text(String.format(t("chaperone_code_valid_until"), ChatTime.format(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
        }
        Button(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, String.format(t("chaperone_share_text"), shown))
                }
                context.startActivity(Intent.createChooser(send, null))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(t("chaperone_share"))
        }
    } else {
        // The app was closed since the code was made: it cannot be shown again, only replaced.
        Text(t("chaperone_code_hidden"), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onNewCode, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("chaperone_new_code")) }
    }
    TextButton(onClick = onCancel, enabled = !busy) { Text(t("chaperone_cancel_invite"), color = MaterialTheme.colorScheme.error) }
}

/** Entering a code: the chaperone also gives the name the member will see. */
@Composable
fun AcceptChaperoneCodeDialog(
    lang: AppLanguage,
    busy: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onAccept: (String, String) -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    // Kept tidy while typing or pasting: "abcd ef23" becomes "ABCD-EF23", cursor at the end.
    var codeField by remember { mutableStateOf(TextFieldValue("")) }
    val code = ChaperoneCode.normalize(codeField.text)
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("chaperone_enter_code")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("chaperone_enter_code_desc"), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = codeField,
                    onValueChange = { v ->
                        val shown = ChaperoneCode.format(ChaperoneCode.normalize(v.text))
                        codeField = TextFieldValue(shown, selection = TextRange(shown.length))
                    },
                    label = { Text(t("chaperone_code_field")) },
                    placeholder = { Text("ABCD-EF23", fontFamily = FontFamily.Monospace) },
                    supportingText = {
                        Text(
                            if (ChaperoneCode.hasLookAlike(code)) t("chaperone_code_lookalike")
                            else String.format(t("chaperone_code_count"), code.length, ChaperoneCode.LENGTH),
                            color = if (ChaperoneCode.hasLookAlike(code)) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.muted
                        )
                    },
                    isError = ChaperoneCode.hasLookAlike(code),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, letterSpacing = 2.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().testTag("chaperone_code_field")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(t("chaperone_your_name")) },
                    supportingText = { Text(t("chaperone_your_name_hint")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("chaperone_name_field")
                )
                if (errorText != null) {
                    Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAccept(code, name) }, enabled = !busy && ChaperoneCode.isComplete(code) && name.isNotBlank()) {
                Text(t("chaperone_join"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

/** A member's chat as the chaperone sees it: read-only, without photos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WardChat(
    memberName: String,
    otherName: String,
    messages: List<com.example.data.WardMessage>,
    loading: Boolean,
    lang: AppLanguage,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$memberName & $otherName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(t("chaperone_read_only"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) } },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = t("chaperone_refresh")) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Surface(color = MaterialTheme.extendedColors.surfaceVariantSubtle, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Text(t("chaperone_only_since_joined"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp))
                }
            }
            if (loading && messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (messages.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(t("chaperone_no_messages_yet"), color = MaterialTheme.extendedColors.muted, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = msg.fromMember
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                (if (mine) memberName else otherName) + " · " + ChatTime.format(msg.sentAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.extendedColors.muted
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Text(mediaLabel(msg.type, lang, msg.body.orEmpty()), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "📷 Photo: caption" for media; plain text otherwise. Shared with the match list. */
fun mediaLabel(type: String?, lang: AppLanguage, body: String): String {
    val key = when (type) {
        "image" -> "media_label_photo"
        "voice" -> "media_label_voice"
        "video" -> "media_label_video"
        else -> return body
    }
    val label = Localization.t(key, lang)
    return if (body.isBlank()) label else "$label: $body"
}
