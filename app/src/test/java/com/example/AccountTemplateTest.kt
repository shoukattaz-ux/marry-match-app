package com.example

import com.example.core.admin.AccountTemplate
import com.example.core.admin.AccountTemplate.Field
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTemplateTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun theTemplateItselfIsUnderstoodCompletely() {
        val r = AccountTemplate.parse(AccountTemplate.TEMPLATE_EN, today)
        assertTrue(r.errors.toString(), r.ok)
        assertEquals(emptyList<AccountTemplate.Issue>(), r.warnings)
        assertEquals("sara.beispiel@gmail.com", r.email)
        assertEquals("Sommer2026!", r.password)
        assertEquals("Sara", r.name)
        assertEquals("woman", r.gender)
        assertEquals("man", r.lookingFor)
        assertEquals(LocalDate.of(1995, 3, 12), r.birthDate)
        assertEquals(31, r.age(today))
        assertEquals("Berlin", r.city)
        assertEquals("Berlin", r.region)
        assertEquals("de", r.language)
        assertEquals(165, r.heightCm)
        assertEquals("within_1y", r.marriageTimeline)
        assertEquals("yes", r.wantsChildren)
        assertEquals(mapOf("smoking" to "non_smoker"), r.traits)
        assertEquals(listOf("German", "English", "Turkish"), r.languages)
        assertEquals(listOf("cooking", "travel", "reading"), r.interests)
    }

    @Test
    fun theUsersExampleWithAnEmptyReligion() {
        val r = AccountTemplate.parse(AccountTemplate.TEMPLATE_EN.replace("Religion: Islam", "Religion: "), today)
        assertTrue(r.ok)
        assertEquals("", r.religion)
    }

    @Test
    fun germanLabelsAndValues() {
        val r = AccountTemplate.parse(
            """
            E-Mail: Max.Mustermann@Web.de
            Passwort: Geheim123
            Vorname: Max
            Geschlecht: Mann
            Geburtsdatum: 1990-07-01
            Stadt: Muenchen
            Sprache: Englisch
            Größe: 1,82 m
            Heirat: 1 bis 2 Jahre
            Kinder: vielleicht
            Rauchen: gelegentlich
            Haustiere: allergisch
            Sprachen: Deutsch, Arabisch
            Interessen: Fußball, Kochen, Wandern
            Über mich: Ruhig und ehrlich.
            Ich suche eine Ehefrau.
            """.trimIndent(), today
        )
        assertTrue(r.errors.toString(), r.ok)
        assertEquals("max.mustermann@web.de", r.email)
        assertEquals("man", r.gender)
        assertEquals("woman", r.lookingFor)
        assertEquals("München", r.city)
        assertEquals("Bayern", r.region)
        assertEquals("en", r.language)
        assertEquals(182, r.heightCm)
        assertEquals("1_2y", r.marriageTimeline)
        assertEquals("open", r.wantsChildren)
        assertEquals(mapOf("smoking" to "occasionally", "pets" to "allergic"), r.traits)
        assertEquals(listOf("German", "Arabic"), r.languages)
        assertEquals(listOf("football", "cooking", "hiking"), r.interests)
        assertEquals("Ruhig und ehrlich.\nIch suche eine Ehefrau.", r.bio)
    }

    @Test
    fun withoutLabelsTheOrderCounts() {
        val r = AccountTemplate.parse("ali@example.com\nPass1234word\nAli\nman\n05.05.1992\nKöln", today)
        assertTrue(r.errors.toString(), r.ok)
        assertEquals("ali@example.com", r.email)
        assertEquals("Pass1234word", r.password)
        assertEquals("Ali", r.name)
        assertEquals("Köln", r.city)
    }

    @Test
    fun aPasswordWithAColonStaysWhole() {
        val r = AccountTemplate.parse(AccountTemplate.TEMPLATE_EN.replace("Password: Sommer2026!", "Password: ab:cd:1234"), today)
        assertEquals("ab:cd:1234", r.password)
    }

    @Test
    fun mistakesAreNamedAndStopTheCreation() {
        val r = AccountTemplate.parse(
            """
            Email: not-an-email
            Password: short
            Name: Lea
            Gender: other
            Date of birth: 31.02.2000
            City: Atlantis
            Height: 300
            Marriage: soon
            """.trimIndent(), today
        )
        assertFalse(r.ok)
        val byField = r.errors.associate { it.field to it.code }
        assertEquals("create_err_email", byField[Field.EMAIL])
        assertEquals("create_err_password", byField[Field.PASSWORD])
        assertEquals("create_err_gender", byField[Field.GENDER])
        assertEquals("create_err_date", byField[Field.BIRTH_DATE])
        assertEquals("create_err_city", byField[Field.CITY])
        assertEquals("create_err_height", byField[Field.HEIGHT])
        assertEquals("create_err_choice", byField[Field.MARRIAGE])
    }

    @Test
    fun under18AndMissingRequiredFields() {
        val r = AccountTemplate.parse("Email: kid@example.com\nDate of birth: 01.01.2010", today)
        val byField = r.errors.associate { it.field to it.code }
        assertEquals("create_err_underage", byField[Field.BIRTH_DATE])
        assertEquals("create_err_missing", byField[Field.PASSWORD])
        assertEquals("create_err_missing", byField[Field.NAME])
        assertEquals("create_err_missing", byField[Field.GENDER])
        assertEquals("create_err_missing", byField[Field.CITY])
    }

    @Test
    fun unknownInterestsAndLanguagesAreOnlyWarnings() {
        val r = AccountTemplate.parse(AccountTemplate.TEMPLATE_EN
            .replace("Interests: cooking, travel, reading", "Interests: cooking, knitting")
            .replace("Languages: German, English, Turkish", "Languages: German, Klingon"), today)
        assertTrue(r.ok)
        assertEquals(listOf("cooking"), r.interests)
        assertEquals(listOf("German"), r.languages)
        assertEquals(setOf("knitting", "Klingon"), r.warnings.map { it.value }.toSet())
    }
}
