package util

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun rememberCompartilharTexto(): (titulo: String, texto: String, fotosBase64: List<String>) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { titulo, texto, fotosBase64 ->
            try {
                val uris = ArrayList<android.net.Uri>()
                fotosBase64.forEachIndexed { index, base64 ->
                    try {
                        val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
                        val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEachIndexed
                        val file = File(context.cacheDir, "pendencia_${index}_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
                    } catch (e: Exception) {
                        LogWriter.log("⚠️ Erro ao preparar foto ${index + 1} para compartilhar: ${e.message}")
                    }
                }

                val intent = if (uris.isEmpty()) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, titulo)
                        putExtra(Intent.EXTRA_TEXT, texto)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_SUBJECT, titulo)
                        putExtra(Intent.EXTRA_TEXT, texto)
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                context.startActivity(Intent.createChooser(intent, titulo))
            } catch (e: Exception) {
                LogWriter.log("❌ Erro ao compartilhar pendências: ${e.message}")
            }
        }
    }
}
