package util

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCompartilharTexto(): (titulo: String, texto: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { titulo, texto ->
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, titulo)
                    putExtra(Intent.EXTRA_TEXT, texto)
                }
                context.startActivity(Intent.createChooser(intent, titulo))
            } catch (e: Exception) {
                LogWriter.log("❌ Erro ao compartilhar pendências: ${e.message}")
            }
        }
    }
}
