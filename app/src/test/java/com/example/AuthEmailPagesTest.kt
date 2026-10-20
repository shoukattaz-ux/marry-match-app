package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The auth email templates and the pages their links open (docs/ on GitHub Pages). */
class AuthEmailPagesTest {

    private fun repoFile(path: String) = File("../$path").also { assertTrue("$path is missing", it.isFile) }

    @Test
    fun theVerifiedPageReadsBothOutcomesAndOpensTheApp() {
        val page = repoFile("docs/verified.html").readText()
        assertTrue(page.contains("error_code"))
        assertTrue(page.contains("history.replaceState"))  // the login token leaves the address bar
        assertTrue(page.contains("package=de.marrymatch.app"))
        assertTrue(page.contains("Your account has been verified") && page.contains("Ihr Konto wurde bestätigt"))
    }

    @Test
    fun theResetPageTalksOnlyToOurServerWithThePublishableKey() {
        val page = repoFile("docs/reset.html").readText()
        assertTrue(page.contains("https://zaixmvhfblgarpjckjhc.supabase.co"))
        assertTrue(page.contains("sb_publishable_"))
        assertTrue("never a secret key", !page.contains("sb_secret_") && !page.contains("service_role"))
        assertTrue(page.contains("/auth/v1/user"))
        assertTrue("no outside scripts", !page.contains("<script src"))
    }

    @Test
    fun theEmailTemplatesAreBrandedAndBilingual() {
        for (name in listOf("confirm_signup", "reset_password")) {
            val html = repoFile("supabase/templates/$name.html").readText()
            assertTrue(name, html.contains("{{ .ConfirmationURL }}"))
            assertTrue(name, html.contains("{{ if eq .Data.lang \"de\" }}") && html.contains("{{ else }}") && html.contains("{{ end }}"))
            assertTrue(name, html.contains("https://shoukattaz-ux.github.io/marry-match-app/logo.png"))
            assertTrue(name, !html.contains("Supabase"))
        }
        assertTrue(repoFile("docs/logo.png").length() in 5_000L..200_000L)
    }
}
