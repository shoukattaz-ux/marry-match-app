package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.Localization
import com.example.model.SignatureCard
import com.example.model.SignatureCatalog

/** The fonts bundled for the card (res/font, SIL Open Font License, see assets/licenses). */
object CardFonts {
    val Playfair = FontFamily(
        Font(R.font.playfair_display_bold, FontWeight.Bold),
        Font(R.font.playfair_display_italic, FontWeight.Normal, FontStyle.Italic)
    )
    val Cormorant = FontFamily(
        Font(R.font.cormorant_garamond_medium, FontWeight.Medium),
        Font(R.font.cormorant_garamond_semibold_italic, FontWeight.SemiBold, FontStyle.Italic)
    )
    val Manrope = FontFamily(
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_bold, FontWeight.Bold)
    )
    val Amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal))
}

/** Colours, fonts and light of one card design. */
data class CardStyle(
    val background: List<Color>,
    /** Soft round lights drawn over the background: colour, x and y (0..1 of the card), radius in dp. */
    val glows: List<Glow>,
    val text: Color,
    val muted: Color,
    val accent: Color,
    /** The inner glass panels and tag pills. */
    val panel: Color,
    val panelBorder: Color,
    /** A thin frame inside the edge (Champagne Pearl, Emerald Noir). */
    val frame: Color?,
    val headline: TextStyle,
    val seal: Color,
    val sealText: Color
) {
    data class Glow(val color: Color, val x: Float, val y: Float, val radiusDp: Float)
}

fun cardStyle(template: String): CardStyle = when (template) {
    "champagne_pearl" -> CardStyle(
        background = listOf(Color(0xFFFBF6EE), Color(0xFFF3E7D3), Color(0xFFE9D8BC)),
        glows = listOf(CardStyle.Glow(Color.White.copy(alpha = 0.85f), 0.2f, 0.1f, 180f), CardStyle.Glow(Color(0x33C9A55C), 0.9f, 0.95f, 200f)),
        text = Color(0xFF3B2F2A), muted = Color(0xFF8A7458), accent = Color(0xFFB08D57),
        panel = Color.White.copy(alpha = 0.55f), panelBorder = Color(0x66B08D57), frame = Color(0xFFC9A55C),
        headline = TextStyle(fontFamily = CardFonts.Cormorant, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 25.sp, lineHeight = 30.sp),
        seal = Color(0xFFB08D57), sealText = Color.White
    )
    "rose_velvet" -> CardStyle(
        background = listOf(Color(0xFFB0365C), Color(0xFF7A1C3C), Color(0xFF3F0A20)),
        glows = listOf(CardStyle.Glow(Color.White.copy(alpha = 0.22f), 0.15f, 0.05f, 220f), CardStyle.Glow(Color(0x55FF8FB1), 0.95f, 0.6f, 160f)),
        text = Color(0xFFFFF1F4), muted = Color(0xFFF7C6D0), accent = Color(0xFFF7C6D0),
        panel = Color.White.copy(alpha = 0.10f), panelBorder = Color.White.copy(alpha = 0.22f), frame = null,
        headline = TextStyle(fontFamily = CardFonts.Playfair, fontStyle = FontStyle.Italic, fontSize = 23.sp, lineHeight = 30.sp),
        seal = Color(0xFFE8C77D), sealText = Color(0xFF3F0A20)
    )
    "emerald_noir" -> CardStyle(
        background = listOf(Color(0xFF0E3B2F), Color(0xFF082219), Color(0xFF03110C)),
        glows = listOf(CardStyle.Glow(Color(0x3329B37F), 0.85f, 0.1f, 200f), CardStyle.Glow(Color(0x22C9A55C), 0.1f, 0.9f, 180f)),
        text = Color(0xFFF3EBD8), muted = Color(0xFFC9B98F), accent = Color(0xFFC9A55C),
        panel = Color.White.copy(alpha = 0.06f), panelBorder = Color(0x66C9A55C), frame = Color(0xFFC9A55C),
        headline = TextStyle(fontFamily = CardFonts.Cormorant, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 25.sp, lineHeight = 30.sp),
        seal = Color(0xFFC9A55C), sealText = Color(0xFF03110C)
    )
    else -> CardStyle(  // midnight_glass
        background = listOf(Color(0xFF1A1440), Color(0xFF2B1E5C), Color(0xFF120E2E)),
        glows = listOf(CardStyle.Glow(Color(0x998E2DE2), 0.1f, 0.15f, 190f), CardStyle.Glow(Color(0x88FF4E8A), 0.95f, 0.75f, 200f)),
        text = Color.White, muted = Color.White.copy(alpha = 0.72f), accent = Color(0xFFFFB3CF),
        panel = Color.White.copy(alpha = 0.10f), panelBorder = Color.White.copy(alpha = 0.26f), frame = null,
        headline = TextStyle(fontFamily = CardFonts.Playfair, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp),
        seal = Color(0xFFE8C77D), sealText = Color(0xFF1A1440)
    )
}

/**
 * The Signature Card as everyone sees it. [onLike] shows the heart ("Like this card"); it is
 * left out in previews and on her own profile. Arabic text is set right-to-left in Amiri.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignatureCardView(
    card: SignatureCard,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
    onLike: (() -> Unit)? = null
) {
    val style = cardStyle(card.template)
    val german = lang == AppLanguage.DE
    val t: (String) -> String = { Localization.t(it, lang) }
    val label = TextStyle(fontFamily = CardFonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.6.sp, color = style.muted)
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(style.background))
            .drawBehind {
                style.glows.forEach { g ->
                    val center = Offset(size.width * g.x, size.height * g.y)
                    drawCircle(Brush.radialGradient(listOf(g.color, Color.Transparent), center, g.radiusDp.dp.toPx()), g.radiusDp.dp.toPx(), center)
                }
            }
            .testTag("signature_card")
    ) {
        if (style.frame != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(8.dp)
                    .border(BorderStroke(1.dp, style.frame.copy(alpha = 0.7f)), RoundedCornerShape(18.dp))
            )
        }
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦ " + t("card_signature").uppercase(), style = label.copy(color = style.accent), modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(50), color = style.seal) {
                    Text(
                        "✦ " + t("card_verified").uppercase(),
                        style = label.copy(color = style.sealText, fontSize = 10.sp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(t("card_looking_for").uppercase(), style = label)
            Spacer(Modifier.height(6.dp))
            val looking = card.lookingFor.trim()
            val rtl = SignatureCatalog.isArabicScript(looking)
            Text(
                text = "“$looking”",
                style = (if (rtl) style.headline.copy(fontFamily = CardFonts.Amiri, fontStyle = FontStyle.Normal, fontWeight = FontWeight.Normal, lineHeight = 36.sp) else style.headline)
                    .copy(color = style.text, textDirection = if (rtl) TextDirection.Rtl else TextDirection.Content, textAlign = if (rtl) TextAlign.Right else TextAlign.Start),
                modifier = Modifier.fillMaxWidth().testTag("card_looking_for")
            )

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, style.accent.copy(alpha = 0.7f), Color.Transparent))))
            Spacer(Modifier.height(16.dp))

            Text(t("card_why_here").uppercase(), style = label)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                card.reasons.mapNotNull { SignatureCatalog.reason(it) }.forEach { reason ->
                    Text(
                        reason.label(german),
                        style = TextStyle(fontFamily = CardFonts.Manrope, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = style.text),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(style.panel)
                            .border(1.dp, style.panelBorder, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            val vibe = card.vibe?.trim().orEmpty()
            if (vibe.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(t("card_my_vibe").uppercase(), style = label)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(style.panel)
                        .border(1.dp, style.panelBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(card.vibeEmoji ?: "✨", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    val vibeRtl = SignatureCatalog.isArabicScript(vibe)
                    Text(
                        vibe,
                        style = TextStyle(
                            fontFamily = if (vibeRtl) CardFonts.Amiri else style.headline.fontFamily,
                            fontStyle = if (vibeRtl) FontStyle.Normal else FontStyle.Italic,
                            fontSize = 18.sp,
                            color = style.text,
                            textDirection = if (vibeRtl) TextDirection.Rtl else TextDirection.Content
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (onLike != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(50))
                        .background(style.panel)
                        .border(1.dp, style.panelBorder, RoundedCornerShape(50))
                        .clickable(onClick = onLike)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("card_like_button")
                ) {
                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = style.accent, modifier = Modifier.size(18.dp))
                    Text(t("card_like"), style = TextStyle(fontFamily = CardFonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = style.text))
                }
            }
        }
    }
}

/** The small "✦ Signature" label on her Discover photo: a card is waiting further down. */
@Composable
fun SignatureBadge(lang: AppLanguage, modifier: Modifier = Modifier) {
    Text(
        "✦ " + Localization.t("card_signature", lang),
        style = TextStyle(fontFamily = CardFonts.Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2B1E0A), letterSpacing = 0.5.sp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Color(0xFFF3DFA2), Color(0xFFD9B46A))))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("signature_badge")
    )
}
