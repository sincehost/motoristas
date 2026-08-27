package util

import androidx.compose.ui.graphics.ImageBitmap

// Delega pro decodificador já existente e em produção (CameraHelper.ios.kt),
// usado hoje pelas telas de edição pra mostrar foto já cadastrada.
actual fun decodificarFotoBase64(base64: String): ImageBitmap? {
    return CameraHelper.base64ToImageBitmap(base64)
}
