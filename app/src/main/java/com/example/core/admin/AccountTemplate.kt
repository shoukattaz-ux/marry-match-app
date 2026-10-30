package com.example.core.admin

import com.example.model.GermanCities
import com.example.model.MatchTraits
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * The text an admin pastes to create an account: one "Label: value" per line, in the order of
 * [TEMPLATE_EN]. Labels may be English or German; a line without a label takes the next field in
 * that order (so "email, password, name" on the first three lines also works), and lines after
 * "Bio:" continue the bio. Everything is checked here; the server checks it again.
 */
object AccountTemplate {

    /** Fields in template order: key, required, labels (lower case, without ":"). */
    enum class Field(val required: Boolean, vararg val labels: String) {
        EMAIL(true, "email", "e-mail", "mail"),
        PASSWORD(true, "password", "passwort", "kennwort"),
        NAME(true, "name", "vorname", "first name", "display name"),
        GENDER(true, "gender", "geschlecht"),
        BIRTH_DATE(true, "date of birth", "birth date", "birthday", "dob", "geburtsdatum", "geburtstag"),
        CITY(true, "city", "stadt", "wohnort", "ort"),
        LANGUAGE(false, "language", "app language", "sprache"),
        HEIGHT(false, "height", "größe", "groesse", "körpergröße", "koerpergroesse"),
        RELIGION(false, "religion", "glaube"),
        EDUCATION(false, "education", "bildung", "ausbildung", "abschluss"),
        OCCUPATION(false, "occupation", "job", "profession", "beruf"),
        MARRIAGE(false, "marriage", "marriage timeline", "heirat", "ehe", "heiraten"),
        CHILDREN(false, "children", "wants children", "kinder", "kinderwunsch"),
        SMOKING(false, "smoking", "rauchen", "raucher"),
        DRINKING(false, "drinking", "alcohol", "alkohol", "trinken"),
        EXERCISE(false, "exercise", "sport", "fitness"),
        DIET(false, "diet", "ernährung", "ernaehrung", "essen"),
        PETS(false, "pets", "haustiere", "tiere"),
        SLEEP(false, "sleep", "schlaf", "schlafen"),
        PERSONALITY(false, "personality", "persönlichkeit", "persoenlichkeit"),
        FAMILY(false, "family", "family closeness", "familie"),
        HOUSEHOLD(false, "household", "haushalt"),
        HOME(false, "home", "home after marriage", "wohnen", "zuhause"),
        RELOCATE(false, "relocate", "umziehen", "umzug"),
        LANGUAGES(false, "languages", "spoken languages", "sprachen"),
        INTERESTS(false, "interests", "hobbies", "interessen"),
        BIO(false, "bio", "about me", "about", "über mich", "ueber mich", "beschreibung")
    }

    const val MIN_PASSWORD = 8
    const val MAX_BIO = 500

    val TEMPLATE_EN = """
        Email: sara.beispiel@gmail.com
        Password: Sommer2026!
        Name: Sara
        Gender: woman
        Date of birth: 12.03.1995
        City: Berlin
        Language: de
        Height: 165
        Religion: Islam
        Education: Master
        Occupation: Teacher
        Marriage: within 1 year
        Children: yes
        Smoking: no
        Languages: German, English, Turkish
        Interests: cooking, travel, reading
        Bio: I am a calm, family-oriented person and looking for a serious partner for marriage.
    """.trimIndent()

    /** A problem with one field: [code] is a localization key, [value] what was written. */
    data class Issue(val field: Field, val code: String, val value: String = "")

    /** What was understood; [errors] stop the creation, [warnings] only leave something out. */
    data class Result(
        val email: String = "",
        val password: String = "",
        val name: String = "",
        val gender: String = "",
        val birthDate: LocalDate? = null,
        val city: String = "",
        val region: String = "",
        val language: String = "de",
        val heightCm: Int? = null,
        val religion: String = "",
        val education: String = "",
        val occupation: String = "",
        val marriageTimeline: String = "",
        val wantsChildren: String = "",
        /** Single-choice answers by profiles column (smoking, drinking, …). */
        val traits: Map<String, String> = emptyMap(),
        val languages: List<String> = emptyList(),
        val interests: List<String> = emptyList(),
        val bio: String = "",
        val errors: List<Issue> = emptyList(),
        val warnings: List<Issue> = emptyList()
    ) {
        val ok: Boolean get() = errors.isEmpty()
        val lookingFor: String get() = if (gender == "woman") "man" else if (gender == "man") "woman" else ""
        fun age(today: LocalDate): Int? = birthDate?.let { java.time.Period.between(it, today).years }
    }

    fun parse(text: String, today: LocalDate = LocalDate.now()): Result {
        val raw = linkedMapOf<Field, String>()
        var last: Field? = null
        for (line in text.lines().map { it.trim() }) {
            if (line.isEmpty()) continue
            val labelled = splitLabel(line)
            if (labelled != null) {
                val (field, value) = labelled
                raw[field] = value
                last = field
            } else if (last == Field.BIO) {
                raw[Field.BIO] = (raw[Field.BIO].orEmpty() + "\n" + line).trim()
            } else {
                // No label: the next field of the template order.
                val next = Field.entries.firstOrNull { it.ordinal > (last?.ordinal ?: -1) && it !in raw } ?: continue
                raw[next] = line
                last = next
            }
        }
        return validate(raw, today)
    }

    /** "Label: value" (also "Label - value" or "Label = value") with a known label, else null. */
    private fun splitLabel(line: String): Pair<Field, String>? {
        val m = Regex("""^([\p{L} \-]{2,30}?)\s*[:=]\s*(.*)$""").find(line)
            ?: Regex("""^([\p{L} ]{2,30}?)\s+-\s+(.*)$""").find(line)
            ?: return null
        val label = m.groupValues[1].trim().lowercase(Locale.ROOT)
        val field = Field.entries.firstOrNull { label in it.labels } ?: return null
        return field to m.groupValues[2].trim()
    }

    private fun validate(raw: Map<Field, String>, today: LocalDate): Result {
        val errors = mutableListOf<Issue>()
        val warnings = mutableListOf<Issue>()
        fun v(f: Field) = raw[f].orEmpty().trim()
        Field.entries.filter { it.required && v(it).isEmpty() }.forEach { errors += Issue(it, "create_err_missing") }

        val email = v(Field.EMAIL).lowercase(Locale.ROOT)
        if (email.isNotEmpty() && !EMAIL.matches(email)) errors += Issue(Field.EMAIL, "create_err_email", email)

        val password = v(Field.PASSWORD)
        if (password.isNotEmpty() && password.length < MIN_PASSWORD) errors += Issue(Field.PASSWORD, "create_err_password")

        val name = v(Field.NAME)
        if (name.length > 40) errors += Issue(Field.NAME, "create_err_name", name)

        val gender = when (v(Field.GENDER).lowercase(Locale.ROOT)) {
            "" -> ""
            "woman", "female", "f", "w", "frau", "weiblich" -> "woman"
            "man", "male", "m", "mann", "männlich", "maennlich" -> "man"
            else -> "?".also { errors += Issue(Field.GENDER, "create_err_gender", v(Field.GENDER)) }
        }.takeIf { it != "?" }.orEmpty()

        val birth = v(Field.BIRTH_DATE).takeIf { it.isNotEmpty() }?.let { text ->
            parseDate(text) ?: null.also { errors += Issue(Field.BIRTH_DATE, "create_err_date", text) }
        }
        if (birth != null) {
            val age = java.time.Period.between(birth, today).years
            if (birth.isAfter(today) || age > 100) errors += Issue(Field.BIRTH_DATE, "create_err_date", v(Field.BIRTH_DATE))
            else if (age < 18) errors += Issue(Field.BIRTH_DATE, "create_err_underage", v(Field.BIRTH_DATE))
        }

        val city = v(Field.CITY).takeIf { it.isNotEmpty() }?.let { text ->
            findCity(text) ?: null.also { errors += Issue(Field.CITY, "create_err_city", text) }
        }

        val language = when (v(Field.LANGUAGE).lowercase(Locale.ROOT)) {
            "", "de", "deutsch", "german", "ger" -> "de"
            "en", "english", "englisch", "eng" -> "en"
            else -> "de".also { warnings += Issue(Field.LANGUAGE, "create_warn_language", v(Field.LANGUAGE)) }
        }

        val height = v(Field.HEIGHT).takeIf { it.isNotEmpty() }?.let { text ->
            parseHeight(text) ?: null.also { errors += Issue(Field.HEIGHT, "create_err_height", text) }
        }

        fun choice(field: Field, words: Map<String, String>): String =
            v(field).takeIf { it.isNotEmpty() }?.let { text ->
                words[normalize(text)] ?: "".also { errors += Issue(field, "create_err_choice", text) }
            }.orEmpty()

        val marriage = choice(Field.MARRIAGE, MARRIAGE)
        val children = choice(Field.CHILDREN, CHILDREN)
        val traits = buildMap {
            TRAIT_FIELDS.forEach { (field, column) ->
                choice(field, TRAIT_WORDS.getValue(column)).takeIf { it.isNotEmpty() }?.let { put(column, it) }
            }
        }

        val languages = list(v(Field.LANGUAGES)).mapNotNull { word ->
            LANGUAGE_NAMES[normalize(word)] ?: null.also { warnings += Issue(Field.LANGUAGES, "create_warn_unknown", word) }
        }.distinct()
        val interests = list(v(Field.INTERESTS)).mapNotNull { word ->
            INTEREST_WORDS[normalize(word)] ?: null.also { warnings += Issue(Field.INTERESTS, "create_warn_unknown", word) }
        }.distinct()
        if (interests.size > MatchTraits.MAX_INTERESTS) errors += Issue(Field.INTERESTS, "create_err_interests")

        val bio = v(Field.BIO)
        if (bio.length > MAX_BIO) errors += Issue(Field.BIO, "create_err_bio")
        val religion = v(Field.RELIGION)
        if (religion.length > 60) errors += Issue(Field.RELIGION, "create_err_long")
        val education = v(Field.EDUCATION)
        val occupation = v(Field.OCCUPATION)
        if (education.length > 80) errors += Issue(Field.EDUCATION, "create_err_long")
        if (occupation.length > 80) errors += Issue(Field.OCCUPATION, "create_err_long")

        return Result(
            email = email, password = password, name = name, gender = gender, birthDate = birth,
            city = city?.name.orEmpty(), region = city?.state.orEmpty(), language = language, heightCm = height,
            religion = religion, education = education, occupation = occupation,
            marriageTimeline = marriage, wantsChildren = children, traits = traits,
            languages = languages, interests = interests, bio = bio,
            errors = errors.distinctBy { it.field }, warnings = warnings
        )
    }

    private val EMAIL = Regex("""^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$""")

    private val DATE_FORMATS = listOf("d.M.uuuu", "uuuu-M-d", "d/M/uuuu", "d-M-uuuu")
        .map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }

    fun parseDate(text: String): LocalDate? = DATE_FORMATS.firstNotNullOfOrNull { f ->
        runCatching { LocalDate.parse(text.trim(), f) }.getOrNull()
    }

    /** "165", "165 cm", "1,65", "1.65 m". */
    fun parseHeight(text: String): Int? {
        val t = text.lowercase(Locale.ROOT).replace("cm", "").replace("m", "").replace(',', '.').trim()
        val n = t.toDoubleOrNull() ?: return null
        val cm = if (n < 3) (n * 100).toInt() else n.toInt()
        return cm.takeIf { it in 120..230 }
    }

    private val CITY_ALIASES = mapOf(
        "munich" to "München", "muenchen" to "München", "munchen" to "München", "cologne" to "Köln", "koeln" to "Köln",
        "koln" to "Köln", "frankfurt" to "Frankfurt am Main", "nuremberg" to "Nürnberg", "nuernberg" to "Nürnberg",
        "duesseldorf" to "Düsseldorf", "dusseldorf" to "Düsseldorf", "hanover" to "Hannover", "muenster" to "Münster"
    )

    fun findCity(text: String): com.example.model.GermanCity? {
        val wanted = text.trim().lowercase(Locale.GERMAN)
        val name = CITY_ALIASES[wanted] ?: text.trim()
        return GermanCities.list.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private fun list(text: String): List<String> = text.split(',', ';', '/').map { it.trim() }.filter { it.isNotEmpty() }

    private fun normalize(text: String) = text.trim().lowercase(Locale.ROOT).replace('–', '-').replace(Regex("\\s+"), " ")

    private val MARRIAGE = mapOf(
        "within 1 year" to "within_1y", "within a year" to "within_1y", "1 year" to "within_1y", "1 jahr" to "within_1y",
        "innerhalb 1 jahr" to "within_1y", "innerhalb eines jahres" to "within_1y", "in 1 jahr" to "within_1y", "within_1y" to "within_1y",
        "1-2 years" to "1_2y", "1 to 2 years" to "1_2y", "1-2 jahre" to "1_2y", "1 bis 2 jahre" to "1_2y", "in 1-2 jahren" to "1_2y", "1_2y" to "1_2y",
        "2-5 years" to "2_5y", "2 to 5 years" to "2_5y", "2-5 jahre" to "2_5y", "2 bis 5 jahre" to "2_5y", "in 2-5 jahren" to "2_5y", "2_5y" to "2_5y",
        "open" to "open", "offen" to "open", "not sure" to "open", "unsure" to "open", "unsicher" to "open"
    )
    private val CHILDREN = mapOf(
        "yes" to "yes", "ja" to "yes", "no" to "no", "nein" to "no",
        "open" to "open", "maybe" to "open", "offen" to "open", "vielleicht" to "open"
    )

    private val TRAIT_FIELDS = listOf(
        Field.SMOKING to "smoking", Field.DRINKING to "drinking", Field.EXERCISE to "exercise", Field.DIET to "diet",
        Field.PETS to "pets", Field.SLEEP to "sleep", Field.PERSONALITY to "personality", Field.FAMILY to "family_closeness",
        Field.HOUSEHOLD to "household", Field.HOME to "home_after_marriage", Field.RELOCATE to "relocate"
    )

    /** For each trait column: words (EN/DE) to the stored option; the option names themselves also work. */
    private val TRAIT_WORDS: Map<String, Map<String, String>> = run {
        val extra = mapOf(
            "smoking" to mapOf("no" to "non_smoker", "nein" to "non_smoker", "never" to "non_smoker", "nie" to "non_smoker",
                "non-smoker" to "non_smoker", "nichtraucher" to "non_smoker", "sometimes" to "occasionally", "gelegentlich" to "occasionally",
                "manchmal" to "occasionally", "yes" to "smoker", "ja" to "smoker", "raucher" to "smoker"),
            "drinking" to mapOf("no" to "never", "nein" to "never", "nie" to "never", "socially" to "socially", "gesellig" to "socially",
                "sometimes" to "socially", "manchmal" to "socially", "yes" to "regularly", "regelmäßig" to "regularly", "often" to "regularly"),
            "exercise" to mapOf("selten" to "rarely", "manchmal" to "sometimes", "oft" to "often", "häufig" to "often", "täglich" to "daily", "every day" to "daily"),
            "diet" to mapOf("everything" to "anything", "alles" to "anything", "vegetarisch" to "vegetarian", "vegan" to "vegan", "pescetarisch" to "pescatarian"),
            "pets" to mapOf("yes" to "have_pets", "ja" to "have_pets", "habe haustiere" to "have_pets", "like" to "like_pets", "mag tiere" to "like_pets",
                "no" to "no_pets", "nein" to "no_pets", "keine" to "no_pets", "allergisch" to "allergic"),
            "sleep" to mapOf("early" to "early_bird", "frühaufsteher" to "early_bird", "late" to "night_owl", "nachteule" to "night_owl", "flexibel" to "flexible"),
            "personality" to mapOf("introvertiert" to "introvert", "ambivertiert" to "ambivert", "extrovertiert" to "extrovert", "in between" to "ambivert"),
            "family_closeness" to mapOf("very close" to "very_close", "sehr eng" to "very_close", "eng" to "close", "unabhängig" to "independent"),
            "household" to mapOf("traditionell" to "traditional", "geteilt" to "shared", "gemeinsam" to "shared", "flexibel" to "flexible"),
            "home_after_marriage" to mapOf("stadt" to "city", "land" to "countryside", "country" to "countryside", "flexibel" to "flexible"),
            "relocate" to mapOf("ja" to "yes", "vielleicht" to "maybe", "nein" to "no")
        )
        MatchTraits.ALL.associate { trait ->
            trait.key to (trait.options.associateBy { it } + trait.options.associateBy { it.replace('_', ' ') } + extra[trait.key].orEmpty())
        }
    }

    /** Spoken languages: the names the profile uses (English), also written in German. */
    private val LANGUAGE_NAMES: Map<String, String> = run {
        val german = mapOf("Deutsch" to "German", "Englisch" to "English", "Türkisch" to "Turkish", "Arabisch" to "Arabic",
            "Polnisch" to "Polish", "Russisch" to "Russian", "Französisch" to "French", "Italienisch" to "Italian", "Spanisch" to "Spanish")
        val names = listOf("German", "English", "Turkish", "Arabic", "Polish", "Russian", "French", "Italian", "Spanish")
        names.associateBy { it.lowercase(Locale.ROOT) } + german.mapKeys { it.key.lowercase(Locale.ROOT) }
    }

    private val INTEREST_WORDS: Map<String, String> = run {
        val german = mapOf("kochen" to "cooking", "backen" to "baking", "reisen" to "travel", "wandern" to "hiking", "natur" to "nature",
            "radfahren" to "cycling", "fahrrad" to "cycling", "laufen" to "running", "joggen" to "running", "fitness" to "gym",
            "fitnessstudio" to "gym", "fußball" to "football", "fussball" to "football", "schwimmen" to "swimming", "yoga" to "yoga",
            "tanzen" to "dancing", "musik" to "music", "konzerte" to "concerts", "lesen" to "reading", "bücher" to "reading",
            "schreiben" to "writing", "kunst" to "art", "fotografie" to "photography", "filme" to "movies", "kino" to "movies",
            "serien" to "series", "gaming" to "gaming", "videospiele" to "gaming", "technik" to "tech", "autos" to "cars",
            "garten" to "gardening", "gärtnern" to "gardening", "ehrenamt" to "volunteering", "familie" to "family_time",
            "familienzeit" to "family_time", "brettspiele" to "board_games", "kaffee" to "coffee", "mode" to "fashion",
            "sprachen lernen" to "learning_languages", "tiere" to "animals", "theater" to "theatre", "museen" to "museums",
            "movie" to "movies", "films" to "movies", "books" to "reading", "family" to "family_time", "languages" to "learning_languages",
            "fitness" to "gym", "sport" to "gym", "theater" to "theatre", "museum" to "museums", "concert" to "concerts")
        MatchTraits.INTERESTS.associateBy { it } + MatchTraits.INTERESTS.associateBy { it.replace('_', ' ') } + german
    }
}
