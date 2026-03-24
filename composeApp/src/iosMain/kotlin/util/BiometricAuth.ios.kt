package util

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.Foundation.NSError
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

/**
 * BiometricAuth — Implementação iOS.
 *
 * Usa LocalAuthentication framework (Face ID, Touch ID).
 * Se biometria não está configurada, tenta Device Passcode como fallback.
 *
 * IMPORTANTE: Adicionar no Info.plist:
 *   <key>NSFaceIDUsageDescription</key>
 *   <string>Usamos Face ID para acesso rápido ao app</string>
 */
actual object BiometricAuth {

    @OptIn(ExperimentalForeignApi::class)
    actual fun isAvailable(): Boolean {
        val context = LAContext()
        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthenticationWithBiometrics,
                error = error.ptr
            ) || context.canEvaluatePolicy(
                LAPolicyDeviceOwnerAuthentication,
                error = error.ptr
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onNotAvailable: () -> Unit
    ) {
        if (!isAvailable()) {
            onNotAvailable()
            return
        }

        val context = LAContext()
        context.localizedFallbackTitle = "Usar senha"

        // Tentar biometria primeiro, com fallback para passcode do dispositivo
        val policy = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = error.ptr)) {
                LAPolicyDeviceOwnerAuthenticationWithBiometrics
            } else {
                LAPolicyDeviceOwnerAuthentication
            }
        }

        context.evaluatePolicy(
            policy,
            localizedReason = subtitle
        ) { success, error ->
            if (success) {
                // Callback pode vir em background thread — dispatch para main
                platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                    onSuccess()
                }
            } else {
                val errorMsg = error?.localizedDescription ?: "Autenticação falhou"
                platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                    onError(errorMsg)
                }
            }
        }
    }
}
