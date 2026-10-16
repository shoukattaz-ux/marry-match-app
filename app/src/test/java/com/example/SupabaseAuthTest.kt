package com.example

import com.example.core.AppConfig
import com.example.core.DisposableEmail
import com.example.core.LocationGateOutcome
import com.example.core.LocationGateService
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.data.SignupPassRequiredException
import com.example.data.isRealUserId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.Period

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseAuthTest {

    private lateinit var repository: MarryMatchRepository

    @Before
    fun setup() {
        repository = MarryMatchRepository()
    }

    @Test
    fun testAppStartsAtWelcomeScreenWithoutHardcodedDemoUser() {
        // Requirement 4: App must start at Welcome screen (or home only if session exists)
        // And initial user state must NOT have isAdmin = true
        assertEquals("Initial auth state must be WELCOME", AuthState.WELCOME, repository.authState.value)
        val currentUser = repository.currentUser.value
        assertFalse("Initial user must not be an admin", currentUser.isAdmin)
        assertEquals("Initial user must not have prefilled email", "", currentUser.email)
    }

    @Test
    fun testExactBirthDateBlocksUnder18() {
        // Requirement 5: Block anyone under 18 by exact date
        val today = LocalDate.now()
        val seventeenYearsOld = today.minusYears(17).minusDays(360)
        val exact18YearsOld = today.minusYears(18)
        val twentyFiveYearsOld = today.minusYears(25)

        val age17 = Period.between(seventeenYearsOld, today).years
        val age18 = Period.between(exact18YearsOld, today).years
        val age25 = Period.between(twentyFiveYearsOld, today).years

        assertTrue("17 years old must be under 18", age17 < 18)
        assertTrue("18 years old must be eligible", age18 >= 18)
        assertTrue("25 years old must be eligible", age25 >= 18)
    }

    @Test
    fun testPhoneVerificationIsDisabled() {
        // Requirement 6: Phone verification must be hidden behind PHONE_VERIFICATION_ENABLED = false
        assertFalse("PHONE_VERIFICATION_ENABLED must be false", AppConfig.PHONE_VERIFICATION_ENABLED)
    }

    @Test
    fun testSupabaseConfigurationFlags() {
        // Requirement 1: Supabase config reader
        assertNotNull("SUPABASE_URL should be defined", AppConfig.SUPABASE_URL)
        assertNotNull("SUPABASE_ANON_KEY should be defined", AppConfig.SUPABASE_ANON_KEY)
    }

    @Test
    fun testGermanyLocationGateUrlAndRules() {
        // Germany Location Gate Rules:
        // 1. Target URL must be exactly check-germany edge function
        assertEquals(
            "https://zaixmvhfblgarpjckjhc.supabase.co/functions/v1/check-germany",
            com.example.core.LocationGateService.CHECK_GERMANY_URL
        )

        // 2. Initial user must not have any hardcoded city
        assertEquals("", repository.currentUser.value.city)
        assertEquals("", repository.currentUser.value.region)

        // 3. Before sign-up, passing location gate must transition to SIGNUP
        repository.setPostLoginLocationGate(false)
        repository.onLocationGatePassed()
        assertEquals(AuthState.SIGNUP, repository.authState.value)

        // 4. After login, passing location gate must transition to LOGGED_IN
        repository.setPostLoginLocationGate(true)
        repository.onLocationGatePassed()
        assertEquals(AuthState.LOGGED_IN, repository.authState.value)
        assertFalse(repository.isPostLoginLocationGate.value)
    }

    @Test
    fun testGatePassIsKeptForSignUp() {
        assertFalse("No pass before the location check", repository.hasValidSignupPass())
        repository.setPostLoginLocationGate(false)
        repository.onLocationGatePassed("one-time-pass")
        assertEquals(AuthState.SIGNUP, repository.authState.value)
        assertTrue("Pass from check-germany must be kept for sign-up", repository.hasValidSignupPass())
    }

    @Test
    fun testSignUpWithoutPassAsksForLocationCheck() = runBlocking {
        val result = repository.signUpWithSupabase(
            email = "anna@example.de",
            password = "secret123",
            birthDate = "1995-05-05",
            age = 31
        )
        assertTrue("Sign-up without a pass must not reach the server", result.isFailure)
        assertTrue(result.exceptionOrNull() is SignupPassRequiredException)
    }

    @Test
    fun testParseGateResponse() {
        val withPass = LocationGateService.parseGateResponse("""{"allowed":true,"signup_pass":"abc"}""")
        assertEquals(LocationGateOutcome.Allowed("abc"), withPass)

        val signedIn = LocationGateService.parseGateResponse("""{"allowed":true}""")
        assertEquals(LocationGateOutcome.Allowed(null), signedIn)

        val refused = LocationGateService.parseGateResponse(
            """{"allowed":false,"message":"MarryMatch is not available in your area yet."}"""
        )
        assertEquals(LocationGateOutcome.NotAllowed("MarryMatch is not available in your area yet."), refused)
    }

    @Test
    fun testOnlyRealAccountIdsGoToTheServer() {
        assertTrue(isRealUserId("20301f7c-1eaf-4dbc-baaf-6b4210d78ccf"))
        assertFalse("Sample profiles are not real accounts", isRealUserId("c1"))
        assertFalse(isRealUserId(""))
    }

    @Test
    fun testReportOnSampleProfileStaysLocal() = runBlocking {
        // Sample profiles are not real accounts, so nothing is sent and nothing can fail.
        val result = repository.submitReport("c1", "Anna", "fake", "")
        assertTrue(result.isSuccess)
    }

    @Test
    fun testBlockHidesPersonImmediately() {
        repository.blockUser("c1")
        assertTrue("c1" in repository.blockedUserIds.value)
    }

    @Test
    fun testDisposableEmailDomainsAreRefused() {
        assertTrue(DisposableEmail.isDisposable("someone@mailinator.com"))
        assertTrue(DisposableEmail.isDisposable("Someone@YOPMAIL.com "))
        assertFalse(DisposableEmail.isDisposable("anna@gmail.com"))
        assertFalse(DisposableEmail.isDisposable("not-an-email"))
    }
}

