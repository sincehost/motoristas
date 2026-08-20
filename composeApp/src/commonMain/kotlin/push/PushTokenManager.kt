package push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Guarda o último token FCM conhecido pro aparelho atual. Alimentado pelo
 * lado nativo (Android: FirebaseMessagingService.onNewToken; iOS:
 * MessagingDelegate.didReceiveRegistrationToken via PushTokenBridge), lido
 * pela tela de login e por App.kt pra registrar/re-registrar com o backend.
 */
object PushTokenManager {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token

    fun setToken(novoToken: String) {
        _token.value = novoToken
    }

    fun tokenAtual(): String? = _token.value
}
