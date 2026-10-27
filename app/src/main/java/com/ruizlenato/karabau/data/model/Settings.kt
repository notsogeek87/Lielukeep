package com.ruizlenato.karabau.data.model

import kotlinx.serialization.Serializable

const val DEFAULT_SERVER_ADDRESS = "https://cloud.karakeep.app"

@Serializable
data class Settings(
    val apiKey: String? = null,
    val apiKeyId: String? = null,
    val address: String = DEFAULT_SERVER_ADDRESS,
    val showListIcons: Boolean = true,
    /** true = onglet intégré à l'app (Custom Tabs) ; false = navigateur par défaut du téléphone. */
    val openLinksInApp: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap(),
    val archiveDisplayBehaviour: ArchiveDisplayBehaviour = ArchiveDisplayBehaviour.HIDE,
    /** Dernier dossier choisi à l'enregistrement d'un lien (présélectionné la fois suivante). */
    val lastFolderId: String? = null
)

enum class ArchiveDisplayBehaviour {
    SHOW, HIDE
}

fun Settings.isLoggedIn(): Boolean = !apiKey.isNullOrBlank()
