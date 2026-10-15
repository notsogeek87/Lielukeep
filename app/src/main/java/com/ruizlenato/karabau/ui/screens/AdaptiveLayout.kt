package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Largeur à partir de laquelle l'écran est « large » (Fold ouvert, tablette) : rail de navigation et liste en grille. */
internal const val WIDE_SCREEN_DP = 600

@Composable
internal fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP

/**
 * Limite la largeur d'un écran de formulaire ou de réglages et le centre : sur un grand écran, des champs de 800 dp
 * de large sont pénibles à lire et à viser.
 */
@Composable
internal fun ReadableWidth(maxWidth: Dp = 640.dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
