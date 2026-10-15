package com.lielu.keep.data.karakeep

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Adresse du serveur et clé d'API. Volontairement hors DataStore et hors sauvegarde : la clé est un secret,
 * elle ne doit figurer ni dans les fichiers de sauvegarde ni dans la sauvegarde automatique d'Android
 * (les règles de `res/xml` n'incluent que `database/` et `datastore/`).
 */
interface ConnectionStore {
    val connection: Flow<KarakeepConnection?>

    fun current(): KarakeepConnection?

    fun save(connection: KarakeepConnection)

    fun clear()

    var lastSyncAt: Long?
}

class SharedPrefsConnectionStore(
    context: Context,
) : ConnectionStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val connection: Flow<KarakeepConnection?> = state

    override fun current(): KarakeepConnection? = state.value

    override fun save(connection: KarakeepConnection) {
        prefs
            .edit()
            .putString(KEY_URL, connection.baseUrl)
            .putString(KEY_API_KEY, connection.apiKey)
            .apply()
        state.value = connection
    }

    override fun clear() {
        prefs
            .edit()
            .remove(KEY_URL)
            .remove(KEY_API_KEY)
            .remove(KEY_LAST_SYNC)
            .apply()
        state.value = null
    }

    override var lastSyncAt: Long?
        get() = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }
        set(value) {
            prefs.edit().putLong(KEY_LAST_SYNC, value ?: 0L).apply()
        }

    private fun read(): KarakeepConnection? {
        val url = prefs.getString(KEY_URL, null)
        val key = prefs.getString(KEY_API_KEY, null)
        return if (url.isNullOrBlank() || key.isNullOrBlank()) null else KarakeepConnection(url, key)
    }

    private companion object {
        const val FILE = "karakeep_connection"
        const val KEY_URL = "url"
        const val KEY_API_KEY = "api_key"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
