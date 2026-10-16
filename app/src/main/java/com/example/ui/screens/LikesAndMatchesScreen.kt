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
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.VerifiedBadge
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikesAndMatchesScreen(
    repository: MarryMatchRepository,
    onOpenChat: (MatchItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val matches by repository.matches.collectAsState()
    val likesCount by repository.receivedLikesCount.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Matches, 1: Likes You

    // Fresh matches and "likes you" count every time the tab opens
    LaunchedEffect(Unit) { repository.loadMatches() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(Localization.t("matches", lang))
                                    if (matches.isNotEmpty()) {
                                        Badge { Text(matches.size.toString()) }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(Localization.t("likes_you", lang)) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedTab == 0) {
                // Matches List
                if (matches.isEmpty()) {
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
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(matches) { match ->
                            MatchRow(
                                match = match,
                                newMatchText = Localization.t("new_match_say_hello", lang),
                                lang = lang,
                                onClick = { onOpenChat(match) }
                            )
                        }
                    }
                }
            } else {
                // Likes You Blurred Teaser
                LikesYouBlurredView(lang = lang, likesCount = likesCount)
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
                AsyncImage(
                    model = match.matchedUser.photos.firstOrNull() ?: "",
                    contentDescription = match.matchedUser.displayName,
                    contentScale = ContentScale.Crop,
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
                        phoneVerified = match.matchedUser.phoneVerified
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
fun LikesYouBlurredView(lang: AppLanguage, likesCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Blurred simulated grid of admirers
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )
                }
            }
        }

        // Foreground callout
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.extendedColors.accent.copy(alpha = 0.2f),
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.extendedColors.accent, modifier = Modifier.size(32.dp))
                    }
                }

                Text(
                    text = if (likesCount > 0) String.format(Localization.t("likes_you_count", lang), likesCount)
                    else Localization.t("likes_you_none", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (lang == AppLanguage.EN)
                        "Free users can connect when both people like each other during daily discovery. A future premium plan will reveal incoming likes directly."
                    else
                        "Kostenlose Nutzer matchen sofort, wenn beide sich in den täglichen Profilen liken. Ein zukünftiges Premium-Abo schaltet eingehende Likes frei.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.muted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { /* Preview future feature */ },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (lang == AppLanguage.EN) "Keep Browsing Daily Profiles" else "Tägliche Profile weiter ansehen",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
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
