package com.example.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.data.AuthState
import com.example.data.MarryMatchRepository
import com.example.ui.components.MarryPrimaryButton
import com.example.ui.components.MarrySecondaryButton
import com.example.ui.theme.extendedColors
import kotlinx.coroutines.delay

/** The photos of the welcome carousel with their caption keys and how each is cropped. */
private data class WelcomeSlide(val image: Int, val captionKey: String, val alignment: Alignment)

private val WELCOME_SLIDES = listOf(
    WelcomeSlide(R.drawable.welcome_family_park, "welcome_slide_values", BiasAlignment(0.05f, -0.2f)),
    WelcomeSlide(R.drawable.welcome_family_beach, "welcome_slide_story", BiasAlignment(-0.05f, 0f)),
    WelcomeSlide(R.drawable.welcome_family_nature, "welcome_slide_family", BiasAlignment(0.27f, -0.3f))
)

/** Time each photo stays before the carousel moves on by itself. */
private const val SLIDE_MILLIS = 4500L

/**
 * First screen: a photo carousel at the top (it slides by itself and can be swiped), then the
 * app name, promise and trust points, and the two buttons at the bottom. The layout fills any
 * screen height; on small screens or with large text it scrolls instead of cutting anything off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(
    repository: MarryMatchRepository,
    modifier: Modifier = Modifier
) {
    val lang by repository.appLanguage.collectAsState()
    val t: (String) -> String = { Localization.t(it, lang) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenHeight = maxHeight
        // The photos take a bit under half of the screen: big on tall phones, never crowding the buttons.
        val carouselHeight = (screenHeight * 0.46f).coerceIn(240.dp, 480.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = screenHeight),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WelcomeCarousel(
                height = carouselHeight,
                t = t,
                languageLabel = if (lang == AppLanguage.EN) "DE · Deutsch" else "EN · English",
                onToggleLanguage = repository::toggleLanguage
            )

            // Text and buttons; on tablets and in landscape they keep a readable width.
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The logo overlaps the bottom edge of the photos a little.
                    Image(
                        painter = painterResource(R.drawable.brand_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .offset(y = (-36).dp)
                            .size(72.dp)
                            .shadow(10.dp, RoundedCornerShape(18.dp))
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Text(
                        text = t("app_name"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(y = (-24).dp)
                    )
                    Column(
                        modifier = Modifier.offset(y = (-16).dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = t("tagline"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = t("welcome_promise"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.muted,
                            textAlign = TextAlign.Center
                        )
                        // Wraps onto two lines on narrow screens instead of squeezing.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            TrustPill(icon = "🇩🇪", text = if (lang == AppLanguage.EN) "Germany Only" else "Nur Deutschland")
                            TrustPill(icon = "✓", text = if (lang == AppLanguage.EN) "Selfie Verified" else "Verifiziert")
                            TrustPill(icon = "🔒", text = if (lang == AppLanguage.EN) "EU Hosted" else "DSGVO-sicher")
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MarryPrimaryButton(
                        text = t("create_account"),
                        onClick = { repository.setAuthState(AuthState.LOCATION_GATE) },
                        testTag = "welcome_create_account_btn"
                    )
                    MarrySecondaryButton(
                        text = t("log_in"),
                        onClick = { repository.setAuthState(AuthState.LOGIN) },
                        testTag = "welcome_login_btn"
                    )
                }
            }
        }
    }
}

/**
 * Full-width photos under the status bar. They move on every few seconds (endlessly, in a loop),
 * pause while a finger is on them, and fade into the page at the bottom. The language switch sits
 * on top of the photos.
 */
@Composable
private fun WelcomeCarousel(
    height: Dp,
    t: (String) -> String,
    languageLabel: String,
    onToggleLanguage: () -> Unit
) {
    val count = WELCOME_SLIDES.size
    // A very long row of pages that repeats the photos, so swiping and sliding never hit an end.
    val start = (Int.MAX_VALUE / 2).let { it - it % count }
    val pager = rememberPagerState(initialPage = start) { Int.MAX_VALUE }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(pager.settledPage, dragged) {
        if (dragged) return@LaunchedEffect
        delay(SLIDE_MILLIS)
        pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(durationMillis = 700))
    }
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .testTag("welcome_carousel")
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val slide = WELCOME_SLIDES[page % count]
            Image(
                painter = painterResource(slide.image),
                contentDescription = t("welcome_photo_desc"),
                contentScale = ContentScale.Crop,
                alignment = slide.alignment,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Darker top so the clock and the language switch stay readable on bright photos.
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
        )
        // The photo fades into the page below.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(height * 0.45f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = 0.85f), background)))
        )

        // Language switch
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onToggleLanguage)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("welcome_language_btn"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(languageLabel, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }

        // Caption of the current photo and the page dots, above the fade.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = t(WELCOME_SLIDES[pager.currentPage % count].captionKey),
                style = MaterialTheme.typography.titleMedium.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f)
                ),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    val selected = pager.currentPage % count == i
                    val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dot")
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustPill(icon: String, text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 12.sp)
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
