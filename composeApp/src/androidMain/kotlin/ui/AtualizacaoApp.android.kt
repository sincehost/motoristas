package ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import util.LogWriter

@Composable
actual fun rememberAbrirAtualizacao(urlLoja: String): () -> Unit {
    val context = LocalContext.current
    val appUpdateManager = remember(context) { AppUpdateManagerFactory.create(context) }
    // Resultado não importa: se o motorista cancelar, a tela/aviso de
    // atualização continua aparecendo e ele pode tocar de novo.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    return remember(context, urlLoja) {
        {
            // Atualização do Google Play dentro do app (baixa e instala sem
            // sair). Só funciona em app instalado pela Play Store — em APK de
            // teste, ou se o Play ainda não liberou o build pro aparelho, cai
            // pra página da loja.
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { info ->
                    val disponivel = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                        info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    if (disponivel && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                            )
                        } catch (e: Exception) {
                            LogWriter.log("⚠️ In-app update falhou: ${e.message}")
                            abrirLoja(context, urlLoja)
                        }
                    } else {
                        abrirLoja(context, urlLoja)
                    }
                }
                .addOnFailureListener { abrirLoja(context, urlLoja) }
        }
    }
}

private fun abrirLoja(context: Context, urlLoja: String) {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")),
        Intent(Intent.ACTION_VIEW, Uri.parse(urlLoja.ifBlank { "https://play.google.com/store/apps/details?id=${context.packageName}" }))
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) { }
    }
}
