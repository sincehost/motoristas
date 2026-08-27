package util

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

// Mesma lógica já usada e testada em MinhasViagensScreen.android.kt (FotoBase64Card).
actual fun decodificarFotoBase64(base64: String): ImageBitmap? {
    return try {
        val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
        val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
