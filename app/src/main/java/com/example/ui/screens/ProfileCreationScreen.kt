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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.model.GermanCities
import com.example.model.ProfilePrompt
import com.example.model.availablePromptsList
import com.example.ui.components.DetailChip
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileCreationScreen(
    repository: MarryMatchRepository,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val user by repository.currentUser.collectAsState()

    var displayName by remember { mutableStateOf(user.displayName) }
    var occupation by remember { mutableStateOf(user.occupation) }
    var education by remember { mutableStateOf(user.education) }
    var selectedCity by remember { mutableStateOf(user.city.ifBlank { "Berlin" }) }
    var selectedRegion by remember { mutableStateOf(user.region.ifBlank { "Berlin" }) }
    var marriageTimeline by remember { mutableStateOf(user.marriageTimeline.ifBlank { "1_2y" }) }
    var wantsChildren by remember { mutableStateOf(user.wantsChildren.ifBlank { "yes" }) }
    var heightCm by remember { mutableIntStateOf(if (user.heightCm > 0) user.heightCm else 175) }
    var smoking by remember { mutableStateOf(user.smoking.ifBlank { "Non-smoker" }) }
    var religion by remember { mutableStateOf(user.religion) }
    var religionConsent by remember { mutableStateOf(false) }

    var bioText by remember {
        mutableStateOf(user.bio)
    }

    // Selected Interests & Hobbies
    val availableInterests = remember {
        listOf(
            "🏔️ Alps Hiking", "🍳 Cooking & Baking", "☕ Coffee Culture", "📚 Reading",
            "🌍 Travel & Roadtrips", "👨‍👩‍👧 Family Traditions", "🚴 Biking", "🏃 Running & Gym",
            "🎭 Theater & Arts", "🎼 Classical Music", "🪴 Gardening", "🎲 Board Games",
            "🐕 Animal Lover", "🕯️ Deep Conversations", "🏛️ Architecture", "⛵ Sailing"
        )
    }

    var selectedInterests by remember {
        mutableStateOf(user.interests.toSet())
    }

    // Spoken Languages
    val availableLangs = listOf("German", "English", "Turkish", "French", "Arabic", "Italian", "Spanish", "Russian")
    var selectedLanguages by remember {
        mutableStateOf(user.languages.toSet().ifEmpty { setOf("German", "English") })
    }

    // Photos state (minimum 3 required)
    val defaultSamplePhotos = remember {
        listOf(
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce?auto=format&fit=crop&w=800&q=80",
            "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?auto=format&fit=crop&w=800&q=80"
        )
    }
    var photosList by remember { mutableStateOf(user.photos.ifEmpty { defaultSamplePhotos }) }

    // Personality Prompts
    var prompt1Answer by remember { mutableStateOf("") }
    var prompt2Answer by remember { mutableStateOf("") }

    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (lang == AppLanguage.EN) "Complete Your Profile" else "Profil vervollständigen",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (lang == AppLanguage.EN) "Step 2: Interests, Bio & Details" else "Schritt 2: Interessen, Bio & Details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { repository.setAuthState(AuthState.WELCOME) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { repository.toggleLanguage() }) {
                        Text(if (lang == AppLanguage.EN) "DE" else "EN", fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Intro Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("💍", fontSize = 24.sp)
                        }
                    }
                    Column {
                        Text(
                            text = if (lang == AppLanguage.EN) "Serious & Respectful Matching" else "Ernsthafte Partnersuche",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (lang == AppLanguage.EN)
                                "Complete profiles receive 4x more mutual likes. Be authentic about your values and timeline for marriage."
                            else
                                "Vollständige Profile erhalten viermal mehr Likes. Zeigen Sie Ihre Werte und Vorstellungen für eine Ehe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                    }
                }
            }

            // 1. Personal Details Section
            Text(
                text = if (lang == AppLanguage.EN) "1. Personal Details & Career" else "1. Persönliche Angaben & Beruf",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Display Name
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text(Localization.t("first_name", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_creation_name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Occupation (Required)
                    OutlinedTextField(
                        value = occupation,
                        onValueChange = { occupation = it },
                        label = { Text(Localization.t("occupation", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("profile_creation_occupation_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Education
                    OutlinedTextField(
                        value = education,
                        onValueChange = { education = it },
                        label = { Text(Localization.t("education", lang)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // City Dropdown / Selector
                    Text(
                        text = if (lang == AppLanguage.EN) "City in Germany: $selectedCity ($selectedRegion)" else "Stadt in Deutschland: $selectedCity ($selectedRegion)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(GermanCities.list.take(8)) { city ->
                            FilterChip(
                                selected = selectedCity == city.name,
                                onClick = {
                                    selectedCity = city.name
                                    selectedRegion = city.state
                                },
                                label = { Text(city.name) }
                            )
                        }
                    }

                    // Marriage Timeline
                    Text(Localization.t("step_timeline", lang), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "within_1y" to "Within 1y",
                            "1_2y" to "1 to 2y",
                            "2_5y" to "2 to 5y",
                            "open" to "Open"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = marriageTimeline == key,
                                onClick = { marriageTimeline = key },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Children
                    Text(Localization.t("wants_children", lang), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = wantsChildren == "yes", onClick = { wantsChildren = "yes" }, label = { Text("Yes") })
                        FilterChip(selected = wantsChildren == "no", onClick = { wantsChildren = "no" }, label = { Text("No") })
                        FilterChip(selected = wantsChildren == "open", onClick = { wantsChildren = "open" }, label = { Text("Open") })
                    }
                }
            }

            // 2. Interests & Values Section
            Text(
                text = if (lang == AppLanguage.EN) "2. Interests, Passions & Values" else "2. Interessen, Hobbys & Werte",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "Select activities and passions that matter to you (${selectedInterests.size} selected):"
                        else
                            "Wählen Sie Ihre Hobbys und Interessen (${selectedInterests.size} gewählt):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.muted
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableInterests.forEach { interest ->
                            val isSelected = selectedInterests.contains(interest)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedInterests = if (isSelected) {
                                        selectedInterests - interest
                                    } else {
                                        selectedInterests + interest
                                    }
                                },
                                label = {
                                    Text(
                                        text = interest,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(Localization.t("languages_spoken", lang), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableLangs.forEach { l ->
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

            // 3. Bio & Marriage Vision Section
            Text(
                text = if (lang == AppLanguage.EN) "3. Bio & Marriage Vision" else "3. Über mich & Vorstellungen für die Ehe",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "Write about your personality, what brings you joy, and your aspirations for a lifelong partnership:"
                        else
                            "Schreiben Sie über Ihre Persönlichkeit und was Ihnen in einer Partnerschaft wichtig ist:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.muted
                    )

                    OutlinedTextField(
                        value = bioText,
                        onValueChange = { if (it.length <= 500) bioText = it },
                        placeholder = { Text(Localization.t("bio_placeholder", lang)) },
                        minLines = 4,
                        maxLines = 6,
                        supportingText = {
                            Text("${bioText.length}/500 characters", modifier = Modifier.fillMaxWidth())
                        },
                        modifier = Modifier.fillMaxWidth().testTag("profile_creation_bio_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    HorizontalDivider()

                    // Personality Prompts
                    Text(
                        text = "Prompt: My ideal Sunday is...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = prompt1Answer,
                        onValueChange = { prompt1Answer = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Prompt: What family means to me...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = prompt2Answer,
                        onValueChange = { prompt2Answer = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // 4. Photos Section
            Text(
                text = if (lang == AppLanguage.EN) "4. Profile Photos (Min 3)" else "4. Profilfotos (Mind. 3)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(photosList) { url ->
                            Box(
                                modifier = Modifier
                                    .size(110.dp, 150.dp)
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
                                        .size(26.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Text(
                        text = if (photosList.size >= 3) "✓ ${photosList.size} photos ready" else "⚠️ Need at least 3 photos (currently ${photosList.size})",
                        color = if (photosList.size >= 3) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (validationError != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Complete Profile Button
            MarryPrimaryButton(
                text = if (lang == AppLanguage.EN) "Save Profile & Start Discovering" else "Profil speichern & starten",
                onClick = {
                    if (displayName.isBlank()) {
                        validationError = if (lang == AppLanguage.EN) "Please enter your first name." else "Bitte geben Sie Ihren Vornamen ein."
                        return@MarryPrimaryButton
                    }
                    if (occupation.isBlank()) {
                        validationError = if (lang == AppLanguage.EN) "Occupation is required." else "Berufsangabe ist erforderlich."
                        return@MarryPrimaryButton
                    }
                    if (selectedInterests.size < 3) {
                        validationError = if (lang == AppLanguage.EN) "Please select at least 3 interests." else "Bitte wählen Sie mindestens 3 Interessen aus."
                        return@MarryPrimaryButton
                    }
                    if (photosList.size < 3) {
                        validationError = if (lang == AppLanguage.EN) "Please keep at least 3 photos." else "Bitte behalten Sie mindestens 3 Fotos."
                        return@MarryPrimaryButton
                    }

                    validationError = null

                    // Update user profile
                    val updatedPrompts = listOf(
                        ProfilePrompt("ideal_sunday", "My ideal Sunday is...", "Mein idealer Sonntag ist...", prompt1Answer),
                        ProfilePrompt("family_means", "What family means to me...", "Was Familie für mich bedeutet...", prompt2Answer),
                        ProfilePrompt("non_negotiable", "A value I will not compromise on...", "Ein Wert, bei dem ich keine Kompromisse eingehe...", "Integrity and mutual respect.")
                    )

                    repository.updateCurrentUser {
                        it.copy(
                            displayName = displayName,
                            city = selectedCity,
                            region = selectedRegion,
                            occupation = occupation,
                            education = education,
                            marriageTimeline = marriageTimeline,
                            wantsChildren = wantsChildren,
                            heightCm = heightCm,
                            smoking = smoking,
                            religion = if (religionConsent) religion else "",
                            interests = selectedInterests.toList(),
                            languages = selectedLanguages.toList(),
                            bio = bioText,
                            photos = photosList,
                            prompts = updatedPrompts,
                            onboardingDone = true
                        )
                    }

                    onComplete()
                    repository.setAuthState(AuthState.LOGGED_IN)
                },
                testTag = "save_profile_creation_btn"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
