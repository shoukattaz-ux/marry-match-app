package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MarryMatchRepository
import com.example.data.PromptAnswer
import com.example.model.GermanCities
import com.example.model.PromptCatalog
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.MarrySecondaryButton
import com.example.ui.theme.extendedColors
import com.example.ui.viewmodel.OnboardingState
import com.example.ui.viewmodel.OnboardingViewModel

/**
 * The 10-step profile setup (blueprint Phase 3), one question per screen with a progress bar.
 * With [editMode] the same steps edit a finished profile and [onClose] is called when done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    repository: MarryMatchRepository,
    editMode: Boolean = false,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val vm: OnboardingViewModel = viewModel(key = "onboarding_$editMode") {
        OnboardingViewModel(repository, editMode)
    }
    val state by vm.state.collectAsState()
    val lang by repository.appLanguage.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }
    val closeEditor = {
        repository.refreshMyProfile()
        onClose()
    }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            if (editMode) onClose() else repository.onOnboardingFinished()
        }
    }

    // System back goes one step back; on step 1 it leaves the editor (onboarding cannot be skipped).
    BackHandler(enabled = state.step > 1 || editMode) {
        if (state.step > 1) vm.back() else closeEditor()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            t(if (editMode) "edit_profile_title" else "onboarding_title"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            String.format(t("step_of"), state.step, OnboardingViewModel.TOTAL_STEPS),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.muted
                        )
                    }
                },
                navigationIcon = {
                    if (state.step > 1) {
                        IconButton(onClick = vm::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("back"))
                        }
                    } else if (editMode) {
                        IconButton(onClick = closeEditor) {
                            Icon(Icons.Default.Close, contentDescription = t("close"))
                        }
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
        when {
            state.loading -> Box(
                modifier = modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.loadFailed -> Column(
                modifier = modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(t("profile_load_failed"), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                MarryPrimaryButton(text = t("retry"), onClick = vm::load, testTag = "onboarding_retry_btn")
            }

            else -> Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { state.step / OnboardingViewModel.TOTAL_STEPS.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                when (state.step) {
                    1 -> NameAndAgeStep(state, vm, t)
                    2 -> GenderStep(state, vm, t)
                    3 -> CityStep(state, vm, t)
                    4 -> TimelineStep(state, vm, t)
                    5 -> FamilyStep(state, vm, t)
                    6 -> LifestyleStep(state, vm, t)
                    7 -> ReligionStep(state, vm, t)
                    8 -> PhotosStep(state, vm, t)
                    9 -> PromptsStep(state, vm, t, lang)
                    10 -> BioStep(state, vm, t)
                }

                state.errorKey?.let { key ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            t(key),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp).testTag("onboarding_error")
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                MarryPrimaryButton(
                    text = when {
                        state.step < OnboardingViewModel.TOTAL_STEPS -> t("continue")
                        editMode -> t("save")
                        else -> t("complete_profile")
                    },
                    onClick = vm::next,
                    enabled = !state.saving && !state.photoBusy,
                    testTag = "onboarding_continue_btn"
                )
                if (state.saving) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.muted)
    }
}

@Composable
private fun NameAndAgeStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_name_age"), t("your_age"))
    OutlinedTextField(
        value = state.displayName,
        onValueChange = { v -> if (v.length <= 40) vm.edit { copy(displayName = v) } },
        label = { Text(t("first_name")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input"),
        shape = RoundedCornerShape(12.dp)
    )
    if (!state.birthDateLocked) {
        // Only for accounts created before the birth date was stored at sign-up.
        Text(t("birth_date_full"), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(state.birthDay, 2, "birth_day"),
                Triple(state.birthMonth, 2, "birth_month"),
                Triple(state.birthYear, 4, "birth_year_full")
            ).forEachIndexed { index, (value, maxLen, labelKey) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        if (v.length <= maxLen && v.all(Char::isDigit)) vm.edit {
                            when (index) {
                                0 -> copy(birthDay = v)
                                1 -> copy(birthMonth = v)
                                else -> copy(birthYear = v)
                            }
                        }
                    },
                    label = { Text(t(labelKey)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(if (index == 2) 1.3f else 1f),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
    state.age?.let { age ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "${t("age")}: $age",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun GenderStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_gender"))
    Text(t("gender"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("woman", "man", "other").forEach { g ->
            ChoiceChip(label = t(g), selected = state.gender == g) { vm.edit { copy(gender = g) } }
        }
    }
    Text(t("looking_for"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("woman", "man", "any").forEach { g ->
            ChoiceChip(label = t(g), selected = state.lookingFor == g) { vm.edit { copy(lookingFor = g) } }
        }
    }
}

@Composable
private fun CityStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    StepTitle(t("step_city"))
    MarrySecondaryButton(
        text = t("use_my_location"),
        onClick = { vm.useMyLocation(context) },
        enabled = !state.locating,
        leadingIcon = Icons.Default.MyLocation,
        testTag = "onboarding_use_location_btn"
    )
    if (state.locating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(t("search_city")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )

    // The located city may not be in the short list, so show the current choice first.
    val options = buildList {
        if (state.city.isNotBlank() && GermanCities.list.none { it.name == state.city }) add(state.city to state.region)
        GermanCities.list
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .forEach { add(it.name to it.state) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (city, region) ->
            val selected = state.city == city
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.edit { copy(city = city, region = region) } }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(city, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(region, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.muted)
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TimelineStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_timeline"))
    listOf("within_1y", "1_2y", "2_5y", "open").forEach { key ->
        SelectableOptionCard(
            title = t("timeline_$key"),
            subtitle = t(if (key == "within_1y" || key == "1_2y") "timeline_hint_soon" else "timeline_hint_later"),
            selected = state.marriageTimeline == key,
            onClick = { vm.edit { copy(marriageTimeline = key) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_family"))
    Text(t("wants_children"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("yes", "no", "open").forEach { c ->
            ChoiceChip(label = t("children_$c"), selected = state.wantsChildren == c) { vm.edit { copy(wantsChildren = c) } }
        }
    }
    Text(t("languages_spoken"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OnboardingViewModel.LANGUAGES.forEach { l ->
            val selected = l in state.languages
            FilterChip(
                selected = selected,
                onClick = { vm.edit { copy(languages = if (selected) languages - l else languages + l) } },
                label = { Text(t("lang_$l")) }
            )
        }
    }
}

@Composable
private fun LifestyleStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_lifestyle"))
    OutlinedTextField(
        value = state.occupation,
        onValueChange = { v -> vm.edit { copy(occupation = v) } },
        label = { Text(t("occupation")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_occupation_input"),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = state.education,
        onValueChange = { v -> vm.edit { copy(education = v) } },
        label = { Text(t("education_optional")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    OutlinedTextField(
        value = state.heightText,
        onValueChange = { v -> if (v.length <= 3 && v.all(Char::isDigit)) vm.edit { copy(heightText = v) } },
        label = { Text(t("height_optional")) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    )
    Text(t("smoking"), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OnboardingViewModel.SMOKING.forEach { s ->
            ChoiceChip(label = t("smoking_$s"), selected = state.smoking == s) {
                vm.edit { copy(smoking = if (smoking == s) "" else s) }
            }
        }
    }
}

@Composable
private fun ReligionStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_religion"))
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(t("sensitive_data_notice"), style = MaterialTheme.typography.bodySmall)
        }
    }
    // Unticked by default: explicit, separate consent for special-category data.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.edit { copy(religionConsent = !religionConsent) } }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = state.religionConsent,
            onCheckedChange = { checked -> vm.edit { copy(religionConsent = checked) } },
            modifier = Modifier.testTag("onboarding_religion_consent")
        )
        Spacer(Modifier.width(8.dp))
        Text(t("sensitive_consent_box"), style = MaterialTheme.typography.bodySmall)
    }
    if (state.religionConsent) {
        OutlinedTextField(
            value = state.religion,
            onValueChange = { v -> if (v.length <= 60) vm.edit { copy(religion = v) } },
            label = { Text(t("religion_label")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun PhotosStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addPhoto(context, uri)
    }
    StepTitle(t("step_photos"), t("step_photos_desc"))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(state.photos, key = { _, p -> p.id }) { index, photo ->
            Box(modifier = Modifier.size(130.dp, 170.dp).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = photo.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { vm.removePhoto(photo) },
                    enabled = !state.photoBusy,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = t("delete"), tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clickable(enabled = index > 0 && !state.photoBusy) { vm.makeMainPhoto(photo) }
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (index == 0) Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(
                            t(if (index == 0) "main_photo" else "make_main_photo"),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
    Text(
        String.format(t("photos_count"), state.photos.size),
        style = MaterialTheme.typography.labelLarge,
        color = if (state.photos.size >= 3) MaterialTheme.colorScheme.secondary else MaterialTheme.extendedColors.muted
    )
    if (state.photoBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    MarrySecondaryButton(
        text = t("add_photo"),
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        enabled = !state.photoBusy && state.photos.size < 6,
        leadingIcon = Icons.Default.Add,
        testTag = "onboarding_add_photo_btn"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptsStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String, lang: AppLanguage) {
    StepTitle(t("step_prompts"), t("choose_prompts_desc"))
    state.prompts.forEachIndexed { index, prompt ->
        var expanded by remember { mutableStateOf(false) }
        val usedByOthers = state.prompts.filterIndexed { i, _ -> i != index }.map { it.key }.toSet()
        val question = PromptCatalog.find(prompt.key)
        Text(String.format(t("prompt_question"), index + 1), style = MaterialTheme.typography.labelLarge)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = question?.let { if (lang == AppLanguage.DE) it.questionDe else it.questionEn }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PromptCatalog.all.filter { it.key !in usedByOthers }.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(if (lang == AppLanguage.DE) option.questionDe else option.questionEn) },
                        onClick = {
                            expanded = false
                            vm.edit { copy(prompts = prompts.replaceAt(index, PromptAnswer(option.key, prompt.answer))) }
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = prompt.answer,
            onValueChange = { v ->
                if (v.length <= 300) vm.edit { copy(prompts = prompts.replaceAt(index, prompt.copy(answer = v))) }
            },
            label = { Text(t("prompt_answer")) },
            supportingText = { Text("${prompt.answer.length} / 300") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("onboarding_prompt_answer_$index"),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun BioStep(state: OnboardingState, vm: OnboardingViewModel, t: (String) -> String) {
    StepTitle(t("step_bio"))
    OutlinedTextField(
        value = state.bio,
        onValueChange = { v -> if (v.length <= 500) vm.edit { copy(bio = v) } },
        placeholder = { Text(t("bio_placeholder")) },
        minLines = 4,
        maxLines = 8,
        supportingText = { Text("${state.bio.length} / 500") },
        modifier = Modifier.fillMaxWidth().testTag("onboarding_bio_input"),
        shape = RoundedCornerShape(12.dp)
    )
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }

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
