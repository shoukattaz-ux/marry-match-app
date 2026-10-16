package com.example

import com.example.core.AppLanguage
import com.example.core.LegalTexts
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The public web pages in docs/ must say the same as the app (tools/legal-pages regenerates them). */
class LegalPagesTest {

    // Unit tests run in the app module, so docs/ is one level up.
    private fun page(name: String) = File("../docs/$name").readText()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun assertContainsAll(name: String, sections: List<Pair<String, String>>) {
        val html = page(name)
        sections.forEach { (heading, body) ->
            assertTrue("$name is missing \"$heading\"; regenerate docs/", html.contains(esc(heading)))
            body.split("\n").map { it.removePrefix("• ") }.filter { it.isNotBlank() }.forEach { line ->
                assertTrue("$name is out of date at \"${line.take(40)}…\"; regenerate docs/", html.contains(esc(line)))
            }
        }
    }

    @Test
    fun webPagesMatchTheAppTexts() {
        assertContainsAll("privacy.html", LegalTexts.privacy.sectionsEn + LegalTexts.privacy.sectionsDe)
        assertContainsAll("terms.html", LegalTexts.terms.sectionsEn + LegalTexts.terms.sectionsDe)
        assertContainsAll("conduct.html", LegalTexts.conduct.sectionsEn + LegalTexts.conduct.sectionsDe)
        assertContainsAll("impressum.html", LegalTexts.impressum(AppLanguage.EN) + LegalTexts.impressum(AppLanguage.DE))
    }
}
