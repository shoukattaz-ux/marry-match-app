package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.data.MarryMatchRepository
import com.example.model.UserProfile
import com.example.ui.components.*
import com.example.ui.theme.extendedColors

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.viewmodel.MatchingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    repository: MarryMatchRepository,
    onOpenReport: (UserProfile) -> Unit,
    matchingViewModel: MatchingViewModel = viewModel { MatchingViewModel(repository) },
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val matchingState by matchingViewModel.uiState.collectAsState()
    val profiles = matchingState.curatedProfiles
    val currentIndex = matchingState.currentIndex
    val verifiedOnly = matchingState.filters.verifiedOnly
    val selectedRegion = matchingState.filters.selectedRegion
    val isCurating = matchingState.curationProgress.isCurating

    var showFilterSheet by remember { mutableStateOf(false) }
    var activeCommentPrompt by remember { mutableStateOf<String?>(null) }
    var commentText by remember { mutableStateOf("") }

    val remainingCount = (profiles.size - currentIndex).coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = Localization.t("app_name", lang),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$remainingCount ${Localization.t("daily_counter", lang)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag("discover_filters_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (verifiedOnly || selectedRegion != "All" || matchingState.filters.selectedInterests.isNotEmpty()) {
                                    Badge { Text("!") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filters")
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
        ) {
            if (isCurating) {
                // Curation Progress Animation Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (lang == AppLanguage.EN) "Daily Curation in Progress..." else "Tägliche Kuration läuft...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = matchingState.curationProgress.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (currentIndex < profiles.size) {
                val candidate = profiles[currentIndex]
                val matchScore = matchingViewModel.calculateCurationScore(candidate, repository.currentUser.value, matchingState.filters)
                val sharedInterests = matchingViewModel.getSharedInterests(candidate)

                ProfileHingeCard(
                    profile = candidate,
                    matchScore = matchScore,
                    sharedInterests = sharedInterests,
                    isGerman = lang == AppLanguage.DE,
                    onPass = { matchingViewModel.passCurrentProfile() },
                    onLike = { matchingViewModel.likeCurrentProfile() },
                    onLikeWithPrompt = { promptTitle ->
                        activeCommentPrompt = promptTitle
                        commentText = ""
                    },
                    onReport = { onOpenReport(candidate) }
                )
            } else {
                // Done for today state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✨", fontSize = 38.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = Localization.t("done_today_title", lang),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = Localization.t("done_today_desc", lang),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.muted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedButton(
                        onClick = {
                            matchingViewModel.simulateDailyCuration(animated = true)
                        }
                    ) {
                        Text(if (lang == AppLanguage.EN) "Curate New Daily Set (Reset)" else "Tagesliste neu laden")
                    }
                }
            }
        }
    }

    // Comment Modal Sheet when user hearts a specific prompt
    if (activeCommentPrompt != null && currentIndex < profiles.size) {
        val candidate = profiles[currentIndex]
        AlertDialog(
            onDismissRequest = { activeCommentPrompt = null },
            title = {
                Text(
                    text = if (lang == AppLanguage.EN) "Like with Comment" else "Mit Kommentar liken",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "\"$activeCommentPrompt\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 200) commentText = it },
                        placeholder = {
                            Text(if (lang == AppLanguage.EN) "Add a thoughtful comment (optional)..." else "Fügen Sie einen respektvollen Kommentar hinzu...")
                        },
                        supportingText = { Text("${commentText.length}/200") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.likeCurrentProfile(candidate, commentText)
                        activeCommentPrompt = null
                    }
                ) {
                    Text(if (lang == AppLanguage.EN) "Send Like" else "Like senden")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeCommentPrompt = null }) {
                    Text(if (lang == AppLanguage.EN) "Cancel" else "Abbrechen")
                }
            }
        )
    }

    // Filters Dialog
    if (showFilterSheet) {
        AlertDialog(
            onDismissRequest = { showFilterSheet = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (lang == AppLanguage.EN) "Daily Curation Filters" else "Tägliche Kurations-Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { matchingViewModel.resetFilters() }) {
                        Text(if (lang == AppLanguage.EN) "Reset" else "Zurücksetzen", fontSize = 12.sp)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Age Range Selection
                    Text(
                        text = if (lang == AppLanguage.EN) "Age Range: ${matchingState.filters.minAge} – ${matchingState.filters.maxAge} years" else "Altersbereich: ${matchingState.filters.minAge} – ${matchingState.filters.maxAge} Jahre",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair(20, 29),
                            Pair(24, 34),
                            Pair(28, 38),
                            Pair(30, 45)
                        ).forEach { (min, max) ->
                            val isSelected = matchingState.filters.minAge == min && matchingState.filters.maxAge == max
                            FilterChip(
                                selected = isSelected,
                                onClick = { matchingViewModel.updateAgeRange(min, max) },
                                label = { Text("$min-$max", fontSize = 12.sp) }
                            )
                        }
                    }

                    HorizontalDivider()

                    // 2. Germany Location Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (lang == AppLanguage.EN) "🇩🇪 Strictly Germany Only" else "🇩🇪 Nur Deutschland",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (lang == AppLanguage.EN) "Matches must be verified residents in Germany" else "Matches müssen verifizierte Einwohner in Deutschland sein",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.muted
                            )
                        }
                        Switch(
                            checked = matchingState.filters.germanyOnly,
                            onCheckedChange = { matchingViewModel.setGermanyOnly(it) }
                        )
                    }

                    Text(
                        text = if (lang == AppLanguage.EN) "Preferred Bundesland (State)" else "Bevorzugtes Bundesland",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf("All", "Bayern", "Berlin", "Hessen", "Hamburg", "Nordrhein-Westfalen", "Baden-Württemberg").chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { r ->
                                FilterChip(
                                    selected = selectedRegion == r,
                                    onClick = { matchingViewModel.setSelectedRegion(r) },
                                    label = { Text(r, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // 3. Shared Interests Multi-select
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (lang == AppLanguage.EN) "Interests & Lifestyle (${matchingState.filters.selectedInterests.size})" else "Interessen & Lebensstil (${matchingState.filters.selectedInterests.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (matchingState.filters.selectedInterests.isNotEmpty()) {
                            TextButton(onClick = { matchingViewModel.clearInterests() }) {
                                Text(if (lang == AppLanguage.EN) "Clear" else "Leeren", fontSize = 11.sp)
                            }
                        }
                    }

                    matchingState.allAvailableInterests.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { interest ->
                                val isSelected = interest in matchingState.filters.selectedInterests
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { matchingViewModel.toggleInterest(interest) },
                                    label = { Text(interest, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // 4. Verified Only Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                Localization.t("verified_only_filter", lang),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (lang == AppLanguage.EN) "Selfie verified profiles only" else "Nur selfie-verifizierte Profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.muted
                            )
                        }
                        Switch(
                            checked = verifiedOnly,
                            onCheckedChange = { matchingViewModel.setVerifiedOnly(it) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFilterSheet = false
                        matchingViewModel.simulateDailyCuration(animated = true)
                    },
                    modifier = Modifier.testTag("apply_curation_filters_btn")
                ) {
                    Text(if (lang == AppLanguage.EN) "Curate Daily Batch" else "Tagesliste kuratieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFilterSheet = false }) {
                    Text(if (lang == AppLanguage.EN) "Close" else "Schließen")
                }
            }
        )
    }
}

@Composable
fun ProfileHingeCard(
    profile: UserProfile,
    matchScore: Int = 85,
    sharedInterests: List<String> = emptyList(),
    isGerman: Boolean,
    onPass: () -> Unit,
    onLike: () -> Unit,
    onLikeWithPrompt: (String) -> Unit,
    onReport: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 90.dp) // padding for bottom action floating bar
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Primary Hero Photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = profile.photos.firstOrNull() ?: "",
                    contentDescription = profile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Compatibility Badge Overlay Top Right
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("✨", fontSize = 12.sp)
                        Text(
                            text = "$matchScore% ${if (isGerman) "Übereinstimmung" else "Compatibility"}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Gradient scrim on bottom of photo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 220f
                            )
                        )
                )

                // Name, Age, City and Verified Badges overlaid on photo bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${profile.displayName}, ${profile.age}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        VerifiedBadge(
                            selfieVerified = profile.selfieVerified,
                            phoneVerified = profile.phoneVerified
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = "${profile.city}, ${profile.region} (Deutschland)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.9f))
                        )
                    }
                }
            }

            // Shared Interests Highlights if any
            if (sharedInterests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🤝", fontSize = 14.sp)
                        Text(
                            text = "${if (isGerman) "Gemeinsame Interessen" else "Shared Interests"}: ${sharedInterests.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Marriage Timeline Pill (Prominent Hinge-style indicator)
            MarriageTimelineChip(timeline = profile.marriageTimeline)

            Spacer(modifier = Modifier.height(14.dp))

            // Prompt 1 Card
            if (profile.prompts.isNotEmpty()) {
                val p1 = profile.prompts[0]
                PromptCard(
                    prompt = p1,
                    isGerman = isGerman,
                    onLikeClick = { onLikeWithPrompt(if (isGerman) p1.questionDe else p1.questionEn) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details and Lifestyle Grid
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isGerman) "Überblick & Lebensstil" else "Details & Lifestyle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.extendedColors.muted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailChip(label = "💼 ${profile.occupation}")
                        DetailChip(label = "👶 ${if (profile.wantsChildren == "yes") "Wants children" else "Open"}")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailChip(label = "📏 ${profile.heightCm} cm")
                        DetailChip(label = "🚭 ${profile.smoking}")
                    }

                    if (profile.religion.isNotBlank()) {
                        DetailChip(label = "✨ ${profile.religion}")
                    }

                    DetailChip(label = "🗣️ ${profile.languages.joinToString(", ")}")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Photo
            if (profile.photos.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = profile.photos[1],
                        contentDescription = "Photo 2",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Prompt 2 Card
            if (profile.prompts.size > 1) {
                val p2 = profile.prompts[1]
                PromptCard(
                    prompt = p2,
                    isGerman = isGerman,
                    onLikeClick = { onLikeWithPrompt(if (isGerman) p2.questionDe else p2.questionEn) }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Bio Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isGerman) "Über mich" else "About Me",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.extendedColors.muted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Prompt 3 Card
            if (profile.prompts.size > 2) {
                val p3 = profile.prompts[2]
                PromptCard(
                    prompt = p3,
                    isGerman = isGerman,
                    onLikeClick = { onLikeWithPrompt(if (isGerman) p3.questionDe else p3.questionEn) }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Tertiary Photo
            if (profile.photos.size > 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = profile.photos[2],
                        contentDescription = "Photo 3",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Floating Bottom Actions: Pass (X), Like (Heart), and Report (Flag)
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Pass button
                IconButton(
                    onClick = onPass,
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("discover_pass_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Pass profile",
                        tint = MaterialTheme.extendedColors.muted,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Like button
                IconButton(
                    onClick = onLike,
                    modifier = Modifier
                        .size(62.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("discover_like_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Like profile",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Report button
                IconButton(
                    onClick = onReport,
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .testTag("discover_report_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Report profile",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
