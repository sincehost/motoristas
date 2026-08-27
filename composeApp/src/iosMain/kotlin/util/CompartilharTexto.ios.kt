package util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage

// Sem guard de iPad de propósito: o app é TARGETED_DEVICE_FAMILY = "1"
// (só iPhone) no Xcode, então o popover do UIActivityViewController — que
// só é obrigatório em iPad — nunca entra em jogo aqui.
@Composable
actual fun rememberCompartilharTexto(): (titulo: String, texto: String, fotosBase64: List<String>) -> Unit {
    return remember {
        { _, texto, fotosBase64 ->
            try {
                val items = mutableListOf<Any>(texto as NSString)
                fotosBase64.forEach { base64 ->
                    try {
                        val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
                        val data = NSData.create(base64EncodedString = cleanBase64, options = 0u)
                        val image = data?.let { UIImage(data = it) }
                        if (image != null) items.add(image)
                    } catch (e: Throwable) {
                        LogWriter.log("⚠️ [iOS] Erro ao preparar foto para compartilhar: ${e.message}")
                    }
                }

                val activityVC = UIActivityViewController(
                    activityItems = items,
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
