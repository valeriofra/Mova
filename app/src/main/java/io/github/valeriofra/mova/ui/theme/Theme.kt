package io.github.valeriofra.mova.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cores do MOVA para um tema (escuro ou claro). */
@Immutable
data class MovaPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val outline: Color,
    val outlineStrong: Color,
    val accent: Color,
    val cyan: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    /** Fundo de cartões selecionados. */
    val highlight: Color,
)

/**
 * Tema escuro (identidade principal do planejamento): fundo #0B0F1A,
 * azul #2563EB, ciano #06B6D4.
 */
val DarkPalette = MovaPalette(
    isDark = true,
    background = Color(0xFF0B0F1A),
    surface = Color(0xFF111827),
    surfaceHigh = Color(0xFF162033),
    surfaceHighest = Color(0xFF1D2940),
    outline = Color(0xFF243049),
    outlineStrong = Color(0xFF34425E),
    accent = Color(0xFF6EA8FF),
    cyan = Color(0xFF06B6D4),
    textPrimary = Color(0xFFE8ECF4),
    textSecondary = Color(0xFF94A3B8),
    textTertiary = Color(0xFF6B7A90),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFF87171),
    highlight = Color(0xFF12203D),
)

/** Tema claro: mesmos azuis da marca, textos escuros com contraste ≥ 4.5:1. */
val LightPalette = MovaPalette(
    isDark = false,
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEEF2F8),
    surfaceHighest = Color(0xFFE2E8F1),
    outline = Color(0xFFDCE3EE),
    outlineStrong = Color(0xFFB8C3D4),
    accent = Color(0xFF1D4ED8),
    cyan = Color(0xFF0891B2),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF64748B),
    success = Color(0xFF047857),
    warning = Color(0xFFB45309),
    error = Color(0xFFDC2626),
    highlight = Color(0xFFE8F0FE),
)

private val LocalMovaPalette = staticCompositionLocalOf { DarkPalette }

/** Paleta do MOVA (Design System). Os valores seguem o tema atual. */
object MovaColors {
    // Azuis da marca: iguais nos dois temas (texto branco sobre eles tem contraste ≥ 5:1).
    val Blue = Color(0xFF2563EB)
    val BlueDeep = Color(0xFF1D4ED8)
    val BlueBright = Color(0xFF3B82F6)
    val CyanLight = Color(0xFF22D3EE)

    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.isDark
    val Background: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.surface
    val SurfaceHigh: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.surfaceHigh
    val SurfaceHighest: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.surfaceHighest
    val Outline: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.outline
    val OutlineStrong: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.outlineStrong

    /** Azul para textos e ícones de destaque (contraste ≥ 4.5:1 no fundo do tema). */
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.accent
    val Cyan: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.cyan

    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.textSecondary
    val TextTertiary: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.textTertiary

    val Success: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.warning
    val Error: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.error
    val Highlight: Color @Composable @ReadOnlyComposable get() = LocalMovaPalette.current.highlight

    /** Gradiente do botão principal (texto branco mantém contraste ≥ 5:1). */
    val PrimaryGradient = Brush.horizontalGradient(listOf(Blue, BlueDeep))

    /** Gradiente da marca, usado em ícones e destaques. */
    val BrandGradient = Brush.linearGradient(listOf(CyanLight, BlueBright, Blue))
}

private fun colorSchemeFor(p: MovaPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = MovaColors.Blue,
        onPrimary = Color.White,
        primaryContainer = if (p.isDark) Color(0xFF15295A) else Color(0xFFDCE6FF),
        onPrimaryContainer = if (p.isDark) Color(0xFFDCE6FF) else Color(0xFF0B1F4D),
        secondary = p.cyan,
        onSecondary = if (p.isDark) p.background else Color.White,
        secondaryContainer = if (p.isDark) Color(0xFF113B48) else Color(0xFFCFF4FC),
        onSecondaryContainer = if (p.isDark) Color(0xFFCFF6FF) else Color(0xFF083344),
        tertiary = p.accent,
        onTertiary = if (p.isDark) p.background else Color.White,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.background,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceHigh,
        onSurfaceVariant = p.textSecondary,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.surfaceHigh,
        surfaceContainerHighest = p.surfaceHighest,
        outline = p.outlineStrong,
        outlineVariant = p.outline,
        error = p.error,
        onError = Color.White,
        inverseSurface = p.textPrimary,
        inverseOnSurface = p.background,
        inversePrimary = MovaColors.BlueBright,
        scrim = Color(0xCC000000),
    )
}

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Estilo do nome MOVA. */
val WordmarkStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp)

@Composable
fun MovaTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(LocalMovaPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette),
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
