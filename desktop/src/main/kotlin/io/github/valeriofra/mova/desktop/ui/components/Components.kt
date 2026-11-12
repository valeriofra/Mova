package io.github.valeriofra.mova.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.desktop.resources.Res
import io.github.valeriofra.mova.desktop.resources.mova_logo
import io.github.valeriofra.mova.desktop.ui.theme.MovaColors
import org.jetbrains.compose.resources.painterResource

/**
 * Coluna com rolagem e barra de rolagem visível à direita (padrão no computador).
 * O conteúdo reserva espaço para a barra não cobrir os cartões.
 */
@Composable
fun ScrollArea(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().verticalScroll(state).padding(end = SCROLLBAR_GUTTER),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
        VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/** Barra de rolagem para listas (LazyColumn), sobreposta à direita. */
@Composable
fun BoxScope.ListScrollbar(state: LazyListState) {
    VerticalScrollbar(rememberScrollbarAdapter(state), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
}

/** Espaço reservado à direita para a barra de rolagem. */
val SCROLLBAR_GUTTER = 14.dp

/** Aparência da barra de rolagem nas cores do tema. */
@Composable
fun movaScrollbarStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 40.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 250,
    unhoverColor = MovaColors.TextSecondary.copy(alpha = 0.45f),
    hoverColor = MovaColors.TextSecondary.copy(alpha = 0.8f),
)

/** Margem lateral das telas (menor em janelas estreitas). */
val LocalScreenPadding = staticCompositionLocalOf { 32.dp }

/** Janela AWT do app (pai das janelas do sistema, como a de escolher pasta). */
val LocalAwtWindow = staticCompositionLocalOf<java.awt.Window?> { null }

/** Carregador de capas disponível para toda a interface. */
val LocalThumbnails = staticCompositionLocalOf<ThumbnailLoader?> { null }

@Composable
fun MovaLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(Res.drawable.mova_logo), contentDescription = null, modifier = modifier.size(size))
}

@Composable
fun MovaWordmark(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MovaLogo(28.dp)
        Spacer(Modifier.width(10.dp))
        Text("MOVA", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp, color = MovaColors.TextPrimary)
    }
}

/** Ação principal da tela. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) MovaColors.PrimaryGradient else Brush.linearGradient(listOf(MovaColors.SurfaceHighest, MovaColors.SurfaceHighest)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) Color.White else MovaColors.TextTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ação secundária, com contorno. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .border(1.dp, MovaColors.OutlineStrong, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) MovaColors.TextPrimary else MovaColors.TextTertiary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Text(text, color = if (enabled) MovaColors.Accent else MovaColors.TextTertiary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun MovaCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (highlighted) MovaColors.Highlight else MovaColors.Surface)
            .border(1.dp, if (highlighted) MovaColors.BlueBright else MovaColors.Outline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MovaColors.TextSecondary, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MovaColors.TextPrimary, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(MovaColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MovaColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextSecondary, modifier = Modifier.widthIn(max = 420.dp))
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun MovaProgressBar(progress: Float?, modifier: Modifier = Modifier, color: Color = MovaColors.BlueBright) {
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MovaColors.SurfaceHighest,
            strokeCap = StrokeCap.Round,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            color = color,
            trackColor = MovaColors.SurfaceHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun InfoBanner(icon: ImageVector, text: String, modifier: Modifier = Modifier, color: Color = MovaColors.Accent) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextPrimary)
    }
}

fun mediaKindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Rounded.Movie
    MediaKind.AUDIO -> Icons.Rounded.AudioFile
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.DOCUMENT -> Icons.Rounded.Description
    MediaKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

/** Capa remota (quando houver) ou o ícone do tipo de mídia. */
@Composable
fun MediaThumbnail(url: String?, kind: MediaKind, modifier: Modifier = Modifier, width: Dp = 96.dp, height: Dp = 54.dp) {
    val loader = LocalThumbnails.current
    var image by remember(url) { mutableStateOf<ImageBitmap?>(url?.let { loader?.cached(it) }) }
    LaunchedEffect(url) {
        if (image == null && url != null && loader != null) image = loader.load(url)
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.size(width, height).clip(shape).background(MovaColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
        } else {
            Icon(mediaKindIcon(kind), contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(minOf(width, height) * 0.5f))
        }
    }
}

@Composable
fun TitleAndSubtitle(title: String, subtitle: String?, modifier: Modifier = Modifier, maxTitleLines: Int = 1) {
    Column(modifier) {
        Text(
            title, style = MaterialTheme.typography.titleSmall, color = MovaColors.TextPrimary,
            maxLines = maxTitleLines, overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MovaColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MovaChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MovaColors.Surface,
            labelColor = MovaColors.TextSecondary,
            selectedContainerColor = MovaColors.Blue.copy(alpha = 0.22f),
            selectedLabelColor = MovaColors.Accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MovaColors.Outline,
            selectedBorderColor = MovaColors.BlueBright,
        ),
    )
}

@Composable
fun MovaSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = MovaColors.Blue,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = MovaColors.SurfaceHighest,
            uncheckedThumbColor = MovaColors.TextSecondary,
            uncheckedBorderColor = MovaColors.OutlineStrong,
        ),
    )
}
