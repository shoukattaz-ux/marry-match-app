package com.example.ui.screens

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
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
import com.example.core.AppConfig
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.core.ProfileStrength
import com.example.data.MarryMatchRepository
import com.example.data.SelfieStatus
import com.example.ui.components.*
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: MarryMatchRepository,
    onOpenSettings: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenSelfieVerification: () -> Unit = {},
    onOpenProfileCreation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val user by repository.currentUser.collectAsState()
    val selfieStatus by repository.selfieStatus.collectAsState()

    LaunchedEffect(user.id) { if (user.id.isNotBlank()) repository.loadSelfieStatus() }

    var showPhoneFlow by remember { mutableStateOf(false) }
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
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        AsyncImage(
                            model = user.photos.firstOrNull() ?: "",
                            contentDescription = user.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
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
                onOpenEditor = onOpenProfileCreation,
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
                    text = if (lang == AppLanguage.EN) "Interests & Passions" else "Interessen & Hobbys",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(user.interests) { interest ->
                        DetailChip(label = interest)
                    }
                }
            }

            // Photos Gallery
            Text(
                text = if (lang == AppLanguage.EN) "Profile Photos" else "Profilfotos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(user.photos) { photoUrl ->
                    Box(
                        modifier = Modifier
                            .size(110.dp, 150.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
    onOpenEditor: () -> Unit,
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
                            .clickable { if (tip.opensSelfie) onOpenSelfie() else onOpenEditor() }
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
