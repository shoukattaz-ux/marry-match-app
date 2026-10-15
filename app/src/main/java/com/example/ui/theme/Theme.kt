package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ExtendedColors(
  val verified: Color,
  val accent: Color,
  val muted: Color,
  val cardBackground: Color,
  val surfaceVariantSubtle: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
  ExtendedColors(
    verified = MarryVerifiedLight,
    accent = MarryAccentLight,
    muted = MarryMutedLight,
    cardBackground = MarrySurfaceLight,
    surfaceVariantSubtle = Color(0xFFF0F4F8)
  )
}

private val DarkColorScheme = darkColorScheme(
  primary = MarryPrimaryDark,
  onPrimary = Color(0xFF0D1B2A),
  primaryContainer = Color(0xFF1E3A5F),
  onPrimaryContainer = Color(0xFFD6E4F0),
  secondary = MarrySecondaryDark,
  onSecondary = Color(0xFF00382B),
  secondaryContainer = Color(0xFF1B4D3E),
  onSecondaryContainer = Color(0xFFA7F3D0),
  tertiary = MarryAccentDark,
  onTertiary = Color(0xFF452B00),
  background = MarryBackgroundDark,
  onBackground = MarryTextDark,
  surface = MarrySurfaceDark,
  onSurface = MarryTextDark,
  surfaceVariant = Color(0xFF1C2735),
  onSurfaceVariant = MarryMutedDark,
  error = MarryErrorDark,
  onError = Color(0xFF690005),
  outline = Color(0xFF334455),
  outlineVariant = Color(0xFF233040)
)

private val LightColorScheme = lightColorScheme(
  primary = MarryPrimaryLight,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDCE7F5),
  onPrimaryContainer = Color(0xFF0F1E33),
  secondary = MarrySecondaryLight,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFD1FAE5),
  onSecondaryContainer = Color(0xFF064E3B),
  tertiary = MarryAccentLight,
  onTertiary = Color.Black,
  background = MarryBackgroundLight,
  onBackground = MarryTextLight,
  surface = MarrySurfaceLight,
  onSurface = MarryTextLight,
  surfaceVariant = Color(0xFFEEF2F6),
  onSurfaceVariant = MarryMutedLight,
  error = MarryErrorLight,
  onError = Color.White,
  outline = Color(0xFFD0D7DE),
  outlineVariant = Color(0xFFE2E8F0)
)

val MarryShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp), // buttons, text fields (12dp per spec)
  large = RoundedCornerShape(16.dp),  // cards, bottom sheets (16dp per spec)
  extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MarryMatchTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val extendedColors = if (darkTheme) {
    ExtendedColors(
      verified = MarryVerifiedDark,
      accent = MarryAccentDark,
      muted = MarryMutedDark,
      cardBackground = MarrySurfaceDark,
      surfaceVariantSubtle = Color(0xFF1C2735)
    )
  } else {
    ExtendedColors(
      verified = MarryVerifiedLight,
      accent = MarryAccentLight,
      muted = MarryMutedLight,
      cardBackground = MarrySurfaceLight,
      surfaceVariantSubtle = Color(0xFFF1F5F9)
    )
  }

  CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = MarryShapes,
      content = content
    )
  }
}

val MaterialTheme.extendedColors: ExtendedColors
  @Composable
  get() = LocalExtendedColors.current

val ColorScheme.surfaceVariantSubtle: Color
  @Composable
  get() = LocalExtendedColors.current.surfaceVariantSubtle
