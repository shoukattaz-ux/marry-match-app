package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.CityLocator
import com.example.core.Localization
import com.example.data.DiscoveryPrefs
import com.example.data.MarryMatchRepository
import com.example.model.UserProfile
import com.example.ui.components.*
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.LikeTarget
import com.example.ui.viewmodel.MatchingViewModel
import com.example.ui.viewmodel.OnboardingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    repository: MarryMatchRepository,
    onOpenReport: (UserProfile) -> Unit,
    matchingViewModel: MatchingViewModel = viewModel { MatchingViewModel(repository) },
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val state by matchingViewModel.uiState.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }

    var showFilters by remember { mutableStateOf(false) }
    var commentTarget by remember { mutableStateOf<LikeTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = t("app_name"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (!state.loading && state.total > 0) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    text = String.format(t("discover_counter"), state.visible.size, state.total),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).testTag("discover_counter"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }, modifier = Modifier.testTag("discover_filters_btn")) {
                        BadgedBox(badge = { if (!state.prefs.isDefault) Badge() }) {
                            Icon(Icons.Default.FilterList, contentDescription = t("filters_title"))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            val current = state.current
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.loadFailed -> CenteredMessage(
                    title = t("discover_load_failed"),
                    body = null,
                    buttonText = t("retry"),
                    onButton = matchingViewModel::load
                )

                current != null -> ProfileHingeCard(
                    profile = current,
                    lang = lang,
                    enabled = !state.sending,
                    onPass = matchingViewModel::pass,
                    onLike = { matchingViewModel.like() },
                    onLikeTarget = { commentTarget = it },
                    onReport = { onOpenReport(current) }
                )

                else -> CenteredMessage(
                    title = t("done_today_title"),
                    body = if (state.total < 10) t("discover_small_set") else t("done_today_desc"),
                    buttonText = if (!state.prefs.isDefault) t("filters_title") else null,
                    onButton = { showFilters = true }
                )
            }

            state.errorKey?.let { key ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(t(key)) }
            }
        }
    }

    // Comment box when the user taps the heart on one photo or prompt
    commentTarget?.let { target ->
        var comment by remember(target) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { commentTarget = null },
            title = { Text(t("like_with_comment"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    target.label?.let {
                        Text("\"$it\"", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedTextField(
                        value = comment,
                        onValueChange = { if (it.length <= 200) comment = it },
                        placeholder = { Text(t("comment_optional")) },
                        supportingText = { Text("${comment.length}/200") },
                        modifier = Modifier.fillMaxWidth().testTag("like_comment_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        matchingViewModel.like(target, comment)
                        commentTarget = null
                    },
                    modifier = Modifier.testTag("send_like_btn")
                ) { Text(t("send_like")) }
            },
            dismissButton = { TextButton(onClick = { commentTarget = null }) { Text(t("cancel")) } }
        )
    }

    if (showFilters) {
        FiltersDialog(
            initial = state.prefs,
            lang = lang,
            onApply = {
                matchingViewModel.applyPrefs(it)
                showFilters = false
            },
            onDismiss = { showFilters = false }
        )
    }
}

@Composable
private fun CenteredMessage(title: String, body: String?, buttonText: String?, onButton: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(80.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("✨", fontSize = 38.sp) }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(10.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted, textAlign = TextAlign.Center)
        }
        if (buttonText != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onButton) { Text(buttonText) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersDialog(
    initial: DiscoveryPrefs,
    lang: AppLanguage,
    onApply: (DiscoveryPrefs) -> Unit,
    onDismiss: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    var prefs by remember { mutableStateOf(initial) }
    val ageRanges = listOf(18 to 99, 20 to 29, 25 to 35, 30 to 40, 35 to 45, 40 to 55)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("filters_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { prefs = DiscoveryPrefs() }) { Text(t("filters_reset"), fontSize = 12.sp) }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("filters_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)

                Text(String.format(t("filters_age"), prefs.minAge, prefs.maxAge), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ageRanges.forEach { (min, max) ->
                        FilterChip(
                            selected = prefs.minAge == min && prefs.maxAge == max,
                            onClick = { prefs = prefs.copy(minAge = min, maxAge = max) },
                            label = { Text(if (min == 18 && max == 99) t("filters_any") else "$min–$max", fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider()
                Text(t("filters_region"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = prefs.region == null,
                        onClick = { prefs = prefs.copy(region = null) },
                        label = { Text(t("filters_all_germany"), fontSize = 12.sp) }
                    )
                    CityLocator.BUNDESLAENDER.forEach { region ->
                        FilterChip(
                            selected = prefs.region == region,
                            onClick = { prefs = prefs.copy(region = region) },
                            label = { Text(region, fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider()
                Text(t("step_timeline"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("within_1y", "1_2y", "2_5y", "open").forEach { key ->
                        val selected = key in prefs.timelines
                        FilterChip(
                            selected = selected,
                            onClick = { prefs = prefs.copy(timelines = if (selected) prefs.timelines - key else prefs.timelines + key) },
                            label = { Text(t("timeline_$key"), fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider()
                Text(t("languages_spoken"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OnboardingViewModel.LANGUAGES.forEach { language ->
                        val selected = language in prefs.languages
                        FilterChip(
                            selected = selected,
                            onClick = { prefs = prefs.copy(languages = if (selected) prefs.languages - language else prefs.languages + language) },
                            label = { Text(t("lang_$language"), fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("verified_only_filter"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = prefs.verifiedOnly, onCheckedChange = { prefs = prefs.copy(verifiedOnly = it) })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(prefs) }, modifier = Modifier.testTag("apply_filters_btn")) { Text(t("filters_apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("cancel")) } }
    )
}

/**
 * One profile, Hinge style: photos and prompts in turn, each with a heart to like that exact
 * item (with an optional comment). Like, Pass and Report stay at the bottom.
 */
@Composable
fun ProfileHingeCard(
    profile: UserProfile,
    lang: AppLanguage,
    enabled: Boolean,
    onPass: () -> Unit,
    onLike: () -> Unit,
    onLikeTarget: (LikeTarget) -> Unit,
    onReport: () -> Unit
) {
    val isGerman = lang == AppLanguage.DE
    val t: (String) -> String = { Localization.t(it, lang) }

    fun photoTarget(index: Int) = LikeTarget("photo", profile.photoIds.getOrNull(index), null)
    fun promptTarget(index: Int) = profile.prompts[index].let {
        LikeTarget("prompt", it.id.ifBlank { null }, if (isGerman) it.questionDe else it.questionEn)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 90.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            // Main photo with name, age, city and badges
            Box(modifier = Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(16.dp))) {
                AsyncImage(
                    model = profile.photos.firstOrNull(),
                    contentDescription = profile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)), startY = 220f)
                    )
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${profile.displayName}, ${profile.age}",
                            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                        VerifiedBadge(selfieVerified = profile.selfieVerified, phoneVerified = profile.phoneVerified)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = listOf(profile.city, profile.region).filter { it.isNotBlank() }.distinct().joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.9f))
                        )
                    }
                    if (profile.activity.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        ActivityLabel(profile.activity, lang, onDark = true)
                    }
                }
                if (profile.photos.isNotEmpty()) {
                    PhotoHeart(enabled, Modifier.align(Alignment.TopEnd)) { onLikeTarget(photoTarget(0)) }
                }
                if (profile.photosBlurred) {
                    BlurredPhotosBadge(lang, Modifier.align(Alignment.TopStart))
                }
            }

            Spacer(Modifier.height(14.dp))
            if (profile.marriageTimeline.isNotBlank()) {
                MarriageTimelineChip(timeline = profile.marriageTimeline, isGerman = isGerman)
                Spacer(Modifier.height(14.dp))
            }

            if (profile.prompts.isNotEmpty()) {
                PromptCard(prompt = profile.prompts[0], isGerman = isGerman, onLikeClick = { onLikeTarget(promptTarget(0)) })
                Spacer(Modifier.height(12.dp))
            }

            DetailsCard(profile, t)
            Spacer(Modifier.height(14.dp))

            if (profile.photos.size > 1) {
                ExtraPhoto(profile.photos[1], enabled) { onLikeTarget(photoTarget(1)) }
            }
            if (profile.prompts.size > 1) {
                PromptCard(prompt = profile.prompts[1], isGerman = isGerman, onLikeClick = { onLikeTarget(promptTarget(1)) })
                Spacer(Modifier.height(14.dp))
            }

            if (profile.bio.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(t("about_me"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.extendedColors.muted)
                        Spacer(Modifier.height(8.dp))
                        Text(profile.bio, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (profile.prompts.size > 2) {
                PromptCard(prompt = profile.prompts[2], isGerman = isGerman, onLikeClick = { onLikeTarget(promptTarget(2)) })
                Spacer(Modifier.height(14.dp))
            }

            profile.photos.drop(2).forEachIndexed { i, url ->
                ExtraPhoto(url, enabled) { onLikeTarget(photoTarget(i + 2)) }
            }

            Spacer(Modifier.height(30.dp))
        }

        // Pass, Like and Report
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                IconButton(
                    onClick = onPass,
                    enabled = enabled,
                    modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).testTag("discover_pass_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = t("pass"), tint = MaterialTheme.extendedColors.muted, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = onLike,
                    enabled = enabled,
                    modifier = Modifier.size(62.dp).background(MaterialTheme.colorScheme.primary, CircleShape).testTag("discover_like_btn")
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = t("like"), tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(
                    onClick = onReport,
                    modifier = Modifier.size(52.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).testTag("discover_report_btn")
                ) {
                    Icon(Icons.Default.Flag, contentDescription = t("report"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoHeart(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(12.dp)
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
            .testTag("photo_heart_button")
    ) {
        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/** Tells the viewer why the photos are blurred and when they become clear. */
@Composable
private fun BlurredPhotosBadge(lang: AppLanguage, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("blurred_photos_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(Localization.t("photos_blurred_badge", lang), style = MaterialTheme.typography.labelMedium.copy(color = Color.White))
    }
}

@Composable
private fun ExtraPhoto(url: String, enabled: Boolean, onHeart: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(16.dp))) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        PhotoHeart(enabled, Modifier.align(Alignment.TopEnd), onHeart)
    }
    Spacer(Modifier.height(14.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsCard(profile: UserProfile, t: (String) -> String) {
    val chips = buildList {
        if (profile.occupation.isNotBlank()) add("💼 ${profile.occupation}")
        if (profile.education.isNotBlank()) add("🎓 ${profile.education}")
        if (profile.wantsChildren in listOf("yes", "no", "open")) add("👶 ${t("wants_children")}: ${t("children_${profile.wantsChildren}")}")
        if (profile.heightCm > 0) add("📏 ${profile.heightCm} cm")
        if (profile.smoking in OnboardingViewModel.SMOKING) add("🚭 ${t("smoking_${profile.smoking}")}")
        if (profile.religion.isNotBlank()) add("✨ ${profile.religion}")
        if (profile.languages.isNotEmpty()) add("🗣️ ${profile.languages.joinToString(", ") { t("lang_$it") }}")
    }
    if (chips.isEmpty()) return
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chips.forEach { DetailChip(label = it) }
        }
    }
}
