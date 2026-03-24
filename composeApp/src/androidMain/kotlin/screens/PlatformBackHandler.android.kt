package screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import util.rememberCameraState
import util.ImageCompressor

/**
 * Android: Usa BackHandler nativo da activity-compose.
 * Intercepta o botão voltar do dispositivo/gesture.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
