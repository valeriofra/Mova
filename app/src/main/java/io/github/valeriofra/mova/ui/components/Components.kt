package io.github.valeriofra.mova.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.MovaApplication
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.core.model.MediaKind
import io.github.valeriofra.mova.ui.theme.MovaColors
import io.github.valeriofra.mova.ui.theme.WordmarkStyle

/** Símbolo do MOVA. */
@Composable
fun MovaLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_mova_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** Símbolo + nome, usado no cabeçalho. */
@Composable
fun MovaWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        MovaLogo(size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(text = "MOVA", style = WordmarkStyle, color = MovaColors.TextPrimary)
    }
}

/** Botão da ação principal da tela (uma por tela). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .background(
                    brush = if (enabled) MovaColors.PrimaryGradient else SolidBrush(MovaColors.SurfaceHigh),
                    shape = shape,
                )
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val contentColor = if (enabled) Color.White else MovaColors.TextTertiary
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text = text, style = MaterialTheme.typography.titleMedium, color = contentColor)
            }
        }
    }
}

@Suppress("FunctionName")
private fun SolidBrush(color: Color): Brush = Brush.linearGradient(listOf(color, color))

/** Ação secundária, com contorno. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MovaColors.OutlineStrong),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = MovaColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Botão de texto com a cor de destaque acessível. */
@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = MovaColors.Accent, style = MaterialTheme.typography.labelLarge)
    }
}

/** Cartão padrão: superfície escura com borda sutil. */
@Composable
fun MovaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, if (highlighted) MovaColors.BlueBright else MovaColors.Outline)
    val color = if (highlighted) Color(0xFF12203D) else MovaColors.Surface
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) { content() }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) { content() }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MovaColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** Estado vazio útil: explica o que acontece aqui e sugere a próxima ação. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MovaColors.SurfaceHigh)
                .border(1.dp, MovaColors.Outline, CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = MovaColors.Accent, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MovaColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MovaColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

/** Selo de estado com ícone + texto (não depende só de cor). */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MovaProgressBar(progress: Float?, modifier: Modifier = Modifier, color: Color = MovaColors.BlueBright) {
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MovaColors.SurfaceHighest,
            strokeCap = StrokeCap.Round,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = MovaColors.SurfaceHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.VIDEO -> Icons.Rounded.Movie
    MediaKind.AUDIO -> Icons.Rounded.Audiotrack
    MediaKind.IMAGE -> Icons.Rounded.Image
    MediaKind.DOCUMENT -> Icons.Rounded.Description
    MediaKind.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

/** Ícone do tipo de mídia em um quadrado com gradiente da marca. */
@Composable
fun MediaKindIcon(kind: MediaKind, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(MovaColors.Blue.copy(alpha = 0.35f), MovaColors.Cyan.copy(alpha = 0.18f)),
                ),
            ),
    ) {
        Icon(kind.icon(), contentDescription = null, tint = MovaColors.TextPrimary, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * Miniatura do arquivo salvo ([uri]) ou da capa informada pela plataforma
 * ([remoteUrl]), com o ícone do tipo como alternativa.
 */
@Composable
fun MediaThumbnail(
    uri: String?,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    remoteUrl: String? = null,
) {
    val context = LocalContext.current
    val loader = (context.applicationContext as? MovaApplication)?.container?.thumbnails
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(uri, remoteUrl) {
        mutableStateOf<ImageBitmap?>(
            (loader?.cached(uri) ?: if (uri == null) loader?.cachedRemote(remoteUrl) else null)?.asImageBitmap(),
        )
    }
    LaunchedEffect(uri, remoteUrl) {
        if (bitmap == null && loader != null) {
            bitmap = when {
                uri != null -> loader.load(Uri.parse(uri), kind, sizePx)
                remoteUrl != null -> loader.loadRemote(remoteUrl, sizePx)
                else -> null
            }?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        MediaKindIcon(kind, modifier, size)
    }
}

/** Linha de texto principal + secundário, com reticências. */
@Composable
fun TitleAndSubtitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MovaColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MovaColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Faixa de aviso/informação. */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MovaColors.Accent,
    role: Role? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MovaColors.TextPrimary)
    }
}

/** Imagem carregada de uma URL (capas de vídeos), com conteúdo alternativo enquanto carrega. */
@Composable
fun RemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val loader = (context.applicationContext as? MovaApplication)?.container?.thumbnails
    var bitmap by remember(url) { mutableStateOf(loader?.cachedRemote(url)?.asImageBitmap()) }
    LaunchedEffect(url) {
        if (bitmap == null && url != null && loader != null) {
            bitmap = loader.loadRemote(url, REMOTE_IMAGE_SIZE_PX)?.asImageBitmap()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier) { fallback() }
    }
}

private const val REMOTE_IMAGE_SIZE_PX = 720
