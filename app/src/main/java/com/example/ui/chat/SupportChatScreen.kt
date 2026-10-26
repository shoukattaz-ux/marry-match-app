package com.example.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.media.ChatMediaPrep
import com.example.core.media.Prepared
import com.example.data.ChatTime
import com.example.data.SupportApi
import com.example.data.SupportMessage
import com.example.model.PhotoFocus
import com.example.ui.components.AdminBadge
import com.example.ui.components.FocusedPhoto
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.SupportChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Who and what the conversation screen shows; the same screen serves the member and admins. */
data class SupportChatTarget(
    val threadId: String,
    /** The member of this conversation. */
    val memberId: String,
    /** The other side: the admin (for the member) or the member (for admins). */
    val name: String,
    val photoUrl: String? = null,
    val photoFocus: PhotoFocus = PhotoFocus(),
    /** True on the admin side; the member side shows the Admin badge and a safety note. */
    val adminSide: Boolean,
    /** Admin side: the member's e-mail, shown under the name. */
    val email: String = "",
    /** False for an admin reading another admin's conversation. */
    val canWrite: Boolean = true,
    /** Admin side, read along: whose conversation this is. */
    val adminName: String = "",
    val open: Boolean = true,
    val muted: Boolean = false
)

/**
 * An admin conversation (admin messages): text and photos, live. The member sees the admin's own
 * name, photo and platinum Admin badge; admins see the member's name and e-mail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatScreen(
    target: SupportChatTarget,
    myUserId: String,
    api: SupportApi?,
    lang: AppLanguage,
    onBack: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val vm: SupportChatViewModel = viewModel(key = "support_${target.threadId}") {
        SupportChatViewModel(target.threadId, target.memberId, myUserId, api, target.canWrite, target.open, target.muted)
    }
    val state by vm.state.collectAsState()
    // On screen: a push for this conversation reloads it instead of showing a notification.
    com.example.ui.components.TrackOpenConversation(target.threadId, onReload = vm::refresh)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable(target.threadId) { mutableStateOf("") }
    var preparingPhoto by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    BackHandler(onBack = onBack)

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparingPhoto = true
        scope.launch {
            val prepared = ChatMediaPrep.prepareImage(context, uri, File(context.cacheDir, "support_photo"))
            val bytes = (prepared as? Prepared.Ready)?.file?.let { f -> withContext(Dispatchers.IO) { f.readBytes() } }
            preparingPhoto = false
            if (bytes == null) {
                vm.clearError()
                android.widget.Toast.makeText(
                    context, t((prepared as? Prepared.Refused)?.reasonKey ?: "media_prepare_failed"), android.widget.Toast.LENGTH_LONG
                ).show()
            } else if (vm.send(text, bytes)) {
                text = ""
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (target.photoUrl != null) {
                                FocusedPhoto(target.photoUrl, target.photoFocus, target.name, Modifier.fillMaxSize())
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    target.name.ifBlank { t("admin_no_profile") },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!target.adminSide) AdminBadge()
                            }
                            Text(
                                if (target.adminSide) target.email else t("support_team_line"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    if (!target.adminSide) {
                        IconButton(onClick = { vm.setMuted(!state.muted) }, modifier = Modifier.testTag("support_mute")) {
                            Icon(
                                if (state.muted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                                contentDescription = t(if (state.muted) "support_unmute" else "support_mute")
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().testTag("support_chat_screen")) {
            // What this conversation is, before the first message.
            Notice(
                when {
                    !target.adminSide -> String.format(t("support_member_notice"), target.name)
                    !target.canWrite -> String.format(t("support_read_only"), target.adminName)
                    else -> t("support_admin_notice")
                }
            )
            if (state.muted && !target.adminSide) Notice(t("support_muted_notice"))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.loadFailed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t("support_load_failed"), color = MaterialTheme.extendedColors.muted)
                        TextButton(onClick = vm::load) { Text(t("admin_reload")) }
                    }
                    state.messages.isEmpty() -> Text(
                        t(if (target.adminSide) "support_empty_admin" else "support_empty"),
                        color = MaterialTheme.extendedColors.muted,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.messages, key = { it.id }) { m ->
                            // On the admin side, "mine" means written by the conversation's admin.
                            val mine = if (target.adminSide) m.senderId != target.memberId else m.senderId == myUserId
                            SupportBubble(m, mine, api, onOpenPhoto = { viewing = it })
                        }
                    }
                }
            }

            state.errorKey?.let { key ->
                Text(
                    t(key),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when {
                !state.open -> Notice(t("support_closed"))
                target.canWrite -> {
                    if (preparingPhoto || state.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !preparingPhoto && !state.sending,
                            modifier = Modifier.testTag("support_photo")
                        ) { Icon(Icons.Default.PhotoLibrary, contentDescription = t("support_add_photo")) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { if (it.length <= SupportChatViewModel.MAX_LENGTH) text = it },
                            placeholder = { Text(t(if (target.adminSide) "support_hint_admin" else "support_hint")) },
                            maxLines = 5,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.weight(1f).testTag("support_input")
                        )
                        IconButton(
                            onClick = { if (vm.send(text)) text = "" },
                            enabled = text.isNotBlank() && !state.sending && !preparingPhoto,
                            modifier = Modifier.testTag("support_send")
                        ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = t("support_send"), tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }

    viewing?.let { url ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(url, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                IconButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = t("close"), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SupportBubble(message: SupportMessage, mine: Boolean, api: SupportApi?, onOpenPhoto: (String) -> Unit) {
    val container = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    var photoUrl by remember(message.photoPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(message.photoPath) {
        val path = message.photoPath ?: return@LaunchedEffect
        photoUrl = runCatching { api?.photoUrl(path) }.getOrNull()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, if (mine) 4.dp else 18.dp, if (mine) 18.dp else 4.dp),
            color = container,
            modifier = Modifier.widthIn(max = 300.dp).testTag("support_bubble")
        ) {
            Column(Modifier.padding(if (message.photoPath != null) 4.dp else 0.dp)) {
                if (message.photoPath != null) {
                    Box(
                        Modifier.size(220.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.1f))
                            .clickable(enabled = photoUrl != null) { photoUrl?.let(onOpenPhoto) }
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    message.body?.let { Text(it, color = content, style = MaterialTheme.typography.bodyMedium) }
                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ChatTime.format(message.createdAt), color = content.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        if (mine) {
                            Icon(
                                if (message.readAt != null) Icons.Default.DoneAll else Icons.Default.Done,
                                contentDescription = null,
                                tint = content.copy(alpha = 0.8f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A conversation in a list: photo, name (with Admin badge for the member), last message, time, unread. */
@Composable
fun SupportThreadRow(
    name: String,
    photoUrl: String?,
    photoFocus: PhotoFocus,
    preview: String,
    time: String,
    unread: Int,
    showAdminBadge: Boolean,
    label: String?,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (showAdminBadge) 1.5.dp else 1.dp,
            if (showAdminBadge) Color(0xFFA9B0BC) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("support_thread_row")
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (photoUrl != null) FocusedPhoto(photoUrl, photoFocus, name, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (showAdminBadge) AdminBadge()
                }
                label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.muted)
                if (unread > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$unread") }
                }
            }
        }
    }
}

/** The last message of a conversation for a list: "You: …", or "📷 Photo". */
fun supportPreview(thread: com.example.data.SupportThread, lang: AppLanguage): String {
    val text = thread.lastMessage?.takeIf { it.isNotBlank() }
        ?: if (thread.lastHasPhoto) Localization.t("support_photo_preview", lang) else Localization.t("support_no_messages", lang)
    return if (thread.lastFromMe) String.format(Localization.t("support_you_prefix", lang), text) else text
}
