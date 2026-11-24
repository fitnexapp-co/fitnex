package com.fitnex.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fitnex.core.designsystem.R

/**
 * Inter (SIL OFL 1.1, assets/licenses/inter-OFL.txt): a neutral grotesque close in feel to SF Pro.
 * One variable file serves every weight.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

/** Tabular figures so numbers in tiles, timers and charts do not jump as they change. */
private const val TABULAR = "tnum"

/**
 * One deliberate scale: large bold titles, semibold headings, prominent numbers, quiet secondary
 * text and small labels. Display sizes use slightly negative tracking, like system large titles.
 */
internal val FitnexTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.025).em, fontFeatureSettings = TABULAR),
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.022).em),
    headlineMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.02).em, fontFeatureSettings = TABULAR),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.012).em),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.006).em),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = (-0.006).em),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.004.em),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.04.em, fontFeatureSettings = TABULAR),
)

/**
 * User appearance choices (Settings → Apariencia). [liquidGlass] is off by default for performance;
 * [reduceMotion] also turns on when the system's animator scale is 0 ("Quitar animaciones").
 */
@Immutable
data class FitnexAppearance(
    val accent: Color = FitnexAccents.Default,
    val liquidGlass: Boolean = false,
    val reduceMotion: Boolean = false,
    /** Solid surfaces: no backdrop blur, no refraction, no blurred screen edges. */
    val reduceBlur: Boolean = false,
)

internal val LocalAppearance = staticCompositionLocalOf { FitnexAppearance() }

@Composable
fun FitnexTheme(
    darkTheme: Boolean = true, // spec: dark mode by default
    appearance: FitnexAppearance = FitnexAppearance(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    fun readSystemReduce() =
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    var systemReduce by remember(context) { androidx.compose.runtime.mutableStateOf(readSystemReduce()) }
    // Read again whenever the app comes back: turning "Quitar animaciones" on or off in Android's
    // settings applies without restarting FITNEX.
    androidx.lifecycle.compose.LifecycleResumeEffect(context) {
        systemReduce = readSystemReduce()
        onPauseOrDispose { }
    }
    // Battery saver on → no live blur/refraction (they cost GPU while scrolling), like "Reducir desenfoque".
    val powerSave = rememberPowerSaveMode()
    val effective = appearance.copy(
        reduceMotion = appearance.reduceMotion || systemReduce,
        reduceBlur = appearance.reduceBlur || powerSave,
    )
    // Accent changes glide (setting changes, picker drags) unless motion is reduced.
    val accent by animateColorAsState(
        appearance.accent,
        animationSpec = if (effective.reduceMotion) tween(0) else tween(320),
        label = "accent",
    )
    // The target (not the animating value) decides the style, so it doesn't flicker mid-transition.
    val colors = fitnexColors(darkTheme, accent, signature = appearance.accent == FitnexAccents.Default)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondary = colors.textSecondary,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
            surfaceContainerHigh = colors.surfaceRaised, surfaceContainer = colors.surface,
            outline = colors.glassBorder, error = colors.error,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            secondary = colors.textSecondary,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised, onSurfaceVariant = colors.textSecondary,
            surfaceContainerHigh = colors.surfaceRaised, surfaceContainer = colors.surface,
            outline = colors.glassBorder, error = colors.error,
        )
    }
    CompositionLocalProvider(LocalFitnexColors provides colors, LocalAppearance provides effective) {
        MaterialTheme(colorScheme = scheme, typography = FitnexTypography, content = content)
    }
}

/** True while the system battery saver is on; follows changes live. */
@Composable
internal fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager }
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(power?.isPowerSaveMode == true) }
    androidx.compose.runtime.DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                on = power?.isPowerSaveMode == true
            }
        }
        val filter = android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}

/** Accessor: `FitnexTheme.colors.accent`, `FitnexTheme.appearance.reduceMotion`. */
object FitnexTheme {
    val colors: FitnexColors
        @Composable @ReadOnlyComposable get() = LocalFitnexColors.current
    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val appearance: FitnexAppearance
        @Composable @ReadOnlyComposable get() = LocalAppearance.current
}
