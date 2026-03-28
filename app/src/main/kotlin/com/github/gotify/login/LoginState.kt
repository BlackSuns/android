package com.github.gotify.login

sealed class LoginState {
    object UrlInput : LoginState()
    object CheckingUrl : LoginState()
    object Ready : LoginState()
    object LoggingIn : LoginState()
    object WaitingForClientName : LoginState()
    object CreatingClient : LoginState()
}

sealed class LoginEvent {
    object LoginSuccess : LoginEvent()
    object ShowClientNameDialog : LoginEvent()
    data class VersionError(val url: String, val code: Int) : LoginEvent()
    data class VersionException(val url: String, val message: String) : LoginEvent()
    object InvalidCredentials : LoginEvent()
    object ClientCreationFailed : LoginEvent()
}
