package com.ruizlenato.karabau.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.ruizlenato.karabau.data.links.urlFromSharedText

/** Ouvre un lien http(s) selon le réglage : onglet intégré à l'app, ou navigateur par défaut du téléphone. */
fun openLink(context: Context, url: String, inApp: Boolean) {
    if (inApp) openInCustomTab(context, url) else openInExternalBrowser(context, url)
}

/** Ouvre un lien http(s) dans le navigateur par défaut du téléphone. */
fun openInExternalBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "http" && uri.scheme != "https") return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Ouvre un lien http(s) dans un onglet Chrome intégré (Custom Tabs) ; sans navigateur compatible, via ACTION_VIEW. */
fun openInCustomTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "http" && uri.scheme != "https") return
    runCatching {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    }.onFailure {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** URL http(s) présente dans le presse-papiers, nettoyée des paramètres de suivi, ou null. */
fun clipboardUrl(context: Context): String? {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return urlFromSharedText(clip.getItemAt(0).coerceToText(context)?.toString())
}

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun shareText(context: Context, text: String, subject: String?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    context.startActivity(Intent.createChooser(intent, null))
}
