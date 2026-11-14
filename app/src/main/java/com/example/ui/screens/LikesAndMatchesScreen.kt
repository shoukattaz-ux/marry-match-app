package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.model.MatchItem
import com.example.model.UserProfile
import com.example.ui.components.ActivityLabel
import com.example.ui.components.FocusedPhoto
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.LikesYouViewModel
import com.example.ui.viewmodel.LikesYouText
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/**
 * "Likes You" tab (free, migration 045): everyone who liked you, newest first, with what they
 * liked and their comment. Tap a card for the full profile; Like back makes the match at once
 * (it does not use up the free likes), Pass removes them. Opening the tab clears its badge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesYouScreen(
    repository: MarryMatchRepository,
    onOpenDiscover: () -> Unit,
    onOpenReport: (UserProfile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val me by repository.currentUser.collectAsState()
    val newLikes by repository.newLikesCount.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val vm: LikesYouViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "likes_you") {
        LikesYouViewModel(
            api = repository.discovery,
            onMatched = { repository.onNewMatch(it) },
            onChanged = { repository.refreshMatches() }
        )
    }
    val state by vm.state.collectAsState()
    var commentFor by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(Unit) {
        repository.loadMatches()
        repository.onLikesViewed()
    }
    // A like that arrives while this tab is on screen: show it and count it as seen.
    LaunchedEffect(newLikes) {
        if (newLikes > 0) {
            vm.load(quiet = true)
            repository.onLikesViewed()
        }
    }

    state.open?.let { like ->
        androidx.activity.compose.BackHandler(onBack = vm::close)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(like.profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = vm::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back")) } }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                LikedWhatBanner(like, me, lang, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Box(Modifier.weight(1f)) {
                    ProfileHingeCard(
                        profile = like.profile,
                        lang = lang,
                        enabled = state.workingOn == null,
                        myInterests = me.interests,
                        onPass = { vm.pass(like.profile.id) },
                        onLike = { vm.likeBack(like.profile.id) },
                        onLikeTarget = { commentFor = like.profile },
                        onReport = { onOpenReport(like.profile) }
                    )
                }
            }
        }
    } ?: Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.extendedColors.accent)
                        Text(t("likes_you"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("likes_you_screen")
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.loadFailed -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("likes_you_load_failed"), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { vm.load() }) { Text(t("retry")) }
                }
                state.likes.isEmpty() -> LikesYouEmpty(lang, onOpenDiscover)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("likes_you_list")
                ) {
                    item {
                        Text(
                            LikesYouText.count(state.likes.size, lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(t("likes_you_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                    }
                    items(state.likes.chunked(2), key = { row -> row.first().profile.id }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { like ->
                                LikerCard(
                                    like = like,
                                    me = me,
                                    lang = lang,
                                    working = state.workingOn == like.profile.id,
                                    enabled = state.workingOn == null,
                                    onOpen = { vm.open(like.profile.id) },
                                    onLikeBack = { vm.likeBack(like.profile.id) },
                                    onPass = { vm.pass(like.profile.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            state.errorKey?.let { key ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = vm::clearError) { Text(t("close")) } }
                ) { Text(t(key)) }
            }
        }
    }

    // Like back with a message (the heart on a photo or answer in the full profile).
    commentFor?.let { person ->
        var comment by remember(person.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { commentFor = null },
            title = { Text(t("likes_you_like_back_title")) },
            text = {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= LikesYouViewModel.MAX_COMMENT) comment = it },
                    placeholder = { Text(t("comment_optional")) },
                    supportingText = { Text("${comment.length}/${LikesYouViewModel.MAX_COMMENT}") },
                    modifier = Modifier.fillMaxWidth().testTag("like_back_comment")
                )
            },
            confirmButton = {
                Button(onClick = { vm.likeBack(person.id, comment); commentFor = null }) { Text(t("likes_you_like_back")) }
            },
            dismissButton = { TextButton(onClick = { commentFor = null }) { Text(t("cancel")) } }
        )
    }
}

@Composable
private fun LikedWhatBanner(like: com.example.data.ReceivedLike, me: UserProfile, lang: AppLanguage, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.extendedColors.accent.copy(alpha = 0.15f), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.extendedColors.accent, modifier = Modifier.size(16.dp))
                Text(LikesYouText.likedWhat(like, lang), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            like.comment?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun LikerCard(
    like: com.example.data.ReceivedLike,
    me: UserProfile,
    lang: AppLanguage,
    working: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onLikeBack: () -> Unit,
    onPass: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = like.profile
    val t: (String) -> String = { Localization.t(it, lang) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onOpen).testTag("liker_card")
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            FocusedPhoto(model = p.photos.firstOrNull(), focus = p.focusOf(0), contentDescription = p.displayName, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 200f)))
            if (like.isNew) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.extendedColors.accent, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text(t("likes_you_new"), color = Color.Black, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${p.displayName}, ${p.age}", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    VerifiedBadge(selfieVerified = p.selfieVerified, phoneVerified = false, isAdmin = p.isAdmin)
                }
                if (p.city.isNotBlank()) Text(p.city, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(LikesYouText.likedWhat(like, lang), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.extendedColors.accent,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            like.comment?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onPass, enabled = enabled, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("liker_pass")
            ) { Icon(Icons.Default.Close, contentDescription = t("pass"), modifier = Modifier.size(20.dp)) }
            Button(
                onClick = onLikeBack, enabled = enabled, contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(40.dp).testTag("liker_like_back")
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Default.Favorite, contentDescription = t("likes_you_like_back"), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LikesYouEmpty(lang: AppLanguage, onOpenDiscover: () -> Unit) {
    val t: (String) -> String = { Localization.t(it, lang) }
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("likes_you_empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.extendedColors.accent.copy(alpha = 0.2f), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.extendedColors.accent, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(t("likes_you_none"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(t("likes_you_empty_body"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenDiscover,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.accent),
            shape = RoundedCornerShape(12.dp)
        ) { Text(t("likes_you_browse"), color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

/** "Chats" tab: your matches and conversations, newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    repository: MarryMatchRepository,
    onOpenChat: (MatchItem) -> Unit,
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSupport: (com.example.data.SupportThreadItem) -> Unit = {}
) {
    val lang by repository.appLanguage.collectAsState()
    val matches by repository.matches.collectAsState()
    val supportThreads by repository.supportThreads.collectAsState()
    val unreadChats = matches.count { it.unread }

    // Fresh list every time the tab opens.
    LaunchedEffect(Unit) { repository.loadMatches() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Localization.t("chats_title", lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (matches.isNotEmpty()) {
                            Text(
                                if (unreadChats > 0) String.format(Localization.t("chats_unread", lang), unreadChats)
                                else String.format(Localization.t("chats_count", lang), matches.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.muted
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("chats_screen")
        ) {
            if (matches.isEmpty() && supportThreads.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Message, contentDescription = null, tint = MaterialTheme.extendedColors.muted, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = Localization.t("no_matches_yet", lang),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.extendedColors.muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onOpenDiscover) { Text(Localization.t("go_to_discover", lang)) }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Messages from admins come first, with the admin's own profile and badge.
                    items(supportThreads, key = { "support_" + it.thread.threadId }) { item ->
                        val th = item.thread
                        com.example.ui.chat.SupportThreadRow(
                            name = th.name,
                            photoUrl = item.photoUrl,
                            photoFocus = th.photoFocus ?: com.example.model.PhotoFocus(),
                            preview = com.example.ui.chat.supportPreview(th, lang),
                            time = item.time,
                            unread = th.unread,
                            showAdminBadge = true,
                            label = Localization.t("support_list_label", lang),
                            onClick = { onOpenSupport(item) }
                        )
                    }
                    items(matches, key = { it.id }) { match ->
                        MatchRow(
                            match = match,
                            newMatchText = Localization.t("new_match_say_hello", lang),
                            lang = lang,
                            onClick = { onOpenChat(match) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MatchRow(
    match: MatchItem,
    newMatchText: String,
    lang: AppLanguage,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            ) {
                FocusedPhoto(
                    model = match.matchedUser.photos.firstOrNull() ?: "",
                    focus = match.matchedUser.mainPhotoFocus,
                    contentDescription = match.matchedUser.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${match.matchedUser.displayName}, ${match.matchedUser.age}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    VerifiedBadge(
                        selfieVerified = match.matchedUser.selfieVerified,
                        phoneVerified = match.matchedUser.phoneVerified,
                        isAdmin = match.matchedUser.isAdmin
                    )
                }
                if (match.matchedUser.activity.isNotBlank()) {
                    ActivityLabel(match.matchedUser.activity, lang)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = match.lastMessage.ifBlank { newMatchText },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (match.unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.muted,
                    fontWeight = if (match.unread) FontWeight.SemiBold else FontWeight.Normal
                )
                if (match.iMet && match.theyMet) {
                    Text(
                        text = Localization.t("we_met_badge", lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                match.expiresInDays?.let { days ->
                    // Like Hinge/Bumble: a silent match disappears after 7 days.
                    Text(
                        text = if (days <= 1) Localization.t("match_expires_today", lang)
                               else String.format(Localization.t("match_expires_in", lang), days),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (days <= 2) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.muted
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = match.lastMessageTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.muted
                )
                if (match.unread) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun MatchCelebrationDialog(
    matchedUser: UserProfile,
    onSendMessage: () -> Unit,
    onKeepBrowsing: () -> Unit,
    lang: AppLanguage
) {
    Dialog(onDismissRequest = onKeepBrowsing) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (lang == AppLanguage.EN) "💍 It's a Match!" else "💍 Ein gegenseitiges Match!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (lang == AppLanguage.EN)
                        "You and ${matchedUser.displayName} both liked each other! Start a thoughtful conversation."
                    else
                        "Sie und ${matchedUser.displayName} haben sich gegenseitig geliked! Starten Sie ein respektvolles Gespräch.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.extendedColors.muted
                )

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    AsyncImage(
                        model = matchedUser.photos.firstOrNull() ?: "",
                        contentDescription = matchedUser.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                MarryPrimaryButton(
                    text = if (lang == AppLanguage.EN) "Send a Message" else "Nachricht senden",
                    onClick = onSendMessage
                )

                TextButton(onClick = onKeepBrowsing) {
                    Text(if (lang == AppLanguage.EN) "Keep Browsing" else "Weitersuchen")
                }
            }
        }
    }
}
