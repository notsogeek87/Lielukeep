package com.ruizlenato.karabau.data.model

import kotlinx.serialization.Serializable

const val DEFAULT_SERVER_ADDRESS = "https://cloud.karakeep.app"

@Serializable
data class Settings(
    val apiKey: String? = null,
    val apiKeyId: String? = null,
    val address: String = DEFAULT_SERVER_ADDRESS,
    val imageQuality: Float = 0.2f,
    val theme: Theme = Theme.SYSTEM,
    val defaultBookmarkView: BookmarkView = BookmarkView.READER,
    val showNotes: Boolean = false,
    val showListIcons: Boolean = true,
    /** true = onglet intégré à l'app (Custom Tabs) ; false = navigateur par défaut du téléphone. */
    val openLinksInApp: Boolean = true,
    val keepScreenOnWhileReading: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
    val archiveDisplayBehaviour: ArchiveDisplayBehaviour = ArchiveDisplayBehaviour.HIDE,
    val readerFontSize: Int? = null,
    val readerLineHeight: Float? = null,
    val readerFontFamily: String? = null,
    /** Dernier dossier choisi à l'enregistrement d'un lien (présélectionné la fois suivante). */
    val lastFolderId: String? = null
)

enum class ArchiveDisplayBehaviour {
    SHOW, HIDE
}

enum class Theme {
    LIGHT, DARK, SYSTEM
}

enum class BookmarkView {
    READER, BROWSER, EXTERNAL_BROWSER
}

fun Settings.isLoggedIn(): Boolean = !apiKey.isNullOrBlank()
