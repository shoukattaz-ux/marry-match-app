import com.example.core.*
import java.io.File

fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

fun body(text: String): String {
    val out = StringBuilder()
    var inList = false
    text.split("\n").forEach { line ->
        if (line.startsWith("• ")) {
            if (!inList) { out.append("<ul>\n"); inList = true }
            out.append("<li>").append(esc(line.removePrefix("• "))).append("</li>\n")
        } else {
            if (inList) { out.append("</ul>\n"); inList = false }
            if (line.isNotBlank()) out.append("<p>").append(esc(line)).append("</p>\n")
        }
    }
    if (inList) out.append("</ul>\n")
    return out.toString()
}

fun page(title: String, en: List<Pair<String, String>>, de: List<Pair<String, String>>, draft: Boolean) = buildString {
    append("<!doctype html>\n<html lang=\"de\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
    append("<title>MarryMatch – ").append(esc(title)).append("</title>\n")
    append("<style>body{font-family:system-ui,sans-serif;max-width:46rem;margin:0 auto;padding:1.5rem 1rem;line-height:1.55;color:#1d1b20;background:#fffbfe}h1{font-size:1.6rem}h2{font-size:1.1rem;margin-top:1.6rem}nav a{margin-right:1rem}.warn{background:#ffdad6;padding:.6rem .8rem;border-radius:8px}@media(prefers-color-scheme:dark){body{color:#e6e1e5;background:#1c1b1f}.warn{background:#5c1a12}a{color:#cfbcff}}</style>\n</head>\n<body>\n")
    append("<!-- Generated from app/src/main/java/com/example/core/LegalTexts.kt. Do not edit by hand; regenerate. -->\n")
    append("<nav><a href=\"#de\">Deutsch</a><a href=\"#en\">English</a><a href=\"index.html\">MarryMatch</a></nav>\n")
    if (draft) append("<p class=\"warn\">Entwurf – noch nicht anwaltlich geprüft. / Draft – not yet reviewed by a lawyer.</p>\n")
    for ((id, sections) in listOf("de" to de, "en" to en)) {
        append("<section id=\"").append(id).append("\" lang=\"").append(id).append("\">\n<h1>").append(esc(if (id == "de") titleDe(title) else title)).append("</h1>\n")
        sections.forEach { (h, b) -> append("<h2>").append(esc(h)).append("</h2>\n").append(body(b)) }
        append("</section>\n")
    }
    append("</body>\n</html>\n")
}

fun titleDe(en: String) = mapOf("Privacy Policy" to "Datenschutzerklärung", "Terms of Service" to "Nutzungsbedingungen",
    "Code of Conduct" to "Verhaltenskodex", "Impressum" to "Impressum")[en] ?: en

fun main(args: Array<String>) {
    val dir = File(args[0]); dir.mkdirs()
    val draft = !AppConfig.LEGAL_TEXTS_REVIEWED
    File(dir, "privacy.html").writeText(page("Privacy Policy", LegalTexts.privacy.sectionsEn, LegalTexts.privacy.sectionsDe, draft))
    File(dir, "terms.html").writeText(page("Terms of Service", LegalTexts.terms.sectionsEn, LegalTexts.terms.sectionsDe, draft))
    File(dir, "conduct.html").writeText(page("Code of Conduct", LegalTexts.conduct.sectionsEn, LegalTexts.conduct.sectionsDe, false))
    File(dir, "impressum.html").writeText(page("Impressum", LegalTexts.impressum(AppLanguage.EN), LegalTexts.impressum(AppLanguage.DE), false))
}
