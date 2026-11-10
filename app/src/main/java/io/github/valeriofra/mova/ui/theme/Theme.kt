package io.github.valeriofra.mova.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta do MOVA (Design System). Referências do planejamento:
 * fundo #0B0F1A, azul #2563EB, ciano #06B6D4.
 */
object MovaColors {
    val Background = Color(0xFF0B0F1A)
    val Surface = Color(0xFF111827)
    val SurfaceHigh = Color(0xFF162033)
    val SurfaceHighest = Color(0xFF1D2940)
    val Outline = Color(0xFF243049)
    val OutlineStrong = Color(0xFF34425E)

    val Blue = Color(0xFF2563EB)
    val BlueDeep = Color(0xFF1D4ED8)
    val BlueBright = Color(0xFF3B82F6)

    /** Azul claro para textos e ícones de destaque (contraste ≥ 4.5:1 no fundo). */
    val Accent = Color(0xFF6EA8FF)
    val Cyan = Color(0xFF06B6D4)
    val CyanLight = Color(0xFF22D3EE)

    val TextPrimary = Color(0xFFE8ECF4)
    val TextSecondary = Color(0xFF94A3B8)
    val TextTertiary = Color(0xFF6B7A90)

    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)

    /** Gradiente do botão principal (texto branco mantém contraste ≥ 5:1). */
    val PrimaryGradient = Brush.horizontalGradient(listOf(Blue, BlueDeep))

    /** Gradiente da marca, usado em ícones e destaques. */
    val BrandGradient = Brush.linearGradient(listOf(CyanLight, BlueBright, Blue))
}

private val colorScheme = darkColorScheme(
    primary = MovaColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF15295A),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = MovaColors.Cyan,
    onSecondary = MovaColors.Background,
    secondaryContainer = Color(0xFF113B48),
    onSecondaryContainer = Color(0xFFCFF6FF),
    tertiary = MovaColors.Accent,
    onTertiary = MovaColors.Background,
    background = MovaColors.Background,
    onBackground = MovaColors.TextPrimary,
    surface = MovaColors.Background,
    onSurface = MovaColors.TextPrimary,
    surfaceVariant = MovaColors.SurfaceHigh,
    onSurfaceVariant = MovaColors.TextSecondary,
    surfaceContainerLowest = MovaColors.Background,
    surfaceContainerLow = MovaColors.Surface,
    surfaceContainer = MovaColors.Surface,
    surfaceContainerHigh = MovaColors.SurfaceHigh,
    surfaceContainerHighest = MovaColors.SurfaceHighest,
    outline = MovaColors.OutlineStrong,
    outlineVariant = MovaColors.Outline,
    error = MovaColors.Error,
    onError = MovaColors.Background,
    errorContainer = Color(0xFF3B1418),
    onErrorContainer = Color(0xFFFFDADA),
    inverseSurface = MovaColors.TextPrimary,
    inverseOnSurface = MovaColors.Background,
    inversePrimary = MovaColors.Blue,
    scrim = Color(0xCC000000),
)

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
fun MovaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
