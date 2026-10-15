package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MarryMatchRepository
import com.example.ui.viewmodel.ChatViewModel
import com.example.model.ChatMessage
import com.example.model.MatchItem
import com.example.ui.components.SafetyNoticeBanner
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    match: MatchItem,
    repository: MarryMatchRepository,
    onBack: () -> Unit,
    onOpenReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val user by repository.currentUser.collectAsState()
    val chatViewModel: ChatViewModel = viewModel(key = "chat_${match.id}") {
        ChatViewModel(match.id, repository.currentUser.value.id, repository.chat)
    }
    val chatState by chatViewModel.state.collectAsState()
    val messages = chatState.messages

    var messageInput by remember { mutableStateOf("") }
    var lastDraft by remember { mutableStateOf("") }
    var reportedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showPrivacyInfoDialog by remember { mutableStateOf(false) }
    var showUnmatchConfirm by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Gentle warning before sharing money talk, contact details or links early
    val containsSuspiciousContent = remember(messageInput) { ChatSafety.looksRisky(messageInput) }

    val starters = listOf(
        Localization.t("starter_sunday", lang),
        String.format(Localization.t("starter_city", lang), match.matchedUser.city.ifBlank { "Deutschland" }),
        Localization.t("starter_family", lang)
    )

    // Keep the newest message in view
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }

    // A failed send puts the text back so nothing is lost
    LaunchedEffect(chatState.errorKey) {
        if (chatState.errorKey != null && messageInput.isEmpty()) messageInput = lastDraft
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                        ) {
                            AsyncImage(
                                model = match.matchedUser.photos.firstOrNull() ?: "",
                                contentDescription = match.matchedUser.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = match.matchedUser.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                VerifiedBadge(
                                    selfieVerified = match.matchedUser.selfieVerified,
                                    phoneVerified = match.matchedUser.phoneVerified
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.extendedColors.muted,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "${match.matchedUser.city} • Private EU Storage",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.extendedColors.muted
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showPrivacyInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Chat Privacy Guarantee",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (lang == AppLanguage.EN) "Privacy & Data Info" else "Datenschutz-Info") },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showPrivacyInfoDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Localization.t("unmatch", lang)) },
                            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                showUnmatchConfirm = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Localization.t("block", lang), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showBlockConfirm = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Localization.t("report", lang), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onOpenReport()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Privacy & Safety Top Ribbon
            Surface(
                color = MaterialTheme.extendedColors.surfaceVariantSubtle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "Protected under GDPR • Unmatching irrevocably deletes this chat for both users."
                        else
                            "DSGVO-geschützt • Bei Auflösung des Matches wird dieser Chat für beide Seiten gelöscht.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Real-time messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Conversation Starters at top if conversation is short
                if (messages.size <= 2) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("💡", fontSize = 16.sp)
                                    Text(
                                        text = Localization.t("conversation_starters", lang),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                starters.forEach { starter ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable { messageInput = starter }
                                    ) {
                                        Text(
                                            text = "💬 $starter",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (chatState.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }
                }
                if (chatState.loadFailed) {
                    item {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Localization.t("chat_load_failed", lang), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                            TextButton(onClick = chatViewModel::load) { Text(Localization.t("retry", lang)) }
                        }
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == user.id
                    ChatBubble(
                        message = msg,
                        isMe = isMe,
                        onLongPress = if (isMe) null else ({ reportedMessage = msg })
                    )
                }
            }

            chatState.errorKey?.let { key ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().clickable { chatViewModel.clearError() }) {
                    Text(
                        Localization.t(key, lang),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Proactive Data Leak / Scam Warning
            if (containsSuspiciousContent) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🛡️", fontSize = 20.sp)
                        Column {
                            Text(
                                text = if (lang == AppLanguage.EN) "Privacy & Safety Alert" else "Sicherheits- & Datenschutzhinweis",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = if (lang == AppLanguage.EN)
                                    "Never transfer money or share private banking details. Keep communication on MarryMatch until you have safely met in person."
                                else
                                    "Überweisen Sie niemals Geld und geben Sie keine Kontodaten weiter. Bleiben Sie zu Ihrem Schutz vorerst in der App.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Accessible Input Bar with Character Counter
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { if (it.length <= 2000) messageInput = it },
                            placeholder = { Text(Localization.t("type_message", lang), fontSize = 14.sp) },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        IconButton(
                            onClick = {
                                if (messageInput.isNotBlank()) {
                                    lastDraft = messageInput
                                    chatViewModel.send(messageInput)
                                    messageInput = ""
                                }
                            },
                            enabled = messageInput.isNotBlank() && !chatState.sending,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (messageInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                                .testTag("chat_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = Localization.t("send", lang),
                                tint = if (messageInput.isNotBlank()) Color.White else MaterialTheme.extendedColors.muted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Character counter footer
                    if (messageInput.length > 100) {
                        Text(
                            text = "${messageInput.length} / 2000",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.extendedColors.muted,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 24.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Privacy & Security Dialog
    if (showPrivacyInfoDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (lang == AppLanguage.EN) "Chat Privacy Standards" else "Datenschutz-Standards")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "1. Bilateral Erasure: If either party unmatches or deletes their account, this conversation is permanently destroyed under GDPR Article 17."
                        else
                            "1. Beidseitige Löschung: Löst eine Partei das Match auf, wird der Chatverlauf für beide Seiten unwiderruflich gemäß Art. 17 DSGVO gelöscht.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "2. Zero Commercial Profiling: Your private conversations are never scanned for advertisements or shared with third parties."
                        else
                            "2. Keine Werbenutzung: Ihre privaten Nachrichten werden niemals für Werbeprofile analysiert oder weitergegeben.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "3. Anti-Scam Protection: For your financial safety, automated client-side checks warn against wire transfer fraud and off-platform exploitation."
                        else
                            "3. Betrugsschutz: Zu Ihrer Sicherheit warnen clientseitige Schutzmechanismen vor verdächtigen Geldüberweisungen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Unmatch Confirmation Dialog
    if (showUnmatchConfirm) {
        AlertDialog(
            onDismissRequest = { showUnmatchConfirm = false },
            title = { Text(Localization.t("unmatch", lang)) },
            text = {
                Text(
                    if (lang == AppLanguage.EN)
                        "Are you sure you want to unmatch? The conversation will be permanently removed for both of you and cannot be recovered."
                    else
                        "Möchten Sie das Match wirklich auflösen? Der Chatverlauf wird für beide Personen unwiderruflich gelöscht."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnmatchConfirm = false
                        repository.unmatch(match.id)
                        onBack()
                    }
                ) {
                    Text(Localization.t("unmatch", lang), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnmatchConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Block Confirmation Dialog
    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = { Text(Localization.t("block", lang)) },
            text = {
                Text(
                    if (lang == AppLanguage.EN)
                        "Blocking this person will remove them from your matches, daily discovery, and chat forever."
                    else
                        "Durch das Blockieren wird diese Person dauerhaft aus Ihren Matches, täglichen Vorschlägen und Chats entfernt."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockConfirm = false
                        repository.blockUser(match.matchedUser.id)
                        onBack()
                    }
                ) {
                    Text(Localization.t("block", lang), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report one message (long-press on the other person's message)
    reportedMessage?.let { message ->
        ReportProfileDialog(
            userToReport = match.matchedUser,
            repository = repository,
            messageId = message.id,
            onDismiss = { reportedMessage = null }
        )
    }
}

/** Words and patterns that trigger the safety hint (money requests, contact details, links). */
object ChatSafety {
    private val words = listOf(
        "money", "geld", "bank", "iban", "crypto", "bitcoin", "telegram", "whatsapp", "paypal",
        "western union", "wire", "überweisung", "gift card", "gutschein", "http", "www.", "t.me", ".com/"
    )

    fun looksRisky(text: String): Boolean {
        val lower = text.lowercase()
        return words.any { it in lower } || lower.contains("+49") || Regex("\\d[\\d\\s/-]{7,}\\d").containsMatchIn(lower)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    onLongPress: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(onClick = {}, onLongClick = onLongPress, enabled = onLongPress != null)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.extendedColors.muted
                    )
                    if (isMe) {
                        // One tick: sent. Two ticks: the other person opened the chat.
                        Icon(
                            imageVector = if (message.isRead) Icons.Default.DoneAll else Icons.Default.Check,
                            contentDescription = if (message.isRead) "Read" else "Sent",
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (message.isRead) 0.85f else 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
