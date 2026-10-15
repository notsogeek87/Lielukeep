package com.lielu.keep.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.keep.data.karakeep.ConnectionStore
import com.lielu.keep.data.karakeep.KarakeepConnection
import com.lielu.keep.data.karakeep.KarakeepException
import com.lielu.keep.data.karakeep.KarakeepRemote
import com.lielu.keep.data.karakeep.normalizeServerUrl
import com.lielu.keep.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerUiState(
    val url: String = "",
    val apiKey: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    /** Résultat du dernier test ou de la dernière action, affiché sous les champs. */
    val message: String? = null,
    val messageIsError: Boolean = false,
)

class ServerViewModel(
    private val connections: ConnectionStore,
    private val remote: KarakeepRemote,
    private val syncNow: suspend () -> Boolean,
    val syncStatus: StateFlow<SyncStatus>,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            connections.current().let {
                ServerUiState(url = it?.baseUrl.orEmpty(), apiKey = it?.apiKey.orEmpty(), connected = it != null)
            },
        )
    val state: StateFlow<ServerUiState> = _state

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, message = null) }

    fun onApiKeyChange(value: String) = _state.update { it.copy(apiKey = value, message = null) }

    /** Teste l'adresse et la clé ; ne les garde (et ne synchronise) que si le serveur les accepte. */
    fun onSaveAndTest() {
        val current = _state.value
        val url = normalizeServerUrl(current.url)
        val key = current.apiKey.trim()
        if (url == null) {
            _state.update {
                it.copy(
                    message = "Adresse invalide : elle doit commencer par https://",
                    messageIsError = true,
                )
            }
            return
        }
        if (key.isEmpty()) {
            _state.update { it.copy(message = "Colle la clé d'API créée dans Karakeep.", messageIsError = true) }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val previous = connections.current()
            connections.save(KarakeepConnection(url, key))
            try {
                remote.checkConnection()
                _state.update {
                    it.copy(
                        url = url,
                        apiKey = key,
                        connected = true,
                        busy = false,
                        message = "Connecté.",
                        messageIsError = false,
                    )
                }
                syncNow()
            } catch (e: KarakeepException) {
                if (previous != null) connections.save(previous) else connections.clear()
                _state.update { it.copy(busy = false, message = e.message, messageIsError = true) }
            }
        }
    }

    fun onSyncNow() {
        viewModelScope.launch { syncNow() }
    }

    fun onDisconnect() {
        connections.clear()
        _state.value = ServerUiState(message = "Déconnecté. Les liens déjà copiés restent sur cet appareil.")
    }
}
