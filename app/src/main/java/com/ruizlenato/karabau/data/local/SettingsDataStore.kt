package com.ruizlenato.karabau.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ruizlenato.karabau.data.model.DEFAULT_SERVER_ADDRESS
import com.ruizlenato.karabau.data.model.ArchiveDisplayBehaviour
import com.ruizlenato.karabau.data.model.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "karabau_settings")

class SecureStorageUnavailableException(message: String) : Exception(message)

/**
 * Réglages de l'app. Les secrets (clé API, en-têtes personnalisés, souvent des jetons d'accès) vivent dans
 * EncryptedSharedPreferences ; le reste dans DataStore. Seule la connexion écrit la clé ([saveCredentials]) :
 * les autres écritures ne touchent qu'à leur propre réglage, pour qu'aucune ne puisse rétablir une clé effacée
 * par une déconnexion.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private val ADDRESS = stringPreferencesKey("address")
        private val SHOW_LIST_ICONS = booleanPreferencesKey("show_list_icons")
        private val OPEN_LINKS_IN_APP = booleanPreferencesKey("open_links_in_app")
        /** Ancien emplacement, en clair : lu une dernière fois puis déplacé dans le stockage chiffré. */
        private val LEGACY_CUSTOM_HEADERS = stringPreferencesKey("custom_headers")
        private val ARCHIVE_DISPLAY_BEHAVIOUR = stringPreferencesKey("archive_display_behaviour")
        private val LAST_FOLDER_ID = stringPreferencesKey("last_folder_id")
        /** Change à chaque écriture chiffrée : fait réémettre [settingsFlow], qui relit le stockage chiffré. */
        private val SECURE_REVISION = stringPreferencesKey("secure_revision")

        private const val ENCRYPTED_PREFS_NAME = "karabau_secure"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_KEY_ID = "api_key_id"
        private const val KEY_CUSTOM_HEADERS = "custom_headers"
    }

    private val encryptedPrefs: SharedPreferences? by lazy {
        getOrCreateEncryptedPrefs()
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getOrCreateEncryptedPrefs(): SharedPreferences? {
        return runCatching { createEncryptedPrefs() }
            .recoverCatching {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                createEncryptedPrefs()
            }
            .getOrNull()
    }

    private fun secureString(key: String): String? =
        runCatching { encryptedPrefs?.getString(key, null)?.takeIf { it.isNotBlank() } }.getOrNull()

    private fun decodeHeaders(raw: String?): Map<String, String>? =
        raw?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { preferences ->
        val secureHeaders = decodeHeaders(secureString(KEY_CUSTOM_HEADERS))
        Settings(
            apiKey = secureString(KEY_API_KEY),
            apiKeyId = secureString(KEY_API_KEY_ID),
            address = preferences[ADDRESS] ?: DEFAULT_SERVER_ADDRESS,
            showListIcons = preferences[SHOW_LIST_ICONS] ?: true,
            openLinksInApp = preferences[OPEN_LINKS_IN_APP] ?: true,
            customHeaders = secureHeaders ?: decodeHeaders(preferences[LEGACY_CUSTOM_HEADERS]) ?: emptyMap(),
            archiveDisplayBehaviour = runCatching {
                preferences[ARCHIVE_DISPLAY_BEHAVIOUR]?.let { ArchiveDisplayBehaviour.valueOf(it) }
            }.getOrNull() ?: ArchiveDisplayBehaviour.HIDE,
            lastFolderId = preferences[LAST_FOLDER_ID]
        )
    }

    private fun requireSecurePrefs(): SharedPreferences =
        encryptedPrefs ?: throw SecureStorageUnavailableException(
            "Encrypted storage is unavailable; refusing to store secrets in plaintext"
        )

    private suspend fun bumpSecureRevision() {
        context.dataStore.edit { it[SECURE_REVISION] = System.nanoTime().toString() }
    }

    /** Seul point d'écriture de la clé API (connexion). */
    suspend fun saveCredentials(apiKey: String, apiKeyId: String?) {
        withContext(Dispatchers.IO) {
            requireSecurePrefs().edit()
                .putString(KEY_API_KEY, apiKey)
                .putString(KEY_API_KEY_ID, apiKeyId.orEmpty())
                .commit()
        }
        context.dataStore.edit { preferences ->
            // Anciennes copies en clair éventuelles.
            preferences.remove(stringPreferencesKey("api_key"))
            preferences.remove(stringPreferencesKey("api_key_id"))
        }
        bumpSecureRevision()
    }

    /** Chiffrés quand c'est possible ; sinon (appareil sans stockage chiffré) en clair, pour que l'accès marche. */
    suspend fun setCustomHeaders(headers: Map<String, String>) {
        val encoded = Json.encodeToString(headers)
        val securePrefs = withContext(Dispatchers.IO) { encryptedPrefs }
        if (securePrefs == null) {
            context.dataStore.edit { it[LEGACY_CUSTOM_HEADERS] = encoded }
            return
        }
        withContext(Dispatchers.IO) { securePrefs.edit().putString(KEY_CUSTOM_HEADERS, encoded).commit() }
        context.dataStore.edit { it.remove(LEGACY_CUSTOM_HEADERS) }
        bumpSecureRevision()
    }

    /** Déplace les en-têtes encore stockés en clair (anciennes versions) vers le stockage chiffré. */
    suspend fun migrateLegacyHeaders(current: Settings) {
        if (encryptedPrefs == null || secureString(KEY_CUSTOM_HEADERS) != null) return
        if (current.customHeaders.isEmpty()) return
        setCustomHeaders(current.customHeaders)
    }

    suspend fun setServerAddress(address: String) {
        context.dataStore.edit { it[ADDRESS] = address }
    }

    suspend fun setShowListIcons(show: Boolean) {
        context.dataStore.edit { it[SHOW_LIST_ICONS] = show }
    }

    suspend fun setOpenLinksInApp(inApp: Boolean) {
        context.dataStore.edit { it[OPEN_LINKS_IN_APP] = inApp }
    }

    suspend fun setArchiveDisplayBehaviour(behaviour: ArchiveDisplayBehaviour) {
        context.dataStore.edit { it[ARCHIVE_DISPLAY_BEHAVIOUR] = behaviour.name }
    }

    /** `null` = « Aucun dossier ». */
    suspend fun setLastFolderId(folderId: String?) {
        context.dataStore.edit { preferences ->
            if (folderId == null) preferences.remove(LAST_FOLDER_ID) else preferences[LAST_FOLDER_ID] = folderId
        }
    }

    /** Déconnexion : efface la clé et ce qui est propre au compte (dernier dossier). Adresse et en-têtes restent. */
    suspend fun clearAuth() {
        withContext(Dispatchers.IO) {
            encryptedPrefs?.edit()
                ?.remove(KEY_API_KEY)
                ?.remove(KEY_API_KEY_ID)
                ?.commit()
        }

        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey("api_key"))
            preferences.remove(stringPreferencesKey("api_key_id"))
            preferences.remove(LAST_FOLDER_ID)
        }
        bumpSecureRevision()
    }
}
