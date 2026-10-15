package com.ruizlenato.karabau.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.ui.viewmodel.SyncFailure

/**
 * Écran d'état (vide ou erreur) : grande icône dans un disque, titre, phrase d'aide, action. Remplace les textes gris
 * isolés : on comprend ce qui se passe et quoi faire. Défile pour rester lisible sur petit écran ou grande police.
 */
@Composable
internal fun StatePanel(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    detail: String? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAction, modifier = Modifier.height(48.dp)) {
                    Text(actionLabel)
                }
            }
            if (!detail.isNullOrBlank()) {
                ErrorDetail(detail)
            }
        }
    }
}

/** Détail brut du serveur, replié par défaut : indispensable pour comprendre une panne, mais pas pour tout le monde. */
@Composable
private fun ErrorDetail(detail: String) {
    var expanded by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.error_hide_detail else R.string.error_show_detail))
    }
    if (expanded) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = detail.take(300),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/** Erreur de chargement : titre propre à l'écran, cause en une phrase, détail du serveur replié, bouton Réessayer. */
@Composable
internal fun ErrorState(
    title: String,
    detail: String?,
    onRetry: () -> Unit,
    isNetwork: Boolean = false,
    modifier: Modifier = Modifier
) {
    StatePanel(
        icon = if (isNetwork) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
        title = if (isNetwork) stringResource(R.string.error_server_unreachable) else title,
        message = stringResource(if (isNetwork) R.string.error_server_unreachable_hint else R.string.error_server_refused_hint),
        isError = true,
        actionLabel = stringResource(R.string.retry),
        onAction = onRetry,
        detail = detail,
        modifier = modifier
    )
}

/** Bandeau affiché au-dessus de la liste quand le serveur ne répond pas : la copie locale reste utilisable. */
@Composable
internal fun SyncFailureBanner(
    failure: SyncFailure,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(
                    if (failure == SyncFailure.Network) R.string.sync_offline_banner else R.string.sync_server_error_banner
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Squelette de la liste pendant le premier chargement : on voit la forme de la page avant les données. */
@Composable
internal fun LinkSkeletonList(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonPulse"
    )
    val block = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier
            .fillMaxSize()
            .alpha(pulse)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(7) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(64.dp).background(block, RoundedCornerShape(14.dp)))
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(0.85f).height(14.dp).background(block, RoundedCornerShape(7.dp)))
                        Box(Modifier.fillMaxWidth(0.55f).height(12.dp).background(block, RoundedCornerShape(6.dp)))
                    }
                }
            }
        }
    }
}
