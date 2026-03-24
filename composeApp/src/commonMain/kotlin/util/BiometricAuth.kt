package util

/**
 * BiometricAuth — expect/actual para autenticação biométrica.
 *
 * Android: BiometricPrompt (impressão digital, face, PIN do dispositivo)
 * iOS: LocalAuthentication (Face ID, Touch ID)
 *
 * Uso:
 *   BiometricAuth.authenticate(
 *       title = "Verificação de identidade",
 *       subtitle = "Use sua biometria para entrar",
 *       onSuccess = { /* logado */ },
 *       onError = { msg -> /* fallback para senha */ },
 *       onNotAvailable = { /* biometria não disponível, pular */ }
 *   )
 */
expect object BiometricAuth {

    /**
     * Verifica se biometria está disponível no dispositivo.
     */
    fun isAvailable(): Boolean

    /**
     * Solicita autenticação biométrica.
     *
     * @param title Título do diálogo
     * @param subtitle Texto descritivo
     * @param onSuccess Callback quando autenticação foi bem-sucedida
     * @param onError Callback quando falhou (msg com razão)
     * @param onNotAvailable Callback quando biometria não está configurada
     */
    fun authenticate(
        title: String = "Verificação de identidade",
        subtitle: String = "Use sua biometria para acessar",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNotAvailable: () -> Unit
    )
}
