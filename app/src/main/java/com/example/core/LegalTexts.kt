package com.example.core

/** One legal document as titled sections, in English and German. */
data class LegalDoc(val titleKey: String, val sectionsEn: List<Pair<String, String>>, val sectionsDe: List<Pair<String, String>>) {
    fun sections(lang: AppLanguage) = if (lang == AppLanguage.DE) sectionsDe else sectionsEn
}

/**
 * Legal texts shown in the app. They describe what the app really does (checked against the
 * database and Edge Functions). They are drafts: a lawyer must review them before release, and
 * the operator details in [AppConfig] must be filled in.
 */
object LegalTexts {

    private val operator: String
        get() = listOf(AppConfig.LEGAL_OPERATOR_NAME, AppConfig.LEGAL_ADDRESS, AppConfig.LEGAL_EMAIL).joinToString("\n")

    fun impressum(lang: AppLanguage): List<Pair<String, String>> = if (lang == AppLanguage.DE) listOf(
        "Angaben gemäß § 5 DDG" to listOf(AppConfig.LEGAL_OPERATOR_NAME, AppConfig.LEGAL_ADDRESS).joinToString("\n"),
        "Kontakt" to "E-Mail: ${AppConfig.LEGAL_EMAIL}\nTelefon: ${AppConfig.LEGAL_PHONE}",
        "Vertreten durch" to AppConfig.LEGAL_REPRESENTATIVE,
        "Registereintrag" to AppConfig.LEGAL_REGISTER,
        "Umsatzsteuer-ID" to AppConfig.LEGAL_VAT_ID,
        "Verantwortlich für den Inhalt (§ 18 Abs. 2 MStV)" to AppConfig.LEGAL_REPRESENTATIVE,
        "Verbraucherstreitbeilegung" to "Wir sind nicht bereit und nicht verpflichtet, an Streitbeilegungsverfahren vor einer Verbraucherschlichtungsstelle teilzunehmen."
    ) else listOf(
        "Information according to § 5 DDG" to listOf(AppConfig.LEGAL_OPERATOR_NAME, AppConfig.LEGAL_ADDRESS).joinToString("\n"),
        "Contact" to "Email: ${AppConfig.LEGAL_EMAIL}\nPhone: ${AppConfig.LEGAL_PHONE}",
        "Represented by" to AppConfig.LEGAL_REPRESENTATIVE,
        "Register entry" to AppConfig.LEGAL_REGISTER,
        "VAT ID" to AppConfig.LEGAL_VAT_ID,
        "Responsible for content (§ 18 (2) MStV)" to AppConfig.LEGAL_REPRESENTATIVE,
        "Consumer dispute resolution" to "We are neither willing nor obliged to take part in dispute resolution proceedings before a consumer arbitration board."
    )

    val privacy: LegalDoc
        get() = LegalDoc(
            "privacy_link",
            listOf(
                "1. Who is responsible" to "$operator\n\nYou can reach us at the email address above for every privacy question.",
                "2. What we store and why" to
                    "• Account: email address and password (stored only as a secure hash by our hosting provider), date of birth. Purpose: your account and the 18+ check. Legal basis: Art. 6(1)(b) GDPR (contract).\n" +
                    "• Profile: name, gender, who you are looking for, city and Bundesland, marriage plans, children, languages, education, job, height, smoking, bio, answers to questions and photos. Purpose: showing your profile to suitable members. Art. 6(1)(b) GDPR.\n" +
                    "• Religion (optional): only with your explicit consent, Art. 9(2)(a) GDPR. You can hide or delete it at any time.\n" +
                    "• Likes, matches, chat messages, blocks and reports. Purpose: the service itself and keeping members safe. Art. 6(1)(b) and (f) GDPR.\n" +
                    "• Records of your consents (which text version and when). Art. 6(1)(c) and 7(1) GDPR.",
                "3. Location check" to
                    "MarryMatch is only for people in Germany. At sign-up your phone's location is sent once to our server, which checks whether it is inside Germany and compares it with the country of your internet connection. The coordinates are not stored; we only keep the result (inside Germany yes/no, time of the check, and whether a moderator should look at it). Legal basis: Art. 6(1)(b) and (f) GDPR.",
                "4. Selfie verification (optional)" to
                    "If you ask for the blue tick, you take a live selfie with a pose. It is stored encrypted on our server in Frankfurt and only a moderator sees it. A person compares it with your photos; we do not use face recognition. The selfie is deleted as soon as the moderator decides; only the result is kept. Legal basis: your explicit consent, Art. 9(2)(a) GDPR, which you can withdraw at any time.",
                "5. On your phone" to
                    "Before a photo is uploaded, the app removes its metadata (for example GPS position and camera details) and counts the faces in it. The face count runs only on your phone (Google ML Kit); the photo is not sent to Google for this.",
                "6. Who can see your data" to
                    "• Other members see your profile only through the daily suggestions and your matches, with the privacy settings you chose (for example city hidden, religion hidden, incognito).\n" +
                    "• Profile photos are private. The app loads them through links that work for one hour, and our server creates such links only for you, your matches, members who get you as a daily suggestion, and moderators. If you turn on \"Blur my photos until we match\", members who are not your matches only get a blurred copy that your phone makes.\n" +
                    "• Activity: others see whether you were active today or this week (never exact times) and, in an open chat with a match, whether you are online or typing. You can turn this off in the privacy settings.\n" +
                    "• \"We met\": if you confirm that you met a match in person, we store that and how it went. The other person only learns that you confirmed, never your answer; moderators only see totals. Art. 6(1)(f) GDPR (safety and improving matches).\n" +
                    "• Chaperone: if you invite a chaperone (a family member), they can read your chats with matches from the moment they join, but not write, and they do not see photos. Your matches are told that a chaperone reads along. If your match has a chaperone, that person can read your chat with them in the same way. Either side can end this at any time. Art. 6(1)(b) GDPR for the member who invites, Art. 6(1)(f) GDPR (informed in advance) for their matches.\n" +
                    "• Moderators see reports, the reported messages and selfies waiting for review.\n" +
                    "• Hosting: Supabase (database, file storage, server functions), servers in Frankfurt am Main, EU. Supabase processes the data on our behalf under a data processing agreement (Art. 28 GDPR).\n" +
                    "• Push notifications are delivered by Google Firebase Cloud Messaging (Google Ireland Ltd.). For this we store a device code (push token) and the language of your app; a push contains only its kind and, for matches and messages, the other person's first name, never message text. Google may process this data in the USA; the transfer is based on the EU-US Data Privacy Framework and standard contractual clauses (Art. 45 and 46 GDPR). You can turn pushes off in Settings or in your phone's settings.\n" +
                    "We do not sell data, show no advertising and use no analytics or tracking tools.",
                "7. How long we keep it" to
                    "• Your data is kept until you delete your account. Deleting it in the app removes your profile, photos, matches, chats, likes and consents right away.\n" +
                    "• Reports you made stay without your name so moderators can finish them.\n" +
                    "• The moderation log keeps the account number and the decision (for example a ban) to prevent abuse.\n" +
                    "• Selfies are deleted after the moderator's decision.",
                "8. Your rights" to
                    "You have the right to access (Art. 15), correction (Art. 16), deletion (Art. 17), restriction (Art. 18), data portability (Art. 20) and to object (Art. 21). You can withdraw consents at any time with effect for the future. In the app you can export all your data (Settings → Export my data), edit your profile and delete your account. You also have the right to complain to a data protection supervisory authority, for example the one in the federal state where you live.",
                "9. Changes" to "If this policy changes, we will show you the new version in the app."
            ),
            listOf(
                "1. Verantwortlicher" to "$operator\n\nFür alle Fragen zum Datenschutz erreichen Sie uns unter der oben genannten E-Mail-Adresse.",
                "2. Welche Daten wir speichern und warum" to
                    "• Konto: E-Mail-Adresse und Passwort (beim Hosting-Anbieter nur als sicherer Hash gespeichert), Geburtsdatum. Zweck: Ihr Konto und die Prüfung ab 18. Rechtsgrundlage: Art. 6 Abs. 1 lit. b DSGVO (Vertrag).\n" +
                    "• Profil: Name, Geschlecht, gesuchtes Geschlecht, Stadt und Bundesland, Heiratspläne, Kinderwunsch, Sprachen, Ausbildung, Beruf, Größe, Rauchen, Über-mich-Text, Antworten auf Fragen und Fotos. Zweck: Anzeige Ihres Profils für passende Mitglieder. Art. 6 Abs. 1 lit. b DSGVO.\n" +
                    "• Religion (freiwillig): nur mit Ihrer ausdrücklichen Einwilligung, Art. 9 Abs. 2 lit. a DSGVO. Sie können sie jederzeit ausblenden oder löschen.\n" +
                    "• Likes, Matches, Chatnachrichten, Blockierungen und Meldungen. Zweck: der Dienst selbst und die Sicherheit der Mitglieder. Art. 6 Abs. 1 lit. b und f DSGVO.\n" +
                    "• Nachweise Ihrer Einwilligungen (welche Textversion und wann). Art. 6 Abs. 1 lit. c und Art. 7 Abs. 1 DSGVO.",
                "3. Standortprüfung" to
                    "MarryMatch ist nur für Menschen in Deutschland. Bei der Registrierung wird der Standort Ihres Handys einmal an unseren Server gesendet. Dieser prüft, ob er in Deutschland liegt, und vergleicht ihn mit dem Land Ihrer Internetverbindung. Die Koordinaten werden nicht gespeichert; wir speichern nur das Ergebnis (in Deutschland ja/nein, Zeitpunkt der Prüfung und ob ein Moderator nachsehen soll). Rechtsgrundlage: Art. 6 Abs. 1 lit. b und f DSGVO.",
                "4. Selfie-Verifizierung (freiwillig)" to
                    "Wenn Sie das blaue Häkchen möchten, machen Sie ein Live-Selfie mit einer Pose. Es wird verschlüsselt auf unserem Server in Frankfurt gespeichert und nur ein Moderator sieht es. Ein Mensch vergleicht es mit Ihren Fotos; wir nutzen keine Gesichtserkennung. Das Selfie wird gelöscht, sobald der Moderator entschieden hat; nur das Ergebnis bleibt. Rechtsgrundlage: Ihre ausdrückliche Einwilligung, Art. 9 Abs. 2 lit. a DSGVO, die Sie jederzeit widerrufen können.",
                "5. Auf Ihrem Handy" to
                    "Bevor ein Foto hochgeladen wird, entfernt die App seine Metadaten (zum Beispiel GPS-Position und Kameradaten) und zählt die Gesichter darauf. Das Zählen läuft nur auf Ihrem Handy (Google ML Kit); das Foto wird dafür nicht an Google gesendet.",
                "6. Wer Ihre Daten sehen kann" to
                    "• Andere Mitglieder sehen Ihr Profil nur über die täglichen Vorschläge und Ihre Matches, mit den von Ihnen gewählten Privatsphäre-Einstellungen (zum Beispiel Stadt ausgeblendet, Religion ausgeblendet, Inkognito).\n" +
                    "• Profilfotos sind privat. Die App lädt sie über Links, die eine Stunde gültig sind, und unser Server erstellt solche Links nur für Sie, Ihre Matches, Mitglieder, die Sie als täglichen Vorschlag bekommen, und Moderatoren. Wenn Sie „Meine Fotos bis zum Match verschwimmen“ einschalten, bekommen Mitglieder, die nicht Ihre Matches sind, nur eine verschwommene Kopie, die Ihr Handy erstellt.\n" +
                    "• Aktivität: Andere sehen, ob Sie heute oder diese Woche aktiv waren (nie genaue Uhrzeiten), und in einem offenen Chat mit einem Match, ob Sie online sind oder schreiben. Sie können das in den Privatsphäre-Einstellungen ausschalten.\n" +
                    "• „Wir haben uns getroffen“: Wenn Sie bestätigen, dass Sie ein Match persönlich getroffen haben, speichern wir das und wie es war. Die andere Person erfährt nur, dass Sie bestätigt haben, nie Ihre Antwort; Moderatoren sehen nur Gesamtzahlen. Art. 6 Abs. 1 lit. f DSGVO (Sicherheit und bessere Vorschläge).\n" +
                    "• Begleitperson: Wenn Sie eine Begleitperson (ein Familienmitglied) einladen, kann sie Ihre Chats mit Matches ab ihrem Beitritt lesen, aber nicht schreiben, und sieht keine Fotos. Ihre Matches erfahren, dass eine Begleitperson mitliest. Hat Ihr Match eine Begleitperson, kann diese Ihren Chat mit dem Match ebenso lesen. Beide Seiten können das jederzeit beenden. Art. 6 Abs. 1 lit. b DSGVO für das einladende Mitglied, Art. 6 Abs. 1 lit. f DSGVO (vorab informiert) für dessen Matches.\n" +
                    "• Moderatoren sehen Meldungen, die gemeldeten Nachrichten und Selfies, die auf Prüfung warten.\n" +
                    "• Hosting: Supabase (Datenbank, Dateispeicher, Serverfunktionen), Server in Frankfurt am Main, EU. Supabase verarbeitet die Daten in unserem Auftrag auf Grundlage eines Auftragsverarbeitungsvertrags (Art. 28 DSGVO).\n" +
                    "• Push-Benachrichtigungen werden über Google Firebase Cloud Messaging (Google Ireland Ltd.) zugestellt. Dafür speichern wir einen Gerätecode (Push-Token) und die Sprache Ihrer App; eine Benachrichtigung enthält nur ihre Art und bei Matches und Nachrichten den Vornamen der anderen Person, nie Nachrichtentext. Google kann diese Daten in den USA verarbeiten; die Übermittlung stützt sich auf das EU-US Data Privacy Framework und Standardvertragsklauseln (Art. 45 und 46 DSGVO). Sie können Benachrichtigungen in den Einstellungen oder in den Einstellungen Ihres Handys ausschalten.\n" +
                    "Wir verkaufen keine Daten, zeigen keine Werbung und nutzen keine Analyse- oder Tracking-Werkzeuge.",
                "7. Wie lange wir Daten speichern" to
                    "• Ihre Daten bleiben gespeichert, bis Sie Ihr Konto löschen. Die Löschung in der App entfernt Profil, Fotos, Matches, Chats, Likes und Einwilligungen sofort.\n" +
                    "• Meldungen, die Sie gemacht haben, bleiben ohne Ihren Namen erhalten, damit Moderatoren sie abschließen können.\n" +
                    "• Das Moderationsprotokoll behält die Kontonummer und die Entscheidung (zum Beispiel eine Sperre), um Missbrauch zu verhindern.\n" +
                    "• Selfies werden nach der Entscheidung des Moderators gelöscht.",
                "8. Ihre Rechte" to
                    "Sie haben das Recht auf Auskunft (Art. 15), Berichtigung (Art. 16), Löschung (Art. 17), Einschränkung (Art. 18), Datenübertragbarkeit (Art. 20) und Widerspruch (Art. 21). Einwilligungen können Sie jederzeit mit Wirkung für die Zukunft widerrufen. In der App können Sie alle Ihre Daten exportieren (Einstellungen → Meine Daten exportieren), Ihr Profil bearbeiten und Ihr Konto löschen. Außerdem können Sie sich bei einer Datenschutz-Aufsichtsbehörde beschweren, zum Beispiel in dem Bundesland, in dem Sie wohnen.",
                "9. Änderungen" to "Wenn sich diese Erklärung ändert, zeigen wir Ihnen die neue Fassung in der App."
            )
        )

    val terms: LegalDoc
        get() = LegalDoc(
            "terms_link",
            listOf(
                "1. The service" to "MarryMatch helps adults in Germany find a partner for marriage. The service is currently free of charge. Operator: see the Impressum.",
                "2. Who may join" to "You must be at least 18 years old and live in Germany. You may have only one account. All information in your profile must be true, and photos must show you.",
                "3. Your behaviour" to "You must follow the Code of Conduct. Commercial use, advertising, asking for money and contacting people for anything other than a serious relationship are not allowed.",
                "4. Moderation" to "We may check reports, warn members, hide profiles, reject selfies and suspend or delete accounts that break these terms or the Code of Conduct, especially for safety reasons. Serious cases (for example under 18, threats or fraud) lead to an immediate ban.",
                "5. Ending the contract" to "You can delete your account at any time in the app. We can end the contract with notice, or immediately for an important reason such as a serious breach of these terms.",
                "6. Liability" to "We are liable without limit for intent and gross negligence and for injury to life, body or health. For slight negligence we are only liable for breach of essential contractual duties and limited to the typical, foreseeable damage. We are not responsible for what members say or do, but we act on reports.",
                "7. Final provisions" to "German law applies. Mandatory consumer protection rules of the country where you live stay unaffected."
            ),
            listOf(
                "1. Der Dienst" to "MarryMatch hilft Erwachsenen in Deutschland, einen Partner für die Ehe zu finden. Der Dienst ist derzeit kostenlos. Anbieter: siehe Impressum.",
                "2. Wer mitmachen darf" to "Sie müssen mindestens 18 Jahre alt sein und in Deutschland leben. Sie dürfen nur ein Konto haben. Alle Angaben in Ihrem Profil müssen wahr sein, und Fotos müssen Sie zeigen.",
                "3. Ihr Verhalten" to "Sie müssen den Verhaltenskodex einhalten. Gewerbliche Nutzung, Werbung, Bitten um Geld und Kontaktaufnahme zu anderen Zwecken als einer ernsthaften Beziehung sind nicht erlaubt.",
                "4. Moderation" to "Wir dürfen Meldungen prüfen, Mitglieder verwarnen, Profile ausblenden, Selfies ablehnen und Konten sperren oder löschen, die gegen diese Bedingungen oder den Verhaltenskodex verstoßen, besonders aus Sicherheitsgründen. Schwere Fälle (zum Beispiel unter 18, Drohungen oder Betrug) führen zur sofortigen Sperre.",
                "5. Vertragsende" to "Sie können Ihr Konto jederzeit in der App löschen. Wir können den Vertrag mit Frist kündigen oder sofort aus wichtigem Grund, etwa bei einem schweren Verstoß gegen diese Bedingungen.",
                "6. Haftung" to "Wir haften unbeschränkt für Vorsatz und grobe Fahrlässigkeit sowie für Schäden an Leben, Körper oder Gesundheit. Bei leichter Fahrlässigkeit haften wir nur bei Verletzung wesentlicher Vertragspflichten und begrenzt auf den typischen, vorhersehbaren Schaden. Für Äußerungen und Handlungen von Mitgliedern sind wir nicht verantwortlich, gehen Meldungen aber nach.",
                "7. Schlussbestimmungen" to "Es gilt deutsches Recht. Zwingende Verbraucherschutzvorschriften des Landes, in dem Sie leben, bleiben unberührt."
            )
        )

    val conduct: LegalDoc
        get() = LegalDoc(
            "code_of_conduct_link",
            listOf(
                "Be honest" to "Use your real name and age and recent photos that show you. Only one account per person.",
                "Be respectful" to "Treat every member with dignity. No insults, harassment, hate speech or discrimination. A \"no\" is an answer.",
                "Be serious" to "MarryMatch is for people looking for marriage. No casual dating offers, no advertising, no business.",
                "Stay safe" to "Never ask for or send money. Do not share passwords or bank details. Meet in public places the first time.",
                "Report problems" to "Report people who break these rules. Under 18, threats and fraud lead to an immediate ban."
            ),
            listOf(
                "Ehrlich sein" to "Verwenden Sie Ihren echten Namen, Ihr echtes Alter und aktuelle Fotos, die Sie zeigen. Nur ein Konto pro Person.",
                "Respektvoll sein" to "Behandeln Sie jedes Mitglied mit Würde. Keine Beleidigungen, Belästigung, Hassrede oder Diskriminierung. Ein \"Nein\" ist eine Antwort.",
                "Ernsthaft sein" to "MarryMatch ist für Menschen, die eine Ehe suchen. Keine Angebote für Unverbindliches, keine Werbung, keine Geschäfte.",
                "Sicher bleiben" to "Bitten Sie nie um Geld und senden Sie keins. Teilen Sie keine Passwörter oder Bankdaten. Treffen Sie sich beim ersten Mal an öffentlichen Orten.",
                "Probleme melden" to "Melden Sie Personen, die gegen diese Regeln verstoßen. Unter 18, Drohungen und Betrug führen zur sofortigen Sperre."
            )
        )
}
