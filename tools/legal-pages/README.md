# Legal web pages

`docs/privacy.html`, `terms.html`, `conduct.html` and `impressum.html` are generated from
`app/src/main/java/com/example/core/LegalTexts.kt` (and the `LEGAL_*` values in `AppConfig.kt`),
so the app and the website always say the same. Google Play needs the privacy policy as a public URL.

After changing a legal text, regenerate: compile `Gen.kt` together with `LegalTexts.kt` and
`AppConfig.kt` (kotlinc) and run it with `docs` as the argument. `LegalPagesTest` fails when the pages
no longer match the app texts.

Published with GitHub Pages (Settings → Pages → Deploy from a branch → `main` / `docs`).
