package com.example

import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.MyCardState
import com.example.data.SignatureCardApi
import com.example.data.SignatureCardException
import com.example.model.SignatureCard
import com.example.model.SignatureCatalog
import com.example.ui.viewmodel.SignatureCardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Behaves like save_profile_card / get_my_profile_card of migration 043, in memory. */
private class FakeCardApi(var woman: Boolean = true, var verified: Boolean = true) : SignatureCardApi {
    // Migration 047: every selfie-verified member, men too.
    var card: SignatureCard? = null
    var saves = 0
    override suspend fun mine() = MyCardState(woman, verified, card)
    override suspend fun save(card: SignatureCard): MyCardState {
        if (!verified) throw SignatureCardException("card_needs_verification")
        SignatureCatalog.problem(card)?.let { throw SignatureCardException(it) }
        saves++
        this.card = card.copy(id = this.card?.id ?: "c1", lookingFor = card.lookingFor.trim())
        return mine()
    }
    override suspend fun delete() { card = null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureCardTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val good = SignatureCard(
        template = "champagne_pearl", lookingFor = "A kind man who keeps his word",
        reasons = listOf("marriage_soon", "family_first"), vibeEmoji = "☕", vibe = "Slow mornings, deep talks"
    )

    @Test
    fun contactDetailsAreRefused() {
        listOf(
            "Call me 0176 1234567", "+49 (176) 123-45-67", "write to anna@example.de", "www.anna.de",
            "find me on Instagram", "WhatsApp me", "my site anna.com", "follow @anna_berlin", "Telegram only"
        ).forEach { assertTrue(it, SignatureCatalog.hasContactInfo(it)) }
        listOf(
            "A kind, God-fearing man who keeps his word", "Between 28 and 35, family first", "رجل طيب يخاف الله",
            "Slow mornings, deep talks", "Born in 1994", "Signals of kindness matter"
        ).forEach { assertFalse(it, SignatureCatalog.hasContactInfo(it)) }
    }

    @Test
    fun theSameRulesAsTheServer() {
        assertNull(SignatureCatalog.problem(good))
        assertEquals("card_bad_template", SignatureCatalog.problem(good.copy(template = "gold")))
        assertEquals("card_looking_for_length", SignatureCatalog.problem(good.copy(lookingFor = "  ")))
        assertEquals("card_looking_for_length", SignatureCatalog.problem(good.copy(lookingFor = "x".repeat(121))))
        assertEquals("card_bad_reasons", SignatureCatalog.problem(good.copy(reasons = emptyList())))
        assertEquals("card_bad_reasons", SignatureCatalog.problem(good.copy(reasons = listOf("a", "b", "c", "d"))))
        assertEquals("card_bad_reasons", SignatureCatalog.problem(good.copy(reasons = listOf("money"))))
        assertEquals("card_vibe_length", SignatureCatalog.problem(good.copy(vibe = "x".repeat(41))))
        assertEquals("card_bad_emoji", SignatureCatalog.problem(good.copy(vibeEmoji = "💰")))
        assertEquals("card_contact_info", SignatureCatalog.problem(good.copy(vibe = "insta: anna")))
        assertNull(SignatureCatalog.problem(good.copy(vibe = null, vibeEmoji = null)))  // the vibe is optional
    }

    @Test
    fun friendshipFirstAndHonestConversationsAreGone() {
        assertEquals(10, SignatureCatalog.REASONS.size)
        assertNull(SignatureCatalog.reason("friendship_first"))
        assertNull(SignatureCatalog.reason("honest_talk"))
        assertEquals("card_bad_reasons", SignatureCatalog.problem(good.copy(reasons = listOf("honest_talk"))))
    }

    @Test
    fun arabicIsDetectedForRightToLeft() {
        assertTrue(SignatureCatalog.isArabicScript("رجل طيب"))
        assertFalse(SignatureCatalog.isArabicScript("Ein gütiger Mann"))
    }

    @Test
    fun aVerifiedWomanCreatesAndEditsHerCard() {
        val api = FakeCardApi()
        val vm = SignatureCardViewModel(api)
        assertTrue(vm.state.value.unlocked)
        assertNull(vm.state.value.saved)
        vm.setTemplate("rose_velvet")
        vm.setLookingFor("  A kind man who keeps his word  ")
        vm.toggleReason("marriage_soon")
        vm.toggleReason("family_first")
        vm.setVibeEmoji("🌙")
        vm.setVibe("Quiet evenings")
        vm.save()
        assertEquals("card_saved", vm.state.value.doneKey)
        assertEquals("rose_velvet", api.card?.template)
        assertEquals("A kind man who keeps his word", api.card?.lookingFor)
        assertFalse(vm.state.value.changed)
        // Editing keeps the same card (the id a like points at).
        vm.setTemplate("emerald_noir")
        assertTrue(vm.state.value.changed)
        vm.save()
        assertEquals("c1", api.card?.id)
        assertEquals(2, api.saves)
    }

    @Test
    fun atMostThreeReasonsAndLengthsAreCapped() {
        val vm = SignatureCardViewModel(FakeCardApi())
        listOf("marriage_soon", "family_first", "new_chapter", "shared_values").forEach(vm::toggleReason)
        assertEquals(3, vm.state.value.draft.reasons.size)
        assertEquals("card_max_reasons", vm.state.value.errorKey)
        vm.toggleReason("new_chapter")  // tapping again removes it
        assertEquals(listOf("marriage_soon", "family_first"), vm.state.value.draft.reasons)
        vm.setLookingFor("x".repeat(200))
        assertEquals(SignatureCatalog.MAX_LOOKING_FOR, vm.state.value.draft.lookingFor.length)
        vm.setVibe("y".repeat(100))
        assertEquals(SignatureCatalog.MAX_VIBE, vm.state.value.draft.vibe?.length)
        vm.setVibeEmoji("💰")
        assertNull(vm.state.value.draft.vibeEmoji)
    }

    @Test
    fun problemsAreShownBeforeSending() {
        val api = FakeCardApi()
        val vm = SignatureCardViewModel(api)
        vm.setLookingFor("Call me 0176 1234567")
        vm.toggleReason("serious_only")
        vm.save()
        assertEquals("card_contact_info", vm.state.value.errorKey)
        assertEquals(0, api.saves)
    }

    @Test
    fun lockedUntilVerifiedForWomenAndMen() {
        val unverified = SignatureCardViewModel(FakeCardApi(verified = false))
        assertTrue(unverified.state.value.woman)
        assertFalse(unverified.state.value.unlocked)
        // Men have the card too once verified, and are locked until then like everyone.
        val man = SignatureCardViewModel(FakeCardApi(woman = false))
        assertFalse(man.state.value.woman)
        assertTrue(man.state.value.unlocked)
        man.setLookingFor("A kind woman who keeps her word")
        man.toggleReason("marriage_soon")
        man.save()
        assertEquals("card_saved", man.state.value.doneKey)
        assertFalse(SignatureCardViewModel(FakeCardApi(woman = false, verified = false)).state.value.unlocked)
    }

    @Test
    fun verificationLostWhileEditing() {
        val api = FakeCardApi()
        val vm = SignatureCardViewModel(api)
        vm.setLookingFor("A kind man")
        vm.toggleReason("shared_faith")
        api.verified = false  // e.g. a new main photo reset it on another phone
        vm.save()
        assertEquals("card_needs_verification", vm.state.value.errorKey)
        assertFalse(vm.state.value.unlocked)
    }

    @Test
    fun removingTheCard() {
        val api = FakeCardApi().apply { card = good.copy(id = "c1") }
        val vm = SignatureCardViewModel(api)
        assertEquals("champagne_pearl", vm.state.value.draft.template)
        vm.delete()
        assertNull(api.card)
        assertNull(vm.state.value.saved)
        assertEquals("card_removed", vm.state.value.doneKey)
    }

    @Test
    fun everyTextInBothLanguages() {
        val keys = SignatureCatalog.TEMPLATES.map { "card_template_$it" } + listOf(
            "card_signature", "card_verified", "card_looking_for", "card_why_here", "card_my_vibe", "card_like",
            "card_like_label", "card_title", "card_saved", "card_removed", "card_max_reasons", "card_save_failed",
            "card_locked_body", "card_hidden_body", "card_entry_unlocked"
        ) + SignatureCardException.REFUSALS
        keys.forEach { key ->
            val en = Localization.t(key, AppLanguage.EN)
            val de = Localization.t(key, AppLanguage.DE)
            assertNotEquals(key, en)
            assertNotEquals(key, de)
        }
        SignatureCatalog.REASONS.forEach { assertNotEquals(it.en, it.de) }
    }
}
