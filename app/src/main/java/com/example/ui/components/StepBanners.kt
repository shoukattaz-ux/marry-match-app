package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Interests
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.Localization

/**
 * One information card of the profile setup: what MarryMatch stands for (marriage and family),
 * why commitment matters, and the safety rules. Text keys are "banner_<key>_title/_body/_tag".
 */
data class StepBanner(val key: String, val icon: ImageVector, val colors: List<Color>)

object StepBanners {
    /** One card per setup step (index 0 = step 1), each matching what the step asks about. */
    val ALL = listOf(
        StepBanner("purpose", Icons.Default.FamilyRestroom, listOf(Color(0xFF1E3A5F), Color(0xFF3F6FB0))),
        StepBanner("man_woman", Icons.Default.Wc, listOf(Color(0xFF6A3D9A), Color(0xFFB45BA8))),
        StepBanner("verified", Icons.Default.VerifiedUser, listOf(Color(0xFF00695C), Color(0xFF26A69A))),
        StepBanner("marriage_only", Icons.Default.EventAvailable, listOf(Color(0xFFB8862A), Color(0xFFE9B44C))),
        StepBanner("family", Icons.Default.Favorite, listOf(Color(0xFFC2185B), Color(0xFFF06292))),
        StepBanner("respect", Icons.Default.Psychology, listOf(Color(0xFF283593), Color(0xFF5C6BC0))),
        StepBanner("stability", Icons.Default.SelfImprovement, listOf(Color(0xFF2E7D32), Color(0xFF66BB6A))),
        StepBanner("companionship", Icons.Default.Interests, listOf(Color(0xFFE65100), Color(0xFFFFA040))),
        StepBanner("peace", Icons.Default.Spa, listOf(Color(0xFF00838F), Color(0xFF4DD0E1))),
        StepBanner("casual_toll", Icons.Default.HeartBroken, listOf(Color(0xFF6D4C41), Color(0xFFA1887F))),
        StepBanner("no_fakes", Icons.Default.Shield, listOf(Color(0xFFB71C1C), Color(0xFFE57373))),
        StepBanner("commitment", Icons.Default.AutoAwesome, listOf(Color(0xFF4527A0), Color(0xFF7E57C2))),
        StepBanner("story", Icons.Default.Celebration, listOf(Color(0xFF1E3A5F), Color(0xFFB8862A)))
    )

    fun forStep(step: Int): StepBanner = ALL[(step - 1).coerceIn(0, ALL.size - 1)]

    /** Heights from which the bigger layouts are used; below [COMPACT] nothing is shown. */
    val LARGE: Dp = 230.dp
    val MEDIUM: Dp = 140.dp
    val COMPACT: Dp = 76.dp
}

/**
 * Fills the free space under a step with that step's card: large (big icon, title, text and a
 * tag) when there is a lot of room, medium (icon beside title and text) or compact (icon and
 * title) when there is less, and nothing when the step itself fills the screen.
 */
@Composable
fun StepBannerSpace(step: Int, lang: AppLanguage, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        val room = maxHeight
        if (room < StepBanners.COMPACT) return@BoxWithConstraints
        AnimatedContent(
            targetState = StepBanners.forStep(step),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "step_banner"
        ) { banner ->
            val t: (String) -> String = { Localization.t("banner_${banner.key}_$it", lang) }
            val height = when {
                room >= StepBanners.LARGE -> room.coerceAtMost(320.dp)
                room >= StepBanners.MEDIUM -> room.coerceAtMost(StepBanners.LARGE - 1.dp)
                else -> room.coerceAtMost(StepBanners.MEDIUM - 1.dp)
            }
            val shape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .shadow(10.dp, shape, ambientColor = banner.colors.last(), spotColor = banner.colors.last())
                    .clip(shape)
                    .background(Brush.linearGradient(banner.colors))
                    .testTag("step_banner_${banner.key}")
            ) {
                // Soft decorative circles for depth.
                Box(
                    Modifier
                        .size(height * 0.9f)
                        .align(Alignment.TopEnd)
                        .offset(x = height * 0.3f, y = -height * 0.3f)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                )
                Box(
                    Modifier
                        .size(height * 0.5f)
                        .align(Alignment.BottomStart)
                        .offset(x = -height * 0.15f, y = height * 0.2f)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                )
                when {
                    room >= StepBanners.LARGE -> LargeBanner(banner, t)
                    room >= StepBanners.MEDIUM -> MediumBanner(banner, t)
                    else -> CompactBanner(banner, t)
                }
            }
        }
    }
}

@Composable
private fun BannerIcon(icon: ImageVector, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Color.White.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun LargeBanner(banner: StepBanner, t: (String) -> String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BannerIcon(banner.icon, 56.dp)
            Text(
                t("tag").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(
            t("title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            t("body"),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediumBanner(banner: StepBanner, t: (String) -> String) {
    Row(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BannerIcon(banner.icon, 46.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                t("title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                t("body"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactBanner(banner: StepBanner, t: (String) -> String) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BannerIcon(banner.icon, 38.dp)
        Text(
            t("title"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
