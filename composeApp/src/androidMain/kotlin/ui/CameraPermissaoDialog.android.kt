package ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Diálogo mostrado quando a câmera está sem permissão (negada). Antes disso,
 * negar a permissão não mostrava nada — o botão "Tirar Foto" simplesmente
 * parava de responder, sem explicação (mesmo problema que o iOS já tinha
 * resolvido com CameraPermissaoNegadaDialog.ios.kt, que serviu de modelo
 * pra este). O botão principal já abre a tela de permissões DESTE app nas
 * Configurações do Android, direto na aba de permissões.
 */
@Composable
fun CameraPermissaoNegadaDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AppAlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        icon = {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = AppColors.Error, modifier = Modifier.size(40.dp))
        },
        title = {
            Text("Câmera sem permissão", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Text(
                "Pra tirar fotos, o app precisa de acesso à câmera do celular. Toque no botão abaixo — ele já abre a tela de permissões do app, é só tocar em \"Câmera\" e permitir.",
                fontSize = 14.sp,
                color = AppColors.TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) { }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abrir Configurações", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Agora não")
            }
        }
    )
}
