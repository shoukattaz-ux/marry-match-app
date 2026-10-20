package com.example.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfilePrompt(
    val promptKey: String,
    val questionEn: String,
    val questionDe: String,
    val answer: String,
    val id: String = ""  // database id, used to like this exact prompt
)

@Serializable
data class UserProfile(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val birthDate: String = "", // YYYY-MM-DD exact date
    val birthYear: Int = 1998,
    val age: Int = 0,
    val gender: String = "man", // 'woman' | 'man'
    val lookingFor: String = "woman", // 'woman' | 'man'
    val city: String = "",
    val region: String = "", // Bundesland
    val bio: String = "",
    val marriageTimeline: String = "1_2y", // 'within_1y' | '1_2y' | '2_5y' | 'open'
    val wantsChildren: String = "yes", // 'yes' | 'no' | 'open'
    val religion: String = "", // sensitive optional data
    val smoking: String = "",
    val education: String = "",
    val occupation: String = "",
    val languages: List<String> = listOf("German", "English"),
    /** Keys from [MatchTraits.INTERESTS]. */
    val interests: List<String> = emptyList(),
    /** Habit and value answers by [Trait.key], only offered options (see [MatchTraits]). */
    val traits: Map<String, String> = emptyMap(),
    val heightCm: Int = 175,
    val onboardingDone: Boolean = false,
    val photos: List<String> = emptyList(), // image urls
    val photoIds: List<String> = emptyList(), // database ids of [photos], same order
    val prompts: List<ProfilePrompt> = emptyList(),
    val selfieVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val isAdmin: Boolean = false,
    val isBanned: Boolean = false,
    val countryOk: Boolean = true,
    val respectScore: Int = 100,
    /** "today", "week" or "" (unknown or hidden); never an exact time. */
    val activity: String = "",
    /** [photos] are blurred copies: this member shows clear photos only to matches. */
    val photosBlurred: Boolean = false,
    /** A chaperone (family member) reads this member's chats with matches. */
    val chaperoned: Boolean = false,
    /** How the round avatar shows the main photo (set with "Adjust photo"). */
    val mainPhotoFocus: PhotoFocus = PhotoFocus()
)

/**
 * A question users can answer on their profile; [key] is what the database stores. [hintEn] /
 * [hintDe] is an example answer shown in the empty field, so it is clear what to write.
 */
data class PromptQuestion(
    val key: String,
    val questionEn: String,
    val questionDe: String,
    val hintEn: String = "",
    val hintDe: String = ""
)

object PromptCatalog {
    /** Every profile answers this many different questions. */
    const val REQUIRED = 6
    /** Shortest answer that still says something. */
    const val MIN_ANSWER_CHARS = 10
    const val MAX_ANSWER_CHARS = 300

    val all = listOf(
        PromptQuestion(
            "ideal_sunday",
            "How do you like to spend a free Sunday?",
            "Wie verbringen Sie am liebsten einen freien Sonntag?",
            "e.g. Breakfast with my family, a long walk by the river and cooking dinner together.",
            "z. B. Frühstück mit der Familie, ein langer Spaziergang am Fluss und gemeinsam kochen."
        ),
        PromptQuestion(
            "family_means",
            "What role does your family play in your daily life?",
            "Welche Rolle spielt Ihre Familie in Ihrem Alltag?",
            "e.g. I call my parents every day and we all meet for dinner on Fridays.",
            "z. B. Ich telefoniere täglich mit meinen Eltern und freitags essen wir alle zusammen."
        ),
        PromptQuestion(
            "non_negotiable",
            "Which value is non-negotiable for you in a marriage, and why?",
            "Welcher Wert ist für Sie in einer Ehe nicht verhandelbar, und warum?",
            "e.g. Honesty. Even when the truth is hard, I want us to always tell each other.",
            "z. B. Ehrlichkeit. Auch wenn die Wahrheit schwer ist, möchte ich, dass wir sie uns sagen."
        ),
        PromptQuestion(
            "lasting_marriage",
            "What do you think makes a marriage last for a lifetime?",
            "Was macht eine Ehe Ihrer Meinung nach ein Leben lang stark?",
            "e.g. Respect, patience and talking openly, even about small things.",
            "z. B. Respekt, Geduld und offen miteinander reden, auch über Kleinigkeiten."
        ),
        PromptQuestion(
            "disagreements",
            "What do you do when you disagree with someone you love?",
            "Was tun Sie, wenn Sie mit einem geliebten Menschen nicht einer Meinung sind?",
            "e.g. I take a moment to calm down, then we sit together and talk it through.",
            "z. B. Ich beruhige mich kurz, dann setzen wir uns zusammen und reden darüber."
        ),
        PromptQuestion(
            "favorite_tradition",
            "Which tradition would you like to continue in your own family?",
            "Welche Tradition möchten Sie in Ihrer eigenen Familie weiterführen?",
            "e.g. Big family dinners on holidays where everyone cooks one dish.",
            "z. B. Große Familienessen an Feiertagen, bei denen jeder ein Gericht kocht."
        ),
        PromptQuestion(
            "five_years",
            "Where do you see yourself in five years?",
            "Wo sehen Sie sich in fünf Jahren?",
            "e.g. Married, living in our own flat near my family, maybe with our first child.",
            "z. B. Verheiratet, in einer eigenen Wohnung nahe meiner Familie, vielleicht mit unserem ersten Kind."
        ),
        PromptQuestion(
            "partner_qualities",
            "Which three qualities matter most to you in a partner?",
            "Welche drei Eigenschaften sind Ihnen bei einem Partner am wichtigsten?",
            "e.g. Kind, reliable and with a good sense of humour.",
            "z. B. Herzlich, zuverlässig und mit viel Humor."
        ),
        PromptQuestion(
            "friends_describe",
            "How would your closest friends describe you in three words?",
            "Wie würden Ihre engsten Freunde Sie mit drei Wörtern beschreiben?",
            "e.g. Loyal, funny and always on time.",
            "z. B. Loyal, lustig und immer pünktlich."
        ),
        PromptQuestion(
            "weekday_evening",
            "What does a normal weekday evening look like for you?",
            "Wie sieht ein normaler Abend unter der Woche bei Ihnen aus?",
            "e.g. Gym after work, then cooking at home and a phone call with my sister.",
            "z. B. Nach der Arbeit ins Fitnessstudio, dann zu Hause kochen und mit meiner Schwester telefonieren."
        ),
        PromptQuestion(
            "show_care",
            "How do you show someone that you care about them?",
            "Wie zeigen Sie einem Menschen, dass er Ihnen wichtig ist?",
            "e.g. Small surprises, cooking their favourite food and really listening.",
            "z. B. Kleine Überraschungen, das Lieblingsessen kochen und richtig zuhören."
        ),
        PromptQuestion(
            "proud_of",
            "What are you most proud of in your life so far?",
            "Worauf sind Sie in Ihrem Leben bisher am meisten stolz?",
            "e.g. Finishing my degree while working to support my family.",
            "z. B. Mein Studium abgeschlossen zu haben, während ich gearbeitet und meine Familie unterstützt habe."
        ),
        PromptQuestion(
            "teach_children",
            "What would you most like to teach your children?",
            "Was möchten Sie Ihren Kindern am meisten mitgeben?",
            "e.g. To respect everyone and never give up on their dreams.",
            "z. B. Jeden Menschen zu respektieren und ihre Träume nie aufzugeben."
        ),
        PromptQuestion(
            "money_together",
            "How should a married couple handle money, in your view?",
            "Wie sollte ein Ehepaar Ihrer Meinung nach mit Geld umgehen?",
            "e.g. Plan together, save for the future and decide big purchases as a team.",
            "z. B. Gemeinsam planen, für die Zukunft sparen und große Ausgaben zusammen entscheiden."
        ),
        PromptQuestion(
            "first_meeting",
            "What would be a perfect first meeting for you?",
            "Wie sähe für Sie ein perfektes erstes Treffen aus?",
            "e.g. Coffee in a quiet café with enough time to really talk.",
            "z. B. Ein Kaffee in einem ruhigen Café mit genug Zeit, um wirklich zu reden."
        ),
        PromptQuestion(
            "learning_now",
            "What are you learning or working towards right now?",
            "Was lernen Sie gerade oder worauf arbeiten Sie hin?",
            "e.g. Improving my Spanish and training for my first half marathon.",
            "z. B. Mein Spanisch verbessern und für meinen ersten Halbmarathon trainieren."
        ),
        PromptQuestion(
            "feels_like_home",
            "What makes a place feel like home to you?",
            "Wodurch fühlt sich ein Ort für Sie wie ein Zuhause an?",
            "e.g. The smell of home cooking, warm light and the people I love around me.",
            "z. B. Der Duft von Selbstgekochtem, warmes Licht und meine Liebsten um mich herum."
        ),
        PromptQuestion(
            "stress_relief",
            "What helps you relax after a stressful day?",
            "Was hilft Ihnen, nach einem stressigen Tag abzuschalten?",
            "e.g. A walk outside, some music and a cup of tea on the balcony.",
            "z. B. Ein Spaziergang, etwas Musik und eine Tasse Tee auf dem Balkon."
        )
    )

    fun find(key: String): PromptQuestion? = all.firstOrNull { it.key == key }

    /**
     * The questions shown in the editor: saved answers first (in their order), then unused
     * questions with empty answers until there are [REQUIRED].
     */
    fun fillUp(saved: List<Pair<String, String>>): List<Pair<String, String>> {
        val kept = saved.filter { find(it.first) != null }.distinctBy { it.first }.take(REQUIRED)
        val used = kept.map { it.first }.toSet()
        return kept + all.filter { it.key !in used }.take(REQUIRED - kept.size).map { it.key to "" }
    }
}
