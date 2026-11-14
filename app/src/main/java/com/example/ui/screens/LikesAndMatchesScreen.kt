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
                else -> Column(Modifier.fillMaxSize()) {
                    // Filter (All / With a message) and Sort by (newest / online first)
                    var sortMenu by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !state.withMessageOnly,
                            onClick = { vm.setWithMessageOnly(false) },
                            label = { Text(t("likes_you_filter_all")) },
                            modifier = Modifier.testTag("likes_filter_all")
                        )
                        FilterChip(
                            selected = state.withMessageOnly,
                            onClick = { vm.setWithMessageOnly(true) },
                            label = { Text(t("likes_you_filter_message")) },
                            modifier = Modifier.testTag("likes_filter_message")
                        )
                        Spacer(Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("likes_sort")) {
                                Text(t("likes_you_sort"), color = LikePink, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(t("likes_you_sort_newest")) },
                                    leadingIcon = { RadioButton(selected = !state.onlineFirst, onClick = null) },
                                    onClick = { vm.setOnlineFirst(false); sortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text(t("likes_you_sort_online")) },
                                    leadingIcon = { RadioButton(selected = state.onlineFirst, onClick = null) },
                                    onClick = { vm.setOnlineFirst(true); sortMenu = false }
                                )
                            }
                        }
                    }
                    Text(
                        t("likes_you_swipe_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    val shown = state.shown
                    if (shown.isEmpty()) {
                        Text(
                            t("likes_you_none_with_message"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        )
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxSize().testTag("likes_you_list")
                    ) {
                        items(shown, key = { it.profile.id }) { like ->
                            SwipeableLikerRow(
                                like = like,
                                lang = lang,
                                working = state.workingOn == like.profile.id,
                                enabled = state.workingOn == null,
                                onOpen = { vm.open(like.profile.id) },
                                onLikeBack = { vm.likeBack(like.profile.id) },
                                onPass = { vm.pass(like.profile.id) }
                            )
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

/** The pink of "Liked your photo" and "Sort by". */
private val LikePink = Color(0xFFE5487A)

/**
 * One person who liked you: round photo (or silhouette) with the online dot, name and age, city,
 * what they liked in pink and their message. Tap: full profile. Swipe right: like back (match);
 * swipe left: pass (they are not told). The ✕ and ♥ buttons do the same for people who do not swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableLikerRow(
    like: com.example.data.ReceivedLike,
    lang: AppLanguage,
    working: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onLikeBack: () -> Unit,
    onPass: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (enabled) {
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> onLikeBack()
                    SwipeToDismissBoxValue.EndToStart -> onPass()
                    SwipeToDismissBoxValue.Settled -> Unit
                }
            }
            false  // the row leaves the list when the server answered; until then it springs back
        }
    )
    SwipeToDismissBox(
        state = dismiss,
        backgroundContent = {
            val toRight = dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val toLeft = dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier
                    .fillMaxSize()
                    .background(
                        when {
                            toRight -> LikePink.copy(alpha = 0.85f)
                            toLeft -> MaterialTheme.colorScheme.surfaceVariant
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End
            ) {
                if (toRight) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text(t("likes_you_like_back"), color = Color.White, fontWeight = FontWeight.Bold)
                } else if (toLeft) {
                    Text(t("pass"), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }
        },
        modifier = Modifier.testTag("liker_row")
    ) {
        LikerRow(like, lang, working, enabled, onOpen, onLikeBack, onPass)
    }
}

@Composable
private fun LikerRow(
    like: com.example.data.ReceivedLike,
    lang: AppLanguage,
    working: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onLikeBack: () -> Unit,
    onPass: () -> Unit
) {
    val p = like.profile
    val t: (String) -> String = { Localization.t(it, lang) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Round photo (or silhouette) with the online dot
        Box(Modifier.size(78.dp)) {
            Box(Modifier.fillMaxSize().clip(CircleShape)) {
                if (p.silhouette || p.photos.isEmpty()) com.example.ui.components.SilhouettePhoto(Modifier.fillMaxSize())
                else FocusedPhoto(model = p.photos.firstOrNull(), focus = p.focusOf(0), contentDescription = p.displayName, modifier = Modifier.fillMaxSize())
            }
            com.example.ui.components.activityStyle(p.activity)?.let { (color, _) ->
                if (p.activity == "online" || p.activity == "recent" || p.activity == "today") {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .padding(3.dp)
                            .background(color, CircleShape)
                            .testTag("liker_online_dot")
                    )
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${p.displayName} • ${p.age}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                VerifiedBadge(selfieVerified = p.selfieVerified, phoneVerified = false, isAdmin = p.isAdmin)
                if (like.isNew) Box(Modifier.size(8.dp).background(LikePink, CircleShape).testTag("liker_new_dot"))
            }
            val place = listOf(p.city, p.region).filter { it.isNotBlank() }.distinct().joinToString(", ")
            if (place.isNotBlank()) {
                Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                LikesYouText.likedWhat(like, lang),
                style = MaterialTheme.typography.bodyMedium,
                color = LikePink,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            like.comment?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        // Buttons for people who do not swipe
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledIconButton(
                onClick = onLikeBack,
                enabled = enabled,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = LikePink, contentColor = Color.White),
                modifier = Modifier.size(40.dp).testTag("liker_like_back")
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Default.Favorite, contentDescription = t("likes_you_like_back"), modifier = Modifier.size(20.dp))
            }
            OutlinedIconButton(onClick = onPass, enabled = enabled, modifier = Modifier.size(40.dp).testTag("liker_pass")) {
                Icon(Icons.Default.Close, contentDescription = t("pass"), modifier = Modifier.size(20.dp))
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
    onOpenSupport: (com.example.data.SupportThreadItem) -> Unit = {},
    onOpenReport: (UserProfile) -> Unit = {}
) {
    val lang by repository.appLanguage.collectAsState()
    val matches by repository.matches.collectAsState()
    // Tapping a match's photo opens their full profile (the row itself opens the chat).
    var profileOf by remember { mutableStateOf<UserProfile?>(null) }
    profileOf?.let { person ->
        MatchProfileDialog(
            repository = repository,
            person = person,
            onDismiss = { profileOf = null },
            onReport = { profileOf = null; onOpenReport(person) }
        )
    }
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
                            onClick = { onOpenChat(match) },
                            onOpenProfile = { profileOf = match.matchedUser }
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
    onClick: () -> Unit,
    onOpenProfile: (() -> Unit)? = null
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
                    .then(if (onOpenProfile != null) Modifier.clickable(onClick = onOpenProfile).testTag("match_row_photo") else Modifier)
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
