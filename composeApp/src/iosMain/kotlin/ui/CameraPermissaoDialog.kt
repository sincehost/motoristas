package ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import util.CameraHelper

/**
 * Diálogo mostrado quando a câmera está sem permissão (negada ou restrita).
 * Em vez de só instruir "vá em Ajustes" por texto — confuso pra motorista
 * leigo — o botão principal já abre a tela de configurações DESTE app
 * dentro do Ajustes do iPhone, direto na opção de Câmera.
 */
@Composable
fun CameraPermissaoNegadaDialog(onDismiss: () -> Unit) {
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
                "Pra tirar fotos, o app precisa de acesso à câmera do celular. Toque no botão abaixo — ele já abre a tela certa, é só tocar em \"Câmera\" e ativar.",
                fontSize = 14.sp,
                color = AppColors.TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    CameraHelper.abrirConfiguracoesDoApp()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Abrir Ajustes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Agora não")
            }
        }
    )
}
