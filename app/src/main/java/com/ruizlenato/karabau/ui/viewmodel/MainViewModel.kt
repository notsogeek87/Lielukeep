package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.LocalCacheManager
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.isLoggedIn
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MainUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val cacheManager = LocalCacheManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            _uiState.value = MainUiState(
                isLoading = false,
                isLoggedIn = settings.isLoggedIn()
            )
            runCatching { settingsDataStore.migrateLegacyHeaders(settings) }
        }
    }

    fun setLoggedIn(loggedIn: Boolean) {
        _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn)
    }

    /**
     * Déconnexion : révoque sur le serveur la clé créée à la connexion par mot de passe (sinon chaque connexion laisse
     * une clé « Mobile App » de plus), puis efface la clé et la copie locale des liens. Serveur injoignable : la
     * révocation est abandonnée après quelques secondes, la déconnexion se fait quand même.
     */
    suspend fun clearAuth() {
        val settings = settingsDataStore.settingsFlow.first()
        val apiKeyId = settings.apiKeyId
        if (settings.isLoggedIn() && apiKeyId != null) {
            withTimeoutOrNull(REVOKE_TIMEOUT_MS) {
                KarabauRepository().apply { configure(settings) }.revokeKey(apiKeyId)
            }
        }
        settingsDataStore.clearAuth()
        cacheManager.clearAll()
        _uiState.value = _uiState.value.copy(isLoggedIn = false)
    }

    private companion object {
        const val REVOKE_TIMEOUT_MS = 5_000L
    }
}
