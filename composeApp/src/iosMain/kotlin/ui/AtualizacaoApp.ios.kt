package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

// A Apple não permite instalar atualização por dentro do app — o botão abre
// a página do Trakvia na App Store.
@Composable
actual fun rememberAbrirAtualizacao(urlLoja: String): () -> Unit = remember(urlLoja) {
    {
        val url = NSURL.URLWithString(urlLoja.ifBlank { "https://apps.apple.com/app/trakvia/id6805200910" })
        if (url != null) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
        }
    }
}
