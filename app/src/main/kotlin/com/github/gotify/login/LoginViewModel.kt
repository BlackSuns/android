package com.github.gotify.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.github.gotify.Settings
import com.github.gotify.api.Callback
import com.github.gotify.api.ClientFactory
import com.github.gotify.client.ApiClient
import com.github.gotify.client.api.ClientApi
import com.github.gotify.client.api.UserApi
import com.github.gotify.client.model.ClientParams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal class LoginViewModel(private val settings: Settings) : ViewModel() {

    private val _state = MutableLiveData<LoginState>(LoginState.UrlInput)
    val state: LiveData<LoginState> = _state

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    var gotifyVersion: String? = null
        private set

    private var authenticatedClient: ApiClient? = null

    fun invalidateUrl() {
        gotifyVersion = null
        _state.value = LoginState.UrlInput
    }

    fun checkUrl(url: String) {
        _state.value = LoginState.CheckingUrl
        settings.url = url

        try {
            ClientFactory.infoApi(settings, settings.sslSettings(), url)
                .version
                .enqueue(
                    Callback.call(
                        onSuccess = Callback.SuccessBody { version ->
                            gotifyVersion = version.version
                            _state.value = LoginState.Ready
                        },
                        onError = { exception ->
                            _state.value = LoginState.UrlInput
                            _events.trySend(
                                LoginEvent.VersionError(url, exception.code)
                            )
                        }
                    )
                )
        } catch (e: Exception) {
            _state.value = LoginState.UrlInput
            _events.trySend(
                LoginEvent.VersionException(url, e.message ?: "")
            )
        }
    }

    fun login(username: String, password: String) {
        _state.value = LoginState.LoggingIn

        val client = ClientFactory.basicAuth(settings, settings.sslSettings(), username, password)
        authenticatedClient = client
        client.createService(UserApi::class.java)
            .currentUser()
            .enqueue(
                Callback.call(
                    onSuccess = {
                        _state.value = LoginState.WaitingForClientName
                        _events.trySend(LoginEvent.ShowClientNameDialog)
                    },
                    onError = {
                        authenticatedClient = null
                        _state.value = LoginState.Ready
                        _events.trySend(LoginEvent.InvalidCredentials)
                    }
                )
            )
    }

    fun createClient(name: String) {
        val client = authenticatedClient ?: return
        _state.value = LoginState.CreatingClient

        client.createService(ClientApi::class.java)
            .createClient(ClientParams().name(name))
            .enqueue(
                Callback.call(
                    onSuccess = Callback.SuccessBody { c ->
                        settings.token = c.token
                        _events.trySend(LoginEvent.LoginSuccess)
                    },
                    onError = {
                        _state.value = LoginState.Ready
                        _events.trySend(LoginEvent.ClientCreationFailed)
                    }
                )
            )
    }

    fun cancelClientCreation() {
        authenticatedClient = null
        _state.value = LoginState.Ready
    }

    class Factory(private val settings: Settings) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(settings) as T
        }
    }
}
