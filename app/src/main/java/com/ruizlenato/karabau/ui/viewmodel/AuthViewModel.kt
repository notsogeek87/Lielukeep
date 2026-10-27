package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.local.SecureStorageUnavailableException
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.DEFAULT_SERVER_ADDRESS
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.isLoggedIn
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoginType {
    PASSWORD,
    API_KEY
}

enum class AuthState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

data class AuthUiState(
    val authState: AuthState = AuthState.IDLE,
    val loginType: LoginType = LoginType.PASSWORD,
    val email: String = "",
    val password: String = "",
    val apiKey: String = "",
    val errorMessage: String? = null,
    val isLoggedIn: Boolean = false,
    val serverAddress: String = DEFAULT_SERVER_ADDRESS
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)
    private val repository = KarabauRepository()

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            _uiState.update { currentState ->
                currentState.copy(
                    isLoggedIn = settings.isLoggedIn(),
                    serverAddress = settings.address
                )
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun onServerAddressChange(address: String) {
        _uiState.update { it.copy(serverAddress = address) }
        viewModelScope.launch {
            settingsDataStore.setServerAddress(address)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null, authState = AuthState.IDLE) }
    }

    fun login() {
        val currentState = _uiState.value

        if (currentState.serverAddress.isBlank()) {
            _uiState.update { it.copy(errorMessage = text(R.string.server_address_required)) }
            return
        }

        if (!currentState.serverAddress.startsWith("http://") &&
            !currentState.serverAddress.startsWith("https://")
        ) {
            _uiState.update { it.copy(errorMessage = text(R.string.server_address_invalid_scheme)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.LOADING) }

            val settings = settingsDataStore.settingsFlow.first()
            val loginSettings = settings.copy(address = currentState.serverAddress)
            repository.configure(loginSettings)

            val result = when (currentState.loginType) {
                LoginType.PASSWORD -> {
                    if (currentState.email.isBlank() || currentState.password.isBlank()) {
                        _uiState.update {
                            it.copy(
                                authState = AuthState.ERROR,
                                errorMessage = text(R.string.error_email_password_required)
                            )
                        }
                        return@launch
                    }
                    repository.exchangeKey(currentState.email, currentState.password)
                }

                LoginType.API_KEY -> {
                    if (currentState.apiKey.isBlank()) {
                        _uiState.update {
                            it.copy(
                                authState = AuthState.ERROR,
                                errorMessage = text(R.string.error_api_key_required)
                            )
                        }
                        return@launch
                    }
                    repository.validateKey(currentState.apiKey)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    val (apiKey, apiKeyId) = when (currentState.loginType) {
                        LoginType.PASSWORD -> {
                            val data = result.data as? ExchangeKeyResponse
                            if (data == null) {
                                _uiState.update {
                                    it.copy(
                                        authState = AuthState.ERROR,
                                        errorMessage = text(R.string.error_unexpected_response)
                                    )
                                }
                                return@launch
                            }
                            data.key to data.id
                        }

                        LoginType.API_KEY -> currentState.apiKey to null
                    }
                    try {
                        settingsDataStore.setServerAddress(currentState.serverAddress)
                        settingsDataStore.saveCredentials(apiKey, apiKeyId)
                    } catch (e: SecureStorageUnavailableException) {
                        _uiState.update {
                            it.copy(
                                authState = AuthState.ERROR,
                                errorMessage = text(R.string.error_secure_storage_unavailable)
                            )
                        }
                        return@launch
                    }
                    repository.configure(
                        settings.copy(address = currentState.serverAddress, apiKey = apiKey, apiKeyId = apiKeyId)
                    )
                    _uiState.update {
                        it.copy(
                            authState = AuthState.SUCCESS,
                            isLoggedIn = true
                        )
                    }
                }

                is ApiResult.Error -> {
                    val errorMsg = when (result.code) {
                        "UNAUTHORIZED" -> if (currentState.loginType == LoginType.PASSWORD)
                            text(R.string.error_wrong_credentials) else text(R.string.error_invalid_api_key)

                        "FORBIDDEN" -> text(R.string.error_password_auth_disabled)
                        "RATE_LIMIT" -> text(R.string.error_rate_limit)
                        else -> result.message
                    }
                    _uiState.update {
                        it.copy(
                            authState = AuthState.ERROR,
                            errorMessage = errorMsg
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            authState = AuthState.ERROR,
                            errorMessage = text(R.string.error_network, result.message)
                        )
                    }
                }
            }
        }
    }

}
