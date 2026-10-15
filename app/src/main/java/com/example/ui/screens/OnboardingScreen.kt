package com.example.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.model.GermanCities
import com.example.model.ProfilePrompt
import com.example.model.availablePromptsList
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.MarrySecondaryButton
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val user by repository.currentUser.collectAsState()

    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 10

    // Step state holders
    var displayName by remember { mutableStateOf(user.displayName.ifBlank { "Lukas" }) }
    var gender by remember { mutableStateOf(user.gender) }
    var lookingFor by remember { mutableStateOf(user.lookingFor) }
    var selectedCity by remember { mutableStateOf(user.city) }
    var selectedRegion by remember { mutableStateOf(user.region) }
    var marriageTimeline by remember { mutableStateOf(user.marriageTimeline) }
    var wantsChildren by remember { mutableStateOf(user.wantsChildren) }
    var selectedLanguages by remember { mutableStateOf(user.languages.toSet().ifEmpty { setOf("German", "English") }) }
    var occupation by remember { mutableStateOf(user.occupation.ifBlank { "Software Engineer" }) }
    var education by remember { mutableStateOf(user.education.ifBlank { "Master Degree" }) }
    var heightCm by remember { mutableIntStateOf(user.heightCm) }
    var smoking by remember { mutableStateOf(user.smoking) }
    var religion by remember { mutableStateOf(user.religion.ifBlank { "Christian" }) }
    var religionConsent by remember { mutableStateOf(true) }

    // Photos state (min 3)
    val defaultSamplePhotos = remember {
        listOf(
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=800&q=80"
        )
    }
    var photosList by remember { mutableStateOf(user.photos.ifEmpty { defaultSamplePhotos }) }

    // Prompts (3 prompts)
    var prompt1Key by remember { mutableStateOf("ideal_sunday") }
    var prompt1Answer by remember { mutableStateOf("A fresh morning walk through Englischer Garten, brewing espresso together, and cooking a leisurely homemade dinner.") }

    var prompt2Key by remember { mutableStateOf("family_means") }
    var prompt2Answer by remember { mutableStateOf("A safe harbor of unconditional love, laughter across generations, and growing together through every chapter.") }

    var prompt3Key by remember { mutableStateOf("non_negotiable") }
    var prompt3Answer by remember { mutableStateOf("Integrity and deep mutual respect. In a marriage, communication is everything.") }

    var bioText by remember { mutableStateOf(user.bio.ifBlank { "Engineer based in Munich. Looking for a partner who values mutual respect, honesty, and wants to build a warm, supportive family together." }) }

    var stepError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(Localization.t("onboarding_title", lang), style = MaterialTheme.typography.titleMedium)
                        Text("Step $currentStep of $totalSteps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                    }
                },
                navigationIcon = {
                    if (currentStep > 1) {
                        IconButton(onClick = { currentStep--; stepError = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous step")
                        }
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { currentStep / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                when (currentStep) {
                    1 -> {
                        // Step 1: First name & birth date (age shown)
                        Text(Localization.t("step_name_age", lang), style = MaterialTheme.typography.titleLarge)
                        Text(Localization.t("your_age", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted)

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(Localization.t("first_name", lang)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎂", fontSize = 20.sp)
                                Text(
                                    text = if (lang == AppLanguage.EN) "Calculated Age: ${user.age} years old" else "Berechnetes Alter: ${user.age} Jahre",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    2 -> {
                        // Step 2: Gender & Looking For
                        Text(Localization.t("step_gender", lang), style = MaterialTheme.typography.titleLarge)

                        Text(Localization.t("gender", lang), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip(label = Localization.t("man", lang), selected = gender == "man") { gender = "man" }
                            ChoiceChip(label = Localization.t("woman", lang), selected = gender == "woman") { gender = "woman" }
                            ChoiceChip(label = Localization.t("other", lang), selected = gender == "other") { gender = "other" }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(Localization.t("looking_for", lang), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip(label = Localization.t("woman", lang), selected = lookingFor == "woman") { lookingFor = "woman" }
                            ChoiceChip(label = Localization.t("man", lang), selected = lookingFor == "man") { lookingFor = "man" }
                            ChoiceChip(label = Localization.t("any", lang), selected = lookingFor == "any") { lookingFor = "any" }
                        }
                    }

                    3 -> {
                        // Step 3: German City
                        Text(Localization.t("step_city", lang), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (lang == AppLanguage.EN) "Select the German city you live in. We match you with nearby marriage-minded singles."
                            else "Wählen Sie Ihre Stadt in Deutschland.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            GermanCities.list.take(10).forEach { city ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selectedCity == city.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCity = city.name
                                            selectedRegion = city.state
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(city.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                            Text(city.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                                        }
                                        if (selectedCity == city.name) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        // Step 4: Marriage Timeline
                        Text(Localization.t("step_timeline", lang), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (lang == AppLanguage.EN) "Clear intentions are the heartbeat of MarryMatch." else "Klare Absichten sind das Herzstück von MarryMatch.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted
                        )

                        listOf(
                            "within_1y" to Localization.t("timeline_within_1y", lang),
                            "1_2y" to Localization.t("timeline_1_2y", lang),
                            "2_5y" to Localization.t("timeline_2_5y", lang),
                            "open" to Localization.t("timeline_open", lang)
                        ).forEach { (key, label) ->
                            SelectableOptionCard(
                                title = label,
                                subtitle = if (key == "within_1y" || key == "1_2y") "Ready for commitment & building a family" else "Getting to know someone deeply first",
                                selected = marriageTimeline == key,
                                onClick = { marriageTimeline = key }
                            )
                        }
                    }

                    5 -> {
                        // Step 5: Family & Children + Languages
                        Text(Localization.t("step_family", lang), style = MaterialTheme.typography.titleLarge)

                        Text(Localization.t("wants_children", lang), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip(label = Localization.t("children_yes", lang), selected = wantsChildren == "yes") { wantsChildren = "yes" }
                            ChoiceChip(label = Localization.t("children_no", lang), selected = wantsChildren == "no") { wantsChildren = "no" }
                            ChoiceChip(label = Localization.t("children_open", lang), selected = wantsChildren == "open") { wantsChildren = "open" }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(Localization.t("languages_spoken", lang), style = MaterialTheme.typography.labelLarge)
                        listOf("German", "English", "Turkish", "French", "Arabic", "Italian", "Spanish", "Russian").chunked(3).forEach { rowLangs ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowLangs.forEach { l ->
                                    val isSel = selectedLanguages.contains(l)
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            selectedLanguages = if (isSel) selectedLanguages - l else selectedLanguages + l
                                        },
                                        label = { Text(l) }
                                    )
                                }
                            }
                        }
                    }

                    6 -> {
                        // Step 6: Lifestyle (Occupation, Education, Height, Smoking)
                        Text(Localization.t("step_lifestyle", lang), style = MaterialTheme.typography.titleLarge)

                        OutlinedTextField(
                            value = occupation,
                            onValueChange = { occupation = it },
                            label = { Text(Localization.t("occupation", lang)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_occupation_input"),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = education,
                            onValueChange = { education = it },
                            label = { Text(Localization.t("education", lang)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = heightCm.toString(),
                            onValueChange = { it.toIntOrNull()?.let { h -> heightCm = h } },
                            label = { Text(Localization.t("height", lang)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Text(Localization.t("smoking", lang), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceChip(label = "Non-smoker", selected = smoking == "Non-smoker") { smoking = "Non-smoker" }
                            ChoiceChip(label = "Occasionally", selected = smoking == "Occasionally") { smoking = "Occasionally" }
                            ChoiceChip(label = "Smoker", selected = smoking == "Smoker") { smoking = "Smoker" }
                        }
                    }

                    7 -> {
                        // Step 7: Optional Religion & Values (GDPR Art 9)
                        Text(Localization.t("step_religion", lang), style = MaterialTheme.typography.titleLarge)

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = Localization.t("sensitive_data_notice", lang),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { religionConsent = !religionConsent }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = religionConsent,
                                onCheckedChange = { religionConsent = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = Localization.t("sensitive_consent_box", lang),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (religionConsent) {
                            OutlinedTextField(
                                value = religion,
                                onValueChange = { religion = it },
                                label = { Text(if (lang == AppLanguage.EN) "Religion or Worldview (Optional)" else "Religion oder Weltanschauung (Freiwillig)") },
                                placeholder = { Text("e.g. Christian, Muslim, Jewish, Spiritual, Secular") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    8 -> {
                        // Step 8: Photos (Min 3, Max 6)
                        Text(Localization.t("step_photos", lang), style = MaterialTheme.typography.titleLarge)
                        Text(Localization.t("step_photos_desc", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted)

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(photosList) { url ->
                                Box(
                                    modifier = Modifier
                                        .size(130.dp, 170.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Profile photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = {
                                            if (photosList.size > 3) {
                                                photosList = photosList - url
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (photosList.size >= 3) "✓ ${photosList.size} photos ready" else "⚠️ Need at least 3 photos (currently ${photosList.size})",
                            color = if (photosList.size >= 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    9 -> {
                        // Step 9: 3 Prompts
                        Text(Localization.t("step_prompts", lang), style = MaterialTheme.typography.titleLarge)
                        Text(Localization.t("choose_prompts_desc", lang), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted)

                        // Prompt 1
                        Text("Prompt 1: My ideal Sunday is...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = prompt1Answer,
                            onValueChange = { prompt1Answer = it },
                            placeholder = { Text("Write your thoughtful answer...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Prompt 2
                        Text("Prompt 2: What family means to me...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = prompt2Answer,
                            onValueChange = { prompt2Answer = it },
                            placeholder = { Text("Write your thoughtful answer...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Prompt 3
                        Text("Prompt 3: A value I will not compromise on...", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = prompt3Answer,
                            onValueChange = { prompt3Answer = it },
                            placeholder = { Text("Write your thoughtful answer...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    10 -> {
                        // Step 10: Bio
                        Text(Localization.t("step_bio", lang), style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (lang == AppLanguage.EN) "Tell prospective matches about your character, interests, and heart for marriage."
                            else "Erzählen Sie Interessenten von Ihrem Charakter und Ihren Werten.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted
                        )

                        OutlinedTextField(
                            value = bioText,
                            onValueChange = { if (it.length <= 500) bioText = it },
                            label = { Text(Localization.t("step_bio", lang)) },
                            placeholder = { Text(Localization.t("bio_placeholder", lang)) },
                            minLines = 4,
                            maxLines = 6,
                            supportingText = {
                                Text("${bioText.length} / 500 characters", modifier = Modifier.fillMaxWidth())
                            },
                            modifier = Modifier.fillMaxWidth().testTag("onboarding_bio_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (stepError != null) {
                    Text(stepError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Button
            MarryPrimaryButton(
                text = if (currentStep < totalSteps) {
                    if (lang == AppLanguage.EN) "Continue" else "Weiter"
                } else {
                    Localization.t("complete_profile", lang)
                },
                onClick = {
                    if (currentStep < totalSteps) {
                        if (currentStep == 1 && displayName.isBlank()) {
                            stepError = if (lang == AppLanguage.EN) "Please enter your first name." else "Bitte geben Sie Ihren Vornamen ein."
                            return@MarryPrimaryButton
                        }
                        if (currentStep == 6 && occupation.isBlank()) {
                            stepError = if (lang == AppLanguage.EN) "Occupation is required." else "Berufsangabe ist erforderlich."
                            return@MarryPrimaryButton
                        }
                        if (currentStep == 8 && photosList.size < 3) {
                            stepError = if (lang == AppLanguage.EN) "Please provide at least 3 photos." else "Bitte laden Sie mindestens 3 Fotos hoch."
                            return@MarryPrimaryButton
                        }
                        stepError = null
                        currentStep++
                    } else {
                        // Finalize onboarding
                        val compiledPrompts = listOf(
                            ProfilePrompt("ideal_sunday", "My ideal Sunday is...", "Mein idealer Sonntag ist...", prompt1Answer),
                            ProfilePrompt("family_means", "What family means to me...", "Was Familie für mich bedeutet...", prompt2Answer),
                            ProfilePrompt("non_negotiable", "A value I will not compromise on...", "Ein Wert, bei dem ich keine Kompromisse eingehe...", prompt3Answer)
                        )
                        repository.updateCurrentUser {
                            it.copy(
                                displayName = displayName,
                                gender = gender,
                                lookingFor = lookingFor,
                                city = selectedCity,
                                region = selectedRegion,
                                marriageTimeline = marriageTimeline,
                                wantsChildren = wantsChildren,
                                languages = selectedLanguages.toList(),
                                occupation = occupation,
                                education = education,
                                heightCm = heightCm,
                                smoking = smoking,
                                religion = if (religionConsent) religion else "",
                                photos = photosList,
                                prompts = compiledPrompts,
                                bio = bioText,
                                onboardingDone = true
                            )
                        }
                        repository.setAuthState(AuthState.LOGGED_IN)
                    }
                },
                testTag = "onboarding_continue_btn"
            )
        }
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@Composable
fun SelectableOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
