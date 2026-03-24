package screens

import androidx.compose.runtime.Composable

/**
 * iOS: No-op — iOS não tem botão voltar do sistema.
 * A navegação de volta é feita por gestos nativos do SwiftUI ou botões na UI.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op no iOS
}
