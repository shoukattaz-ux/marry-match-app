package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.ProfileStrength
import com.example.model.MatchTraits
import kotlinx.coroutines.launch
import com.example.ui.components.FocusedPhoto
import com.example.data.MarryMatchRepository
import com.example.data.SelfieStatus
import com.example.data.asSeenByOthers
import com.example.ui.components.*
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    repository: MarryMatchRepository,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSelfieVerification: () -> Unit = {},
    onOpenProfileCreation: () -> Unit = {},
    /** Opens the profile editor at a given step (e.g. the habits / interests / values steps). */
    onOpenProfileEditorAt: (Int) -> Unit = { onOpenProfileCreation() },
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val user by repository.currentUser.collectAsState()
    val selfieStatus by repository.selfieStatus.collectAsState()

    LaunchedEffect(user.id) { if (user.id.isNotBlank()) repository.loadSelfieStatus() }

    var showPhoneFlow by remember { mutableStateOf(false) }
    // Which photo is being adjusted (0 = main photo), or null.
    var adjustIndex by remember { mutableStateOf<Int?>(null) }
    var adjustSaving by remember { mutableStateOf(false) }
    var adjustError by remember { mutableStateOf<String?>(null) }
    val adjustScope = rememberCoroutineScope()
    var showPreview by remember { mutableStateOf(false) }
    val privacy by repository.privacy.collectAsState()
    LaunchedEffect(showPreview) { if (showPreview) repository.loadPrivacy() }

    adjustIndex?.takeIf { it in user.photos.indices }?.let { index ->
        PhotoAdjustDialog(
            photo = user.photos[index],
            initial = user.focusOf(index),
            lang = lang,
            isMain = index == 0,
            caption = "${user.displayName}, ${user.age}",
            saving = adjustSaving,
            errorText = adjustError,
            onDismiss = { adjustIndex = null; adjustError = null },
            onSave = { focus ->
                adjustSaving = true
                adjustError = null
                adjustScope.launch {
                    repository.savePhotoFocus(index, focus)
                        .onSuccess { adjustIndex = null }
                        .onFailure { adjustError = Localization.t("avatar_save_failed", lang) }
                    adjustSaving = false
                }
            },
            onChangePhotos = { adjustIndex = null; onOpenProfileCreation() }
        )
    }

    if (showPreview) {
        // Your own card exactly as members see it on Discover; nothing on it can be tapped.
        Dialog(onDismissRequest = { showPreview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showPreview = false }, modifier = Modifier.testTag("profile_preview_close")) {
                            Icon(Icons.Default.Close, contentDescription = Localization.t("close", lang))
                        }
                        Column {
                            Text(Localization.t("profile_preview_title", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(Localization.t("profile_preview_hint", lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                        }
                    }
                    Box(modifier = Modifier.weight(1f).testTag("profile_preview")) {
                        ProfileHingeCard(
                            profile = user.asSeenByOthers(privacy),
                            lang = lang,
                            enabled = false,
                            preview = true,
                            onPass = {},
                            onLike = {},
                            onLikeTarget = {},
                            onReport = {}
                        )
                    }
                }
            }
        }
    }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lang == AppLanguage.EN) "My Profile" else "Mein Profil", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (user.isAdmin) {
                        FilledTonalButton(
                            onClick = onOpenAdmin,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text("Admin", fontSize = 12.sp)
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("profile_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Tap the photo to adjust how it sits in the circle (or to add photos).
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clickable {
                                if (user.photos.isNotEmpty()) adjustIndex = 0 else onOpenProfileCreation()
                            }
                            .testTag("profile_avatar")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            FocusedPhoto(
                                model = user.photos.firstOrNull() ?: "",
                                focus = user.focusOf(0),
                                contentDescription = user.displayName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = Localization.t("avatar_adjust_title", lang),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${user.displayName}, ${user.age}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        VerifiedBadge(
                            selfieVerified = user.selfieVerified,
                            phoneVerified = user.phoneVerified
                        )
                    }

                    Text(
                        text = "${user.city}, ${user.region}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.muted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    MarriageTimelineChip(timeline = user.marriageTimeline, isGerman = lang == AppLanguage.DE)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onOpenProfileCreation,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (lang == AppLanguage.EN) "Edit Profile & Bio" else "Profil & Bio bearbeiten")
                    }
                }
            }

            ProfileStrengthCard(
                strength = ProfileStrength.of(user),
                lang = lang,
                onOpenEditor = onOpenProfileEditorAt,
                onOpenSelfie = onOpenSelfieVerification
            )

            // Phase 4: Selfie Verification Card (Blue Tick)
            if (!user.selfieVerified) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.extendedColors.verified.copy(alpha = 0.1f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.extendedColors.verified.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = MaterialTheme.extendedColors.verified)
                            Text(
                                text = Localization.t("selfie_verification", lang),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        val pending = selfieStatus.state == SelfieStatus.PENDING
                        val rejected = selfieStatus.state == SelfieStatus.REJECTED
                        Text(
                            text = when {
                                pending -> Localization.t("verification_pending", lang)
                                rejected -> Localization.t("selfie_last_rejected", lang) + " " +
                                    Localization.t("selfie_reason_${selfieStatus.rejectReason ?: "other"}", lang)
                                else -> Localization.t("selfie_card_body", lang)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rejected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!pending) {
                            Button(
                                onClick = onOpenSelfieVerification,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.extendedColors.verified),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("get_verified_btn")
                            ) {
                                Text(
                                    Localization.t(if (rejected) "selfie_try_again" else "get_verified", lang),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                // Verified Status Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Column {
                            Text(Localization.t("verification_approved", lang), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                Localization.t("selfie_verified_card", lang),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Phase 5: Phone Verification Button (hidden behind PHONE_VERIFICATION_ENABLED = false)
            if (AppConfig.PHONE_VERIFICATION_ENABLED && !user.phoneVerified) {
                OutlinedCard(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(Localization.t("phone_verification", lang), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("+49 German Mobile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                            }
                        }
                        TextButton(onClick = { showPhoneFlow = true }) {
                            Text(if (lang == AppLanguage.EN) "Verify" else "Prüfen")
                        }
                    }
                }
            }

            // Prompts Section Preview
            Text(
                text = if (lang == AppLanguage.EN) "My Personality Prompts" else "Meine Fragen & Antworten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            user.prompts.forEach { p ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (lang == AppLanguage.DE) p.questionDe else p.questionEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = p.answer,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Interests Section
            if (user.interests.isNotEmpty()) {
                Text(
                    text = Localization.t("interests_title", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(user.interests) { interest ->
                        DetailChip(label = Localization.t("interest_$interest", lang))
                    }
                }
            }

            // Habits & values section
            val answered = MatchTraits.ALL.filter { it.key in user.traits }
            if (answered.isNotEmpty()) {
                Text(
                    text = Localization.t("habits_values_title", lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    answered.forEach { trait ->
                        DetailChip(
                            label = "${Localization.t(trait.labelKey, lang)}: ${Localization.t(trait.optionKey(user.traits.getValue(trait.key)), lang)}"
                        )
                    }
                }
            }

            // Photos Gallery
            Text(
                text = if (lang == AppLanguage.EN) "Profile Photos" else "Profilfotos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                Localization.t("profile_photos_hint", lang),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.muted
            )

            // Shaped and framed like on a Discover card; tap one to move or zoom it.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(user.photos) { index, photoUrl ->
                    Box(
                        modifier = Modifier
                            .height(150.dp)
                            .aspectRatio(if (index == 0) CardPhotoFrame.MAIN_ASPECT else CardPhotoFrame.EXTRA_ASPECT)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { adjustIndex = index }
                            .testTag("profile_photo_$index")
                    ) {
                        FocusedPhoto(
                            model = photoUrl,
                            focus = user.focusOf(index),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Crop, contentDescription = Localization.t("photo_adjust_title", lang), tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                        if (index == 0) {
                            Text(
                                Localization.t("main_photo", lang),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            if (user.photos.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showPreview = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("profile_preview_btn")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Localization.t("profile_preview_button", lang))
                }
            }
        }
    }

    // Phone OTP Verification Flow (guarded behind PHONE_VERIFICATION_ENABLED)
    if (AppConfig.PHONE_VERIFICATION_ENABLED && showPhoneFlow) {
        var phoneNumber by remember { mutableStateOf("") }
        var otpCode by remember { mutableStateOf("") }
        var codeSent by remember { mutableStateOf(false) }
        var phoneError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showPhoneFlow = false },
            title = { Text(Localization.t("phone_verification", lang)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (lang == AppLanguage.EN) "Only German numbers (+49) are accepted for community safety."
                        else "Aus Sicherheitsgründen werden nur deutsche Nummern (+49) akzeptiert.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text(Localization.t("phone_number", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (codeSent) {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            label = { Text(Localization.t("enter_otp", lang)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    if (phoneError != null) {
                        Text(phoneError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (!codeSent) {
                    Button(
                        onClick = {
                            if (!phoneNumber.startsWith("+49")) {
                                phoneError = "Must be a valid German mobile number (+49)"
                            } else {
                                phoneError = null
                                codeSent = true
                            }
                        }
                    ) {
                        Text(if (lang == AppLanguage.EN) "Send SMS Code" else "SMS senden")
                    }
                } else {
                    Button(
                        onClick = {
                            // Real SMS verification provider required when PHONE_VERIFICATION_ENABLED is turned on
                            if (otpCode.length == 6) {
                                repository.updateCurrentUser { it.copy(phoneVerified = true) }
                                showPhoneFlow = false
                            } else {
                                phoneError = if (lang == AppLanguage.EN) "Please enter a valid 6-digit verification code." else "Bitte geben Sie einen gültigen 6-stelligen Code ein."
                            }
                        }
                    ) {
                        Text(Localization.t("verify_code", lang))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneFlow = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        var editBio by remember { mutableStateOf(user.bio) }
        var editOccupation by remember { mutableStateOf(user.occupation) }
        var editTimeline by remember { mutableStateOf(user.marriageTimeline) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text(if (lang == AppLanguage.EN) "Edit Profile" else "Profil bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editOccupation,
                        onValueChange = { editOccupation = it },
                        label = { Text(Localization.t("occupation", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(Localization.t("step_timeline", lang), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChoiceChip(label = "Within 1y", selected = editTimeline == "within_1y") { editTimeline = "within_1y" }
                        ChoiceChip(label = "1-2y", selected = editTimeline == "1_2y") { editTimeline = "1_2y" }
                        ChoiceChip(label = "2-5y", selected = editTimeline == "2_5y") { editTimeline = "2_5y" }
                    }

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { if (it.length <= 500) editBio = it },
                        label = { Text(Localization.t("step_bio", lang)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.updateCurrentUser {
                            it.copy(
                                bio = editBio,
                                occupation = editOccupation,
                                marriageTimeline = editTimeline
                            )
                        }
                        showEditProfileDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** Profile strength meter with the two most useful next steps; tapping a step opens the right screen. */
@Composable
private fun ProfileStrengthCard(
    strength: ProfileStrength,
    lang: AppLanguage,
    onOpenEditor: (step: Int) -> Unit,
    onOpenSelfie: () -> Unit
) {
    val t: (String) -> String = { Localization.t(it, lang) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("profile_strength_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("strength_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "${strength.percent}% · ${t(strength.levelKey)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("profile_strength_percent")
                )
            }
            LinearProgressIndicator(
                progress = { strength.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            if (strength.tips.isEmpty()) {
                Text(t("strength_all_done"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            } else {
                Text(t("strength_hint"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                strength.tips.take(2).forEach { tip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { if (tip.opensSelfie) onOpenSelfie() else onOpenEditor(tip.editorStep) }
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t(tip.key), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("+${tip.points}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
