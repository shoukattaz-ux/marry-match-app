package com.example.data

import com.example.model.ProfilePrompt
import com.example.model.PromptCatalog
import com.example.model.UserProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The user's Discover filters, stored on their profile (pref_* columns). */
data class DiscoveryPrefs(
    val minAge: Int = 18,
    val maxAge: Int = 99,
    val region: String? = null,
    val verifiedOnly: Boolean = false,
    val timelines: Set<String> = emptySet(),
    val languages: Set<String> = emptySet()
) {
    /** Applies the filters to today's set right away; the server uses them for tomorrow's set. */
    fun accepts(profile: UserProfile): Boolean =
        profile.age in minAge..maxAge &&
            (region == null || profile.region == region) &&
            (!verifiedOnly || profile.selfieVerified) &&
            (timelines.isEmpty() || profile.marriageTimeline in timelines) &&
            (languages.isEmpty() || profile.languages.any { it in languages })

    val isDefault: Boolean get() = this == DiscoveryPrefs()
}

/** Today's set: [total] people were picked, [profiles] are the ones not yet liked or passed. */
data class DailySet(val total: Int, val profiles: List<UserProfile>)

data class LikeResult(val matched: Boolean, val matchId: String?)

data class MatchSummary(
    val matchId: String,
    val matchedAt: String,
    val user: UserProfile,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val unreadCount: Int = 0,
    /** Set while nobody has written yet: the match disappears at this time (7 days after matching). */
    val expiresAt: String? = null,
    /** One of the two has a chaperone who can read the chat. */
    val chaperoned: Boolean = false
)

/** Discovery on the server. An interface so the Discover logic can be tested with a fake. */
interface DiscoveryApi {
    suspend fun getDailySet(): DailySet
    suspend fun like(toUserId: String, targetType: String?, targetId: String?, comment: String?): LikeResult
    suspend fun pass(toUserId: String)
    suspend fun getMatches(): List<MatchSummary>
    suspend fun receivedLikesCount(): Int
    suspend fun loadPrefs(): DiscoveryPrefs
    suspend fun savePrefs(prefs: DiscoveryPrefs)
    suspend fun unmatch(matchId: String)
}

@Serializable
private data class DailySetDto(val total: Int = 0, val profiles: List<CardDto> = emptyList())

@Serializable
private data class CardDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val age: Int = 0,
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
    @SerialName("selfie_verified") val selfieVerified: Boolean = false,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    val active: String? = null,
    @SerialName("photos_blurred") val photosBlurred: Boolean = false,
    val chaperoned: Boolean = false,
    val photos: List<CardPhotoDto> = emptyList(),
    val prompts: List<CardPromptDto> = emptyList()
)

@Serializable
private data class CardPhotoDto(val id: String, val path: String)

@Serializable
private data class CardPromptDto(val id: String, val key: String, val answer: String)

@Serializable
private data class LikeResultDto(val matched: Boolean = false, @SerialName("match_id") val matchId: String? = null)

@Serializable
private data class MatchDto(
    @SerialName("match_id") val matchId: String,
    @SerialName("matched_at") val matchedAt: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    val age: Int = 0,
    val city: String? = null,
    val photo: String? = null,
    @SerialName("selfie_verified") val selfieVerified: Boolean = false,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    val active: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val chaperoned: Boolean = false,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    val unread: Int = 0
)

@Serializable
private data class PrefsDto(
    @SerialName("pref_min_age") val minAge: Int = 18,
    @SerialName("pref_max_age") val maxAge: Int = 99,
    @SerialName("pref_region") val region: String? = null,
    @SerialName("pref_verified_only") val verifiedOnly: Boolean = false,
    @SerialName("pref_timelines") val timelines: List<String> = emptyList(),
    @SerialName("pref_languages") val languages: List<String> = emptyList()
)

class SupabaseDiscoveryService(
    private val client: SupabaseClient,
    private val photoLinks: PhotoUrlSigner,
    private val currentUserId: () -> String
) : DiscoveryApi {

    override suspend fun getDailySet(): DailySet {
        val dto = client.postgrest.rpc("get_daily_set").decodeAs<DailySetDto>()
        photoLinks.urls(dto.profiles.flatMap { c -> c.photos.map { c.shownPath(it.path) } })  // sign all photos in one request
        return DailySet(total = dto.total, profiles = dto.profiles.map { it.toUserProfile() })
    }

    override suspend fun like(toUserId: String, targetType: String?, targetId: String?, comment: String?): LikeResult {
        val result = client.postgrest.rpc("send_like", buildJsonObject {
            put("p_to", toUserId)
            put("p_target_type", targetType?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_target_id", targetId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("p_comment", comment?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }).decodeAs<LikeResultDto>()
        return LikeResult(result.matched, result.matchId)
    }

    override suspend fun pass(toUserId: String) {
        try {
            client.from("passes").insert(buildJsonObject {
                put("from_user", currentUserId())
                put("to_user", toUserId)
            })
        } catch (e: Throwable) {
            if (e.message?.contains("duplicate key") != true) throw e
        }
    }

    override suspend fun getMatches(): List<MatchSummary> {
        val rows = client.postgrest.rpc("get_my_matches").decodeAs<List<MatchDto>>()
        // The match list and chat only show a small avatar: use the small copy when it exists.
        val paths = rows.mapNotNull { it.photo }
        photoLinks.urls(paths.map(ProfileService::thumbPathFor) + paths)
        return rows.map { m ->
            MatchSummary(
                matchId = m.matchId,
                matchedAt = m.matchedAt,
                user = UserProfile(
                    id = m.userId,
                    displayName = m.displayName,
                    age = m.age,
                    city = m.city.orEmpty(),
                    photos = listOfNotNull(m.photo?.let { photoLinks.url(ProfileService.thumbPathFor(it)) ?: photoLinks.url(it) }),
                    selfieVerified = m.selfieVerified,
                    phoneVerified = m.phoneVerified,
                    activity = m.active.orEmpty(),
                    languages = emptyList(),
                    interests = emptyList()
                ),
                lastMessage = m.lastMessage,
                lastMessageAt = m.lastMessageAt,
                unreadCount = m.unread,
                expiresAt = m.expiresAt,
                chaperoned = m.chaperoned
            )
        }
    }

    override suspend fun receivedLikesCount(): Int =
        client.postgrest.rpc("received_likes_count").decodeAs<Int>()

    override suspend fun loadPrefs(): DiscoveryPrefs {
        val dto = client.from("profiles")
            .select(columns = Columns.list(
                "pref_min_age", "pref_max_age", "pref_region", "pref_verified_only", "pref_timelines", "pref_languages"
            )) { filter { eq("id", currentUserId()) } }
            .decodeList<PrefsDto>()
            .firstOrNull() ?: return DiscoveryPrefs()
        return DiscoveryPrefs(dto.minAge, dto.maxAge, dto.region, dto.verifiedOnly, dto.timelines.toSet(), dto.languages.toSet())
    }

    override suspend fun savePrefs(prefs: DiscoveryPrefs) {
        client.from("profiles").update(buildJsonObject {
            put("pref_min_age", prefs.minAge)
            put("pref_max_age", prefs.maxAge)
            put("pref_region", prefs.region?.let { JsonPrimitive(it) } ?: JsonNull)
            put("pref_verified_only", prefs.verifiedOnly)
            put("pref_timelines", JsonArray(prefs.timelines.sorted().map { JsonPrimitive(it) }))
            put("pref_languages", JsonArray(prefs.languages.sorted().map { JsonPrimitive(it) }))
        }) { filter { eq("id", currentUserId()) } }
    }

    override suspend fun unmatch(matchId: String) {
        client.from("matches").delete { filter { eq("id", matchId) } }
    }

    private suspend fun CardDto.toUserProfile(): UserProfile {
        // Keep ids and links aligned; a photo the server refuses to sign is left out of both.
        val signed = photos.mapNotNull { ph -> photoLinks.url(shownPath(ph.path))?.let { ph.id to it } }
        return UserProfile(
            id = id,
            displayName = displayName,
            age = age,
            city = city.orEmpty(),
            region = region.orEmpty(),
            bio = bio.orEmpty(),
            marriageTimeline = marriageTimeline.orEmpty(),
            wantsChildren = wantsChildren.orEmpty(),
            religion = religion.orEmpty(),
            smoking = smoking.orEmpty(),
            education = education.orEmpty(),
            occupation = occupation.orEmpty(),
            languages = languages,
            interests = emptyList(),
            heightCm = heightCm ?: 0,
            onboardingDone = true,
            photos = signed.map { it.second },
            photoIds = signed.map { it.first },
            prompts = prompts.map { p ->
                val question = PromptCatalog.find(p.key)
                ProfilePrompt(
                    promptKey = p.key,
                    questionEn = question?.questionEn ?: p.key,
                    questionDe = question?.questionDe ?: p.key,
                    answer = p.answer,
                    id = p.id
                )
            },
            selfieVerified = selfieVerified,
            phoneVerified = phoneVerified,
            activity = active.orEmpty(),
            photosBlurred = photosBlurred,
            chaperoned = chaperoned
        )
    }

    /** Members who blur their photos are shown with the blurred copies; the server refuses the clear ones. */
    private fun CardDto.shownPath(path: String) = if (photosBlurred) ProfileService.blurPathFor(path) else path
}
