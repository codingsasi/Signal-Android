package org.signal.core.ui.compose.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import org.signal.core.ui.CoreUiDependencies
import org.signal.core.ui.compose.ProvideIncognitoKeyboard

/**
 * Headings and labels hyphenate a word too long for its line, rather than breaking it at an arbitrary letter. This also
 * lets lines break at a hyphen already in the text, which they otherwise don't. Body text is left alone.
 */
private val typography = Typography().run {
  copy(
    displayLarge = displayLarge.copy(hyphens = Hyphens.Auto),
    displayMedium = displayMedium.copy(hyphens = Hyphens.Auto),
    displaySmall = displaySmall.copy(hyphens = Hyphens.Auto),
    headlineLarge = headlineLarge.copy(
      fontSize = 32.sp,
      lineHeight = 40.sp,
      letterSpacing = 0.sp,
      hyphens = Hyphens.Auto
    ),
    headlineMedium = headlineMedium.copy(
      fontSize = 28.sp,
      lineHeight = 36.sp,
      letterSpacing = 0.sp,
      hyphens = Hyphens.Auto
    ),
    headlineSmall = headlineSmall.copy(hyphens = Hyphens.Auto),
    titleLarge = titleLarge.copy(
      fontSize = 22.sp,
      lineHeight = 28.sp,
      letterSpacing = 0.sp,
      hyphens = Hyphens.Auto
    ),
    titleMedium = titleMedium.copy(
      fontSize = 18.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.0125.sp,
      fontFamily = FontFamily.SansSerif,
      fontStyle = FontStyle.Normal,
      hyphens = Hyphens.Auto
    ),
    titleSmall = titleSmall.copy(
      fontSize = 16.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.0125.sp,
      hyphens = Hyphens.Auto
    ),
    bodyLarge = bodyLarge.copy(
      fontSize = 16.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.0125.sp
    ),
    bodyMedium = bodyMedium.copy(
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.0107.sp
    ),
    bodySmall = bodySmall.copy(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.0192.sp
    ),
    labelLarge = labelLarge.copy(
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.0107.sp,
      hyphens = Hyphens.Auto
    ),
    labelMedium = labelMedium.copy(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.0192.sp,
      hyphens = Hyphens.Auto
    ),
    labelSmall = labelSmall.copy(
      fontSize = 12.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.025.sp,
      hyphens = Hyphens.Auto
    )
  )
}

private val lightColorScheme = lightColorScheme(
  primary = Color(0xFF6A2CC3),
  primaryContainer = Color(0xFFE2D2FB),
  secondary = Color(0xFF615871),
  secondaryContainer = Color(0xFFE7DCF9),
  surface = Color(0xFFFDFBFF),
  surfaceContainerLow = Color(0xFFF6F2F9),
  surfaceContainerHighest = Color(0xFFEDE7F3),
  surfaceVariant = Color(0xFFEDE7F3),
  background = Color(0xFFFDFBFF),
  error = Color(0xFFBA1B1B),
  errorContainer = Color(0xFFFFDAD4),
  onPrimary = Color(0xFFFFFFFF),
  onPrimaryContainer = Color(0xFF1F0545),
  onSecondary = Color(0xFFFFFFFF),
  onSecondaryContainer = Color(0xFF1D152C),
  onSurface = Color(0xFF1C1A1E),
  onSurfaceVariant = Color(0xFF5C5463),
  onBackground = Color(0xFF1C1A1E),
  outline = Color(0xFF808389)
)

private val lightExtendedColors = ExtendedColors(
  neutralSurface = Color(0x99FFFFFF),
  neutralFill = Color(0x1A000000),
  colorOnCustom = Color(0xFFFFFFFF),
  colorOnCustomVariant = Color(0xB3FFFFFF),
  colorSurface1 = Color(0xFFF6F2F9),
  colorSurface2 = Color(0xFFF2EDF6),
  colorSurface3 = Color(0xFFEEE8F4),
  colorSurface4 = Color(0xFFEDE6F3),
  colorSurface5 = Color(0xFFEAE3F1),
  colorSurfaceVariantFill = Color(0xCCFFFFFF),
  colorTransparent1 = Color(0x14FFFFFF),
  colorTransparent2 = Color(0x29FFFFFF),
  colorTransparent3 = Color(0x8FFFFFFF),
  colorTransparent4 = Color(0xB8FFFFFF),
  colorTransparent5 = Color(0xF5FFFFFF),
  colorNeutral = Color(0xFFFFFFFF),
  colorNeutralVariant = Color(0xB8FFFFFF),
  colorTransparentInverse1 = Color(0x0A000000),
  colorTransparentInverse2 = Color(0x14000000),
  colorTransparentInverse3 = Color(0x66000000),
  colorTransparentInverse4 = Color(0xB8000000),
  colorTransparentInverse5 = Color(0xE0000000),
  colorNeutralInverse = Color(0xFF121212),
  colorNeutralVariantInverse = Color(0xFF5C5C5C),
  colorWarning = Color(0x1FB44828),
  colorOnWarning = Color(0xFFB44828),
  colorAlert = Color(0xFFEF5350),
  colorAlertDisabled = Color(0x80EF5350)
)

private val darkExtendedColors = ExtendedColors(
  neutralSurface = Color(0x14FFFFFF),
  neutralFill = Color(0x33FFFFFF),
  colorOnCustom = Color(0xFFFFFFFF),
  colorOnCustomVariant = Color(0x18FFFFFF),
  colorSurface1 = Color(0xFF27202D),
  colorSurface2 = Color(0xFF2C2533),
  colorSurface3 = Color(0xFF32293A),
  colorSurface4 = Color(0xFF342B3C),
  colorSurface5 = Color(0xFF382E41),
  colorSurfaceVariantFill = Color(0x33FFFFFF),
  colorTransparent1 = Color(0x0AFFFFFF),
  colorTransparent2 = Color(0x1FFFFFFF),
  colorTransparent3 = Color(0x29FFFFFF),
  colorTransparent4 = Color(0x7AFFFFFF),
  colorTransparent5 = Color(0xB8FFFFFF),
  colorNeutral = Color(0xFF120F15),
  colorNeutralVariant = Color(0xFF5D4C6C),
  colorTransparentInverse1 = Color(0x0A000000),
  colorTransparentInverse2 = Color(0x14000000),
  colorTransparentInverse3 = Color(0x29000000),
  colorTransparentInverse4 = Color(0xB8000000),
  colorTransparentInverse5 = Color(0xF5000000),
  colorNeutralInverse = Color(0xE0FFFFFF),
  colorNeutralVariantInverse = Color(0xA3FFFFFF),
  colorWarning = Color(0x1FEB977D),
  colorOnWarning = Color(0xFFEB977D),
  colorAlert = Color(0xFFF44336),
  colorAlertDisabled = Color(0x80F44336)
)

private val darkColorScheme = darkColorScheme(
  primary = Color(0xFFD7B6FA),
  primaryContainer = Color(0xFF50465C),
  secondary = Color(0xFFD0C1DD),
  secondaryContainer = Color(0xFF4D4159),
  surface = Color(0xFF1D1822),
  surfaceContainerLow = Color(0xFF27202D),
  surfaceContainerHighest = Color(0xFF32293A),
  surfaceVariant = Color(0xFF32293A),
  background = Color(0xFF1D1822),
  error = Color(0xFFFFB4A9),
  errorContainer = Color(0xFF930006),
  onPrimary = Color(0xFF2A1E38),
  onPrimaryContainer = Color(0xFFECDBFC),
  onSecondary = Color(0xFF352A42),
  onSecondaryContainer = Color(0xFFEBDCF9),
  onSurface = Color(0xFFE3DEE8),
  onSurfaceVariant = Color(0xFFC2B7CC),
  onBackground = Color(0xFFE3DEE8),
  outline = Color(0xFF605C65)
)

private val lightSnackbarColors = SnackbarColors(
  color = darkColorScheme.surface,
  contentColor = darkColorScheme.onSurface,
  actionColor = darkColorScheme.primary,
  actionContentColor = darkColorScheme.primary,
  dismissActionContentColor = darkColorScheme.onSurface
)

private val darkSnackbarColors = SnackbarColors(
  color = darkColorScheme.surfaceVariant,
  contentColor = darkColorScheme.onSurfaceVariant,
  actionColor = darkColorScheme.primary,
  actionContentColor = darkColorScheme.primary,
  dismissActionContentColor = darkColorScheme.onSurfaceVariant
)

@Composable
fun SignalTheme(
  isDarkMode: Boolean = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
  incognitoKeyboardEnabled: Boolean = CoreUiDependencies.isIncognitoKeyboardEnabled,
  content: @Composable () -> Unit
) {
  val extendedColors = if (isDarkMode) darkExtendedColors else lightExtendedColors
  val snackbarColors = if (isDarkMode) darkSnackbarColors else lightSnackbarColors

  ProvideIncognitoKeyboard(enabled = incognitoKeyboardEnabled) {
    CompositionLocalProvider(LocalExtendedColors provides extendedColors, LocalSnackbarColors provides snackbarColors) {
      MaterialTheme(
        colorScheme = if (isDarkMode) darkColorScheme else lightColorScheme,
        typography = typography,
        content = content
      )
    }
  }
}

/**
 * Applies the light color scheme to [content] regardless of the ambient theme, leaving typography and shapes untouched.
 */
@Composable
fun ForceLightColors(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalExtendedColors provides lightExtendedColors) {
    MaterialTheme(
      colorScheme = lightColorScheme,
      content = content
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun TypographyPreview() {
  SignalTheme(
    isDarkMode = false,
    incognitoKeyboardEnabled = false
  ) {
    Column {
      Text(
        text = "Headline Small",
        style = MaterialTheme.typography.headlineLarge
      )
      Text(
        text = "Headline Small",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Headline Small",
        style = MaterialTheme.typography.headlineSmall
      )
      Text(
        text = "Title Large",
        style = MaterialTheme.typography.titleLarge
      )
      Text(
        text = "Title Medium",
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = "Title Small",
        style = MaterialTheme.typography.titleSmall
      )
      Text(
        text = "Body Large",
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = "Body Medium",
        style = MaterialTheme.typography.bodyMedium
      )
      Text(
        text = "Body Small",
        style = MaterialTheme.typography.bodySmall
      )
      Text(
        text = "Label Large",
        style = MaterialTheme.typography.labelLarge
      )
      Text(
        text = "Label Medium",
        style = MaterialTheme.typography.labelMedium
      )
      Text(
        text = "Label Small",
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}

object SignalTheme {
  val colors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
}
