package util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity

/**
 * BiometricAuth — Implementação Android.
 *
 * Usa AndroidX BiometricPrompt (compatível com Android 6+).
 * Suporta impressão digital, reconhecimento facial e PIN/padrão do dispositivo.
 *
 * IMPORTANTE: Requer dependência no build.gradle:
 *   implementation("androidx.biometric:biometric:1.2.0-alpha05")
 *
 * A Activity deve ser ComponentActivity (que herda de FragmentActivity).
 */
actual object BiometricAuth {

    private var activity: ComponentActivity? = null

    /**
     * Chamar no onCreate da MainActivity para registrar a Activity.
     */
    fun init(activity: ComponentActivity) {
        this.activity = activity
    }

    actual fun isAvailable(): Boolean {
        val ctx = activity ?: return false
        val biometricManager = BiometricManager.from(ctx)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    actual fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNotAvailable: () -> Unit
    ) {
        val act = activity
        if (act == null) {
            onNotAvailable()
            return
        }

        if (!isAvailable()) {
            onNotAvailable()
            return
        }

        val executor = ContextCompat.getMainExecutor(act)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    onError("Cancelado pelo usuário")
                } else {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Não chamar onError aqui — o prompt permite múltiplas tentativas
            }
        }

        val biometricPrompt = BiometricPrompt(act as androidx.fragment.app.FragmentActivity, executor, callback)

        // Permitir biometria OU PIN/padrão do dispositivo como fallback
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError("Erro ao iniciar biometria: ${e.message}")
        }
    }
}
