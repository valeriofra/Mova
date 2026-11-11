package io.github.valeriofra.mova.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.valeriofra.mova.R
import io.github.valeriofra.mova.ui.theme.MovaColors

/** Barra superior simples, com título e (opcional) voltar. */
@Composable
fun MovaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MovaColors.TextPrimary,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MovaColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Mensagem de estado (erro, aviso, informação) com ícone e ações. */
@Composable
fun StateMessage(
    icon: ImageVector,
    tint: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    details: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f))
                .border(1.dp, tint.copy(alpha = 0.35f), CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        }
        if (badge != null) {
            Spacer(Modifier.height(16.dp))
            StatusPill(text = badge, color = MovaColors.Accent)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
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
        if (!details.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    details.take(600),
                    style = MaterialTheme.typography.bodySmall,
                    color = MovaColors.TextTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Abre um link no app correspondente ou no navegador. Retorna false se não houver app. */
fun Context.openExternal(url: String): Boolean =
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
