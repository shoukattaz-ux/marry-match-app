package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.model.MatchTraits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MatchTraitsTest {

    private val allLabelKeys: List<String>
        get() = MatchTraits.ALL.flatMap { trait -> listOf(trait.labelKey) + trait.options.map(trait::optionKey) } +
            MatchTraits.INTERESTS.map { "interest_$it" }

    @Test
    fun everyQuestionAnswerAndInterestHasEnglishAndGermanText() {
        allLabelKeys.forEach { key ->
            assertNotEquals("EN $key", key, Localization.t(key, AppLanguage.EN))
            assertNotEquals("DE $key", key, Localization.t(key, AppLanguage.DE))
        }
    }

    @Test
    fun theServerAcceptsExactlyTheOptionsTheAppOffers() {
        val sql = File("../supabase/migrations/20261020164932_029_match_traits.sql").readText()
        MatchTraits.ALL.forEach { trait -> trait.options.forEach { assertTrue("${trait.key}=$it", "'$it'" in sql) } }
        MatchTraits.INTERESTS.forEach { assertTrue("interest $it", "'$it'" in sql) }
        assertTrue("cardinality(interests) <= ${MatchTraits.MAX_INTERESTS}" in sql)
    }

    @Test
    fun keysAreUnique() {
        assertEquals(MatchTraits.ALL.size, MatchTraits.ALL.map { it.key }.toSet().size)
        assertEquals(MatchTraits.INTERESTS.size, MatchTraits.INTERESTS.toSet().size)
    }

    @Test
    fun unknownAnswersAreDropped() {
        val known = MatchTraits.known(mapOf("drinking" to "never", "diet" to "halal", "pets" to null, "color" to "blue"))
        assertEquals(mapOf("drinking" to "never"), known)
    }

    @Test
    fun sharedInterestsKeepCatalogOrder() {
        assertEquals(listOf("cooking", "travel"), MatchTraits.shared(listOf("travel", "cooking", "yoga"), listOf("travel", "gym", "cooking")))
    }
}
