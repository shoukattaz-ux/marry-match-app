package com.example.data

import com.example.core.PhotoProcessor
import com.example.model.PhotoFocus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** The user's own profile row, as stored in public.profiles. */
@Serializable
data class ProfileRow(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("birth_date") val birthDate: String,
    val gender: String,
    @SerialName("looking_for") val lookingFor: String,
    val city: String? = null,
    val region: String? = null,
    val bio: String? = null,
    @SerialName("marriage_timeline") val marriageTimeline: String? = null,
    @SerialName("wants_children") val wantsChildren: String? = null,
    val religion: String? = null,
    val smoking: String? = null,
    val education: String? = null,
    val occupation: String? = null,
    val languages: List<String> = emptyList(),
    @SerialName("height_cm") val heightCm: Int? = null,
    @SerialName("onboarding_done") val onboardingDone: Boolean = false,
    @SerialName("onboarding_step") val onboardingStep: Int = 1,
    val interests: List<String> = emptyList(),
    val drinking: String? = null,
    val exercise: String? = null,
    val diet: String? = null,
    val pets: String? = null,
    val sleep: String? = null,
    val personality: String? = null,
    @SerialName("family_closeness") val familyCloseness: String? = null,
    val household: String? = null,
    @SerialName("home_after_marriage") val homeAfterMarriage: String? = null,
    val relocate: String? = null
) {
    /** Habit and value answers by column name (see [com.example.model.MatchTraits]). */
    val traits: Map<String, String?>
        get() = mapOf(
            "smoking" to smoking, "drinking" to drinking, "exercise" to exercise, "diet" to diet,
            "pets" to pets, "sleep" to sleep, "personality" to personality,
            "family_closeness" to familyCloseness, "household" to household,
            "home_after_marriage" to homeAfterMarriage, "relocate" to relocate
        )
}

@Serializable
private data class PhotoRow(
    val id: String,
    @SerialName("storage_path") val storagePath: String,
    val position: Int,
    @SerialName("focus_x") val focusX: Float = 0f,
    @SerialName("focus_y") val focusY: Float = 0f,
    val zoom: Float = 1f
) {
    val focus get() = PhotoFocus(focusX, focusY, zoom)
}

@Serializable
private data class PromptRow(
    @SerialName("prompt_key") val promptKey: String,
    val answer: String
)

/** A stored profile photo; [url] is the public address used to show it. */
data class ProfilePhoto(
    val id: String,
    val storagePath: String,
    val url: String,
    val position: Int,
    /** How the round avatar shows this photo when it is the main one. */
    val focus: PhotoFocus = PhotoFocus()
)

data class PromptAnswer(val key: String, val answer: String)

data class MyProfile(
    val row: ProfileRow,
    val photos: List<ProfilePhoto>,
    val prompts: List<PromptAnswer>
)

/**
 * Reads and writes the signed-in user's own profile: profiles, profile_photos (+ the
 * profile-photos storage bucket), profile_prompts and consents. Row Level Security on the
 * server makes sure a user can only change their own rows.
 */
class ProfileService(private val client: SupabaseClient, private val photoLinks: PhotoUrlSigner) {

    private val photoBucket get() = client.storage.from(PHOTO_BUCKET)

    /** Returns null when the user has not saved a profile yet. */
    suspend fun loadMyProfile(userId: String): MyProfile? {
        val row = client.from("profiles")
            .select(columns = Columns.list(PROFILE_COLUMNS)) { filter { eq("id", userId) } }
            .decodeList<ProfileRow>()
            .firstOrNull() ?: return null
        return MyProfile(row = row, photos = loadPhotos(userId), prompts = loadPrompts(userId))
    }

    suspend fun loadPhotos(userId: String): List<ProfilePhoto> =
        client.from("profile_photos")
            .select(columns = Columns.list("id", "storage_path", "position", "focus_x", "focus_y", "zoom")) {
                filter { eq("user_id", userId) }
                order("position", Order.ASCENDING)
            }
            .decodeList<PhotoRow>()
            .let { rows ->
                photoLinks.urls(rows.map { it.storagePath })  // one request for all, then served from the cache
                rows.map { ProfilePhoto(it.id, it.storagePath, photoLinks.url(it.storagePath).orEmpty(), it.position, it.focus) }
            }

    /** Saves how the round avatar shows this photo ("Adjust photo"). */
    suspend fun savePhotoFocus(photoId: String, focus: PhotoFocus) {
        val f = focus.clamped()
        client.from("profile_photos").update(buildJsonObject {
            put("focus_x", f.x)
            put("focus_y", f.y)
            put("zoom", f.zoom)
        }) { filter { eq("id", photoId) } }
    }

    private suspend fun loadPrompts(userId: String): List<PromptAnswer> =
        client.from("profile_prompts")
            .select(columns = Columns.list("prompt_key", "answer")) { filter { eq("user_id", userId) } }
            .decodeList<PromptRow>()
            .map { PromptAnswer(it.promptKey, it.answer) }

    /**
     * Writes only the keys present in [fields]. Use [create] for the first save, which must
     * contain every required column (name, birth date, gender, looking for). Later saves are
     * plain updates: a partial upsert would fail on the required columns it does not send.
     */
    suspend fun saveProfile(userId: String, fields: JsonObject, create: Boolean) {
        if (create) {
            val body = buildJsonObject {
                put("id", userId)
                fields.forEach { (key, value) -> put(key, value) }
            }
            client.from("profiles").upsert(body)
        } else {
            client.from("profiles").update(fields) { filter { eq("id", userId) } }
        }
    }

    /**
     * Uploads an already compressed JPEG to profile-photos/<user_id>/<random>.jpg, the small
     * list copy next to it as <random>_t.jpg (see [thumbPathFor]) and the blurred copy as
     * <random>_b.jpg (see [blurPathFor]).
     */
    suspend fun uploadPhoto(
        userId: String,
        jpeg: ByteArray,
        position: Int,
        thumb: ByteArray? = null,
        blurred: ByteArray? = null
    ): ProfilePhoto {
        val path = "$userId/${UUID.randomUUID()}.jpg"
        photoBucket.upload(path, jpeg) {
            contentType = ContentType.Image.JPEG
            upsert = false
        }
        // Both copies are optional here: lists fall back to the full photo when the small copy is
        // missing, and a missing blurred copy is made by [ensureBlurredCopies].
        for ((copyPath, bytes) in listOf(thumbPathFor(path) to thumb, blurPathFor(path) to blurred)) {
            if (bytes == null) continue
            runCatching {
                photoBucket.upload(copyPath, bytes) {
                    contentType = ContentType.Image.JPEG
                    upsert = false
                }
            }
        }
        val row = try {
            client.from("profile_photos")
                .insert(buildJsonObject {
                    put("user_id", userId)
                    put("storage_path", path)
                    put("position", position)
                }) { select(Columns.list("id", "storage_path", "position")) }
                .decodeSingle<PhotoRow>()
        } catch (e: Throwable) {
            // Do not leave an orphaned file behind when the row is refused (e.g. 6-photo limit).
            runCatching { photoBucket.delete(listOf(path, thumbPathFor(path), blurPathFor(path))) }
            throw e
        }
        return ProfilePhoto(row.id, row.storagePath, photoLinks.url(row.storagePath).orEmpty(), row.position)
    }

    suspend fun deletePhoto(photo: ProfilePhoto) {
        client.from("profile_photos").delete { filter { eq("id", photo.id) } }
        runCatching { photoBucket.delete(listOf(photo.storagePath, thumbPathFor(photo.storagePath), blurPathFor(photo.storagePath))) }
    }

    /**
     * Makes the blurred copy of every photo that does not have one yet (photos uploaded before
     * blurring existed, or whose copy failed to upload). Must succeed before "blur my photos" is
     * switched on: while it is on, others are only given the blurred copies.
     */
    suspend fun ensureBlurredCopies(userId: String) {
        val existing = photoBucket.list(userId) { limit = 100 }.map { "$userId/${it.name}" }.toSet()
        val missing = loadPhotoPaths(userId).filter { blurPathFor(it) !in existing }
        for (path in missing) {
            val blurred = PhotoProcessor.blurredCopy(photoBucket.downloadAuthenticated(path))
                ?: throw IllegalStateException("Unreadable photo")
            photoBucket.upload(blurPathFor(path), blurred) {
                contentType = ContentType.Image.JPEG
                upsert = true
            }
        }
    }

    private suspend fun loadPhotoPaths(userId: String): List<String> =
        client.from("profile_photos")
            .select(columns = Columns.list("id", "storage_path", "position")) { filter { eq("user_id", userId) } }
            .decodeList<PhotoRow>()
            .map { it.storagePath }

    /** Saves the order shown on screen; the first photo is the main one. */
    suspend fun savePhotoOrder(photos: List<ProfilePhoto>) {
        photos.forEachIndexed { index, photo ->
            if (photo.position != index) {
                client.from("profile_photos").update({ set("position", index) }) {
                    filter { eq("id", photo.id) }
                }
            }
        }
    }

    /** Replaces all prompt answers of the user. */
    suspend fun savePrompts(userId: String, prompts: List<PromptAnswer>) {
        client.from("profile_prompts").delete { filter { eq("user_id", userId) } }
        prompts.forEach { prompt ->
            client.from("profile_prompts").insert(buildJsonObject {
                put("user_id", userId)
                put("prompt_key", prompt.key)
                put("answer", prompt.answer)
            })
        }
    }

    /** Records a consent once per version; a repeated consent is not an error. */
    suspend fun saveConsent(userId: String, consentType: String, version: String) {
        try {
            client.from("consents").insert(buildJsonObject {
                put("user_id", userId)
                put("consent_type", consentType)
                put("version", version)
            })
        } catch (e: Throwable) {
            if (e.message?.contains("duplicate key") != true && e.message?.contains("23505") != true) throw e
        }
    }

    companion object {
        const val PHOTO_BUCKET = "profile-photos"

        /** Path of the small list copy of a photo: "<user>/<id>.jpg" -> "<user>/<id>_t.jpg". */
        fun thumbPathFor(path: String): String = path.removeSuffix(".jpg") + "_t.jpg"

        /** Path of the blurred copy: "<user>/<id>.jpg" -> "<user>/<id>_b.jpg" (migration 019). */
        fun blurPathFor(path: String): String = path.removeSuffix(".jpg") + "_b.jpg"
        const val MIN_PHOTOS = 3
        const val MAX_PHOTOS = 6

        private val PROFILE_COLUMNS = listOf(
            "id", "display_name", "birth_date", "gender", "looking_for", "city", "region", "bio",
            "marriage_timeline", "wants_children", "religion", "smoking", "education", "occupation",
            "languages", "height_cm", "onboarding_done", "onboarding_step", "interests",
            "drinking", "exercise", "diet", "pets", "sleep", "personality", "family_closeness",
            "household", "home_after_marriage", "relocate"
        )
    }
}
