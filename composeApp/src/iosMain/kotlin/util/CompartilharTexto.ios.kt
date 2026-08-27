package util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSString
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

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
                    // No iPad o UIActivityViewController precisa de um sourceView
                    // pro popover, senão o app trava ao tentar abrir (crash
                    // conhecido do UIKit, não específico deste app).
                    if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
                        activityVC.popoverPresentationController?.sourceView = rootVC.view
                    }
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
