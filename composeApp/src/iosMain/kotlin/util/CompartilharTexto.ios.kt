package util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSString
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

// Sem guard de iPad de propósito: o app é TARGETED_DEVICE_FAMILY = "1"
// (só iPhone) no Xcode, então o popover do UIActivityViewController — que
// só é obrigatório em iPad — nunca entra em jogo aqui.
@Composable
actual fun rememberCompartilharTexto(): (titulo: String, texto: String) -> Unit {
    return remember {
        { _, texto ->
            try {
                val activityVC = UIActivityViewController(
                    activityItems = listOf(texto as NSString),
                    applicationActivities = null
                )
                val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController
                if (rootVC != null) {
                    rootVC.presentViewController(activityVC, animated = true, completion = null)
                } else {
                    LogWriter.log("⚠️ [iOS] Compartilhar pendências: sem rootViewController disponível")
                }
            } catch (e: Throwable) {
                LogWriter.log("❌ [iOS] Erro ao compartilhar pendências: ${e.message}")
            }
        }
    }
}
