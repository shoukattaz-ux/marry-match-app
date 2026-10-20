package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppConfig
import com.example.core.CityLocator
import com.example.core.PhotoProcessor
import com.example.data.MarryMatchRepository
import com.example.data.ProfilePhoto
import com.example.data.ProfileService
import com.example.data.PromptAnswer
import com.example.model.MatchTraits
import com.example.model.Trait
import com.example.model.PromptCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URL
import java.time.LocalDate
import java.time.Period

data class OnboardingState(
    val editMode: Boolean,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val step: Int = 1,
    // Step 1
    val displayName: String = "",
    val birthDate: String = "",            // YYYY-MM-DD; empty when not known yet
    val birthDateLocked: Boolean = false,  // known from sign-up, so it cannot be changed here
    val birthDay: String = "",
    val birthMonth: String = "",
    val birthYear: String = "",
    // Step 2
    val gender: String = "",
    val lookingFor: String = "",
    // Step 3
    val city: String = "",
    val region: String = "",
    val locating: Boolean = false,
    // Step 4
    val marriageTimeline: String = "",
    // Step 5
    val wantsChildren: String = "",
    val languages: Set<String> = emptySet(),
    // Step 6
    val occupation: String = "",
    val education: String = "",
    val heightText: String = "",
    // Steps 7 (habits) and 9 (values): answer by Trait.key, see MatchTraits
    val traits: Map<String, String> = emptyMap(),
    // Step 8
    val interests: Set<String> = emptySet(),
    // Step 10
    val religionConsent: Boolean = false,
    val religion: String = "",
    // Step 11
    val photos: List<ProfilePhoto> = emptyList(),
    val faceCounts: Map<String, Int?> = emptyMap(),
    val photoBusy: Boolean = false,
    // Step 12
    val prompts: List<PromptAnswer> = PromptCatalog.fillUp(emptyList()).map { PromptAnswer(it.first, it.second) },
    // Step 13
    val bio: String = "",
    /** Localization key of the message to show, or null. */
    val errorKey: String? = null,
    val finished: Boolean = false
) {
    /** Age from the locked birth date or from the three input fields. */
    val age: Int?
        get() = parseBirthDate()?.let { Period.between(it, LocalDate.now()).years }

    fun parseBirthDate(): LocalDate? = try {
        if (birthDateLocked) LocalDate.parse(birthDate)
        else LocalDate.of(birthYear.toInt(), birthMonth.toInt(), birthDay.toInt())
    } catch (e: Exception) {
        null
    }
}

/**
 * Drives the 13-step onboarding (Phase 3) and the same steps for editing a finished profile.
 * Each step is saved to the server when the user continues, so a user who leaves midway
 * resumes at the step stored in profiles.onboarding_step.
 */
class OnboardingViewModel(
    private val repository: MarryMatchRepository,
    editMode: Boolean,
    /** Where the editor opens, e.g. [STEP_HABITS] to add the match details. */
    private val startStep: Int = 1
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState(editMode = editMode))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var profileRowExists = false

    init {
        load()
    }

    fun edit(change: OnboardingState.() -> OnboardingState) {
        _state.update { it.change().copy(errorKey = null) }
    }

    fun load() {
        val service = repository.profileService
        val user = repository.currentUser.value
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val profile = service?.loadMyProfile(user.id)
                profileRowExists = profile != null
                val row = profile?.row
                val birthDate = row?.birthDate ?: user.birthDate
                _state.update { s ->
                    s.copy(
                        loading = false,
                        step = if (s.editMode) startStep.coerceIn(1, TOTAL_STEPS) else (row?.onboardingStep ?: 1),
                        displayName = row?.displayName ?: user.displayName,
                        birthDate = birthDate,
                        birthDateLocked = birthDate.isNotBlank(),
                        gender = knownGender(row?.gender),
                        lookingFor = lookingForAfterGender(knownGender(row?.gender)),
                        city = row?.city.orEmpty(),
                        region = row?.region.orEmpty(),
                        marriageTimeline = row?.marriageTimeline.orEmpty(),
                        wantsChildren = row?.wantsChildren.orEmpty(),
                        languages = row?.languages?.toSet() ?: emptySet(),
                        occupation = row?.occupation.orEmpty(),
                        education = row?.education.orEmpty(),
                        heightText = row?.heightCm?.toString().orEmpty(),
                        traits = row?.let { MatchTraits.known(it.traits) } ?: emptyMap(),
                        interests = row?.interests.orEmpty().filter { it in MatchTraits.INTERESTS }.toSet(),
                        religionConsent = !row?.religion.isNullOrBlank(),
                        religion = row?.religion.orEmpty(),
                        photos = profile?.photos.orEmpty(),
                        // Older profiles answered 3 questions; they keep those and get 3 more.
                        prompts = profile?.prompts
                            ?.let { saved -> PromptCatalog.fillUp(saved.map { it.key to it.answer }).map { PromptAnswer(it.first, it.second) } }
                            ?: s.prompts,
                        bio = row?.bio.orEmpty()
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, loadFailed = true) }
            }
        }
    }

    fun back() {
        _state.update { if (it.step > 1) it.copy(step = it.step - 1, errorKey = null) else it }
    }

    /** Validates and saves the current step, then moves on (or finishes after the last step). */
    fun next() {
        val s = _state.value
        if (s.saving || s.photoBusy) return
        validate(s)?.let { key ->
            _state.update { it.copy(errorKey = key) }
            return
        }
        _state.update { it.copy(saving = true, errorKey = null) }
        viewModelScope.launch {
            try {
                saveStep(s)
                if (s.step < TOTAL_STEPS) {
                    _state.update { it.copy(saving = false, step = it.step + 1) }
                } else {
                    repository.reloadMyProfile()
                    _state.update { it.copy(saving = false, finished = true) }
                }
            } catch (e: MainPhotoException) {
                _state.update { it.copy(saving = false, errorKey = "photo_main_face") }
            } catch (e: Throwable) {
                _state.update { it.copy(saving = false, errorKey = "profile_save_failed") }
            }
        }
    }

    private fun validate(s: OnboardingState): String? = when (s.step) {
        1 -> when {
            s.displayName.isBlank() || s.displayName.trim().length > 40 -> "name_required"
            s.age == null -> "birth_date_invalid"
            s.age!! < AppConfig.MIN_AGE -> "under_18_blocked"
            else -> null
        }
        2 -> if (s.gender.isBlank() || s.lookingFor.isBlank()) "gender_required" else null
        3 -> if (s.city.isBlank() || s.region.isBlank()) "city_required" else null
        4 -> if (s.marriageTimeline.isBlank()) "timeline_required" else null
        5 -> if (s.wantsChildren.isBlank() || s.languages.isEmpty()) "children_required" else null
        6 -> when {
            s.occupation.isBlank() -> "occupation_required"
            s.heightText.isNotBlank() && s.heightText.toIntOrNull()?.let { it in 120..230 } != true -> "height_invalid"
            else -> null
        }
        STEP_HABITS -> if (missingAnswers(s, MatchTraits.HABITS)) "habits_required" else null
        STEP_INTERESTS -> if (s.interests.size < MatchTraits.MIN_INTERESTS) "interests_required" else null
        STEP_VALUES -> if (missingAnswers(s, MatchTraits.VALUES)) "values_required" else null
        10 -> if (s.religionConsent && s.religion.isBlank()) "religion_required_text" else null
        11 -> if (s.photos.size < ProfileService.MIN_PHOTOS) "photos_required" else null
        12 -> if (s.prompts.any { !promptAnswered(it.answer) }) "prompts_required" else null
        13 -> if (s.bio.isBlank()) "bio_required" else null
        else -> null
    }

    private suspend fun saveStep(s: OnboardingState) {
        val service = repository.profileService ?: throw IllegalStateException("Supabase is not configured.")
        val userId = repository.currentUser.value.id
        val nextStep = (s.step + 1).coerceAtMost(TOTAL_STEPS)

        val fields: JsonObject? = when (s.step) {
            1 -> if (profileRowExists) basics(s) else null  // the row needs gender first (step 2)
            2 -> buildJsonObject {
                basics(s).forEach { (k, v) -> put(k, v) }
                put("gender", s.gender)
                put("looking_for", s.lookingFor)
            }
            3 -> buildJsonObject { put("city", s.city); put("region", s.region) }
            4 -> buildJsonObject { put("marriage_timeline", s.marriageTimeline) }
            5 -> buildJsonObject {
                put("wants_children", s.wantsChildren)
                put("languages", JsonArray(s.languages.sorted().map { JsonPrimitive(it) }))
            }
            6 -> buildJsonObject {
                put("occupation", s.occupation.trim())
                put("education", s.education.trim().ifBlank { null }.toJson())
                put("height_cm", s.heightText.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            STEP_HABITS -> traitFields(s, MatchTraits.HABITS)
            STEP_INTERESTS -> buildJsonObject {
                put("interests", JsonArray(MatchTraits.INTERESTS.filter { it in s.interests }.map { JsonPrimitive(it) }))
            }
            STEP_VALUES -> traitFields(s, MatchTraits.VALUES)
            10 -> {
                if (s.religionConsent) {
                    // Consent is stored before the sensitive value itself (GDPR Art. 9).
                    service.saveConsent(userId, "sensitive_profile", AppConfig.SENSITIVE_PROFILE_VERSION)
                }
                buildJsonObject {
                    put("religion", (if (s.religionConsent) s.religion.trim() else null).toJson())
                }
            }
            11 -> {
                ensureMainPhotoHasOneFace(s)
                service.savePhotoOrder(s.photos)
                _state.update { st -> st.copy(photos = st.photos.mapIndexed { i, p -> p.copy(position = i) }) }
                JsonObject(emptyMap())
            }
            12 -> {
                service.savePrompts(userId, s.prompts.map { PromptAnswer(it.key, it.answer.trim().take(PromptCatalog.MAX_ANSWER_CHARS)) })
                JsonObject(emptyMap())
            }
            13 -> buildJsonObject {
                put("bio", s.bio.trim().take(500))
                put("onboarding_done", true)
            }
            else -> null
        }

        val hasSomethingToSave = fields != null && (fields.isNotEmpty() || !s.editMode)
        if (hasSomethingToSave && fields != null && (profileRowExists || s.step >= 2)) {
            val withProgress = if (s.editMode) fields else buildJsonObject {
                fields.forEach { (k, v) -> put(k, v) }
                put("onboarding_step", nextStep)
            }
            service.saveProfile(userId, withProgress, create = !profileRowExists)
            profileRowExists = true
        }
    }

    private fun traitFields(s: OnboardingState, traits: List<Trait>): JsonObject = buildJsonObject {
        traits.forEach { put(it.key, s.traits[it.key].toJson()) }
    }

    /** Picks [option] for [key]; tapping the chosen answer again clears it. */
    fun chooseTrait(key: String, option: String) = edit {
        copy(traits = if (traits[key] == option) traits - key else traits + (key to option))
    }

    /** Adds or removes an interest; at most [MatchTraits.MAX_INTERESTS]. */
    fun toggleInterest(key: String) {
        val s = _state.value
        when {
            key in s.interests -> edit { copy(interests = interests - key) }
            s.interests.size >= MatchTraits.MAX_INTERESTS -> _state.update { it.copy(errorKey = "interests_limit") }
            else -> edit { copy(interests = interests + key) }
        }
    }

    private fun basics(s: OnboardingState): JsonObject = buildJsonObject {
        put("display_name", s.displayName.trim())
        put("birth_date", s.parseBirthDate().toString())
    }

    fun useMyLocation(context: Context) {
        _state.update { it.copy(locating = true, errorKey = null) }
        viewModelScope.launch {
            val guess = CityLocator.guess(context.applicationContext)
            _state.update {
                if (guess != null) it.copy(locating = false, city = guess.city, region = guess.region)
                else it.copy(locating = false, errorKey = "location_not_found")
            }
        }
    }

    fun addPhoto(context: Context, uri: Uri) = addPhotos(context, listOf(uri))

    /**
     * Uploads the photos picked in one go, in the order picked, up to the 6-photo limit. One that
     * fails is skipped and the rest still upload; the first problem is shown afterwards.
     */
    fun addPhotos(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val free = photosStillAllowed(_state.value.photos.size)
        if (free == 0) {
            _state.update { it.copy(errorKey = "photo_limit") }
            return
        }
        val service = repository.profileService ?: return
        val userId = repository.currentUser.value.id
        _state.update { it.copy(photoBusy = true, errorKey = null) }
        viewModelScope.launch {
            var firstError: String? = if (uris.size > free) "photo_limit" else null
            for (uri in uris.take(free)) {
                val error = try {
                    val prepared = PhotoProcessor.prepare(context.applicationContext, uri, withThumbnail = true)
                    // The first photo becomes the main one, which must show exactly one face.
                    val faceError = if (_state.value.photos.isEmpty()) faceErrorKey(prepared.faceCount) else null
                    if (faceError == null) {
                        val photo = service.uploadPhoto(userId, prepared.jpeg, _state.value.photos.size, prepared.thumb, prepared.blurred)
                        _state.update {
                            it.copy(photos = it.photos + photo, faceCounts = it.faceCounts + (photo.id to prepared.faceCount))
                        }
                    }
                    faceError
                } catch (e: Throwable) {
                    "photo_failed"
                }
                if (firstError == null) firstError = error
            }
            _state.update { it.copy(photoBusy = false, errorKey = firstError) }
        }
    }

    fun removePhoto(photo: ProfilePhoto) {
        val s = _state.value
        // A finished profile must keep at least 3 photos.
        if (s.editMode && s.photos.size <= ProfileService.MIN_PHOTOS) {
            _state.update { it.copy(errorKey = "photos_required") }
            return
        }
        val service = repository.profileService ?: return
        _state.update { it.copy(photoBusy = true, errorKey = null) }
        viewModelScope.launch {
            try {
                service.deletePhoto(photo)
                _state.update { it.copy(photoBusy = false, photos = it.photos - photo) }
            } catch (e: Throwable) {
                _state.update { it.copy(photoBusy = false, errorKey = "profile_save_failed") }
            }
        }
    }

    /** Moves [photo] to the front after checking that it shows exactly one face. */
    fun makeMainPhoto(photo: ProfilePhoto) {
        _state.update { it.copy(photoBusy = true, errorKey = null) }
        viewModelScope.launch {
            val faces = faceCountOf(photo)
            val error = faceErrorKey(faces)
            _state.update { s ->
                if (error != null) s.copy(photoBusy = false, errorKey = error)
                else s.copy(photoBusy = false, photos = listOf(photo) + (s.photos - photo))
            }
        }
    }

    private suspend fun ensureMainPhotoHasOneFace(s: OnboardingState) {
        val main = s.photos.firstOrNull() ?: return
        if (faceErrorKey(faceCountOf(main)) != null) throw MainPhotoException()
    }

    /** Face count from this session, or measured from the stored file for older photos. */
    private suspend fun faceCountOf(photo: ProfilePhoto): Int? {
        if (_state.value.faceCounts.containsKey(photo.id)) return _state.value.faceCounts[photo.id]
        val count = try {
            val bytes = withContext(Dispatchers.IO) { URL(photo.url).readBytes() }
            PhotoProcessor.countFaces(bytes)
        } catch (e: Throwable) {
            null
        }
        _state.update { it.copy(faceCounts = it.faceCounts + (photo.id to count)) }
        return count
    }

    /** Null means the photo is fine; an unknown count (detector unavailable) is not blocked. */
    private fun faceErrorKey(faces: Int?): String? = when (faces) {
        null, 1 -> null
        0 -> "photo_no_face"
        else -> "photo_many_faces"
    }

    private class MainPhotoException : Exception()

    private fun String?.toJson() = this?.let { JsonPrimitive(it) } ?: JsonNull

    /** "I am a ...": a man looks for a woman and a woman for a man (the server sets it the same way). */
    fun chooseGender(gender: String) = edit {
        copy(gender = gender, lookingFor = lookingForAfterGender(gender))
    }

    companion object {
        const val TOTAL_STEPS = 13
        const val STEP_HABITS = 7
        const val STEP_INTERESTS = 8
        const val STEP_VALUES = 9

        /** An answer counts once it says something (at least [PromptCatalog.MIN_ANSWER_CHARS] characters). */
        fun promptAnswered(answer: String): Boolean = answer.trim().length >= PromptCatalog.MIN_ANSWER_CHARS

        /** How many more photos fit, 0..6. */
        fun photosStillAllowed(current: Int): Int = (ProfileService.MAX_PHOTOS - current).coerceIn(0, ProfileService.MAX_PHOTOS)

        fun missingAnswers(s: OnboardingState, traits: List<Trait>): Boolean =
            traits.any { s.traits[it.key] !in it.options }

        /** "I am a ..." offers only these two. */
        val GENDERS = listOf("man", "woman")

        /** A saved gender that is no longer offered counts as not chosen. */
        fun knownGender(gender: String?): String = gender?.takeIf { it in GENDERS }.orEmpty()

        /** The "Looking for" choice offered for [gender]; none until a gender is chosen. */
        fun lookingForOptions(gender: String): List<String> = when (gender) {
            "man" -> listOf("woman")
            "woman" -> listOf("man")
            else -> emptyList()
        }

        /** A man looks for a woman and a woman for a man; nothing before a gender is chosen. */
        fun lookingForAfterGender(gender: String): String =
            lookingForOptions(gender).singleOrNull().orEmpty()
        val LANGUAGES = listOf("German", "English", "Turkish", "Arabic", "Polish", "Russian", "French", "Italian", "Spanish")
    }
}
