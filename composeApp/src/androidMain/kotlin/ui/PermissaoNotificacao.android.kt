package ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import database.AppRepository

private const val CHAVE_PERGUNTADO = "notificacao_perguntada"

@Composable
actual fun PedidoPermissaoNotificacao(repository: AppRepository) {
    // Antes do Android 13 a permissão não existe (notificação já liberada).
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var mostrar by remember {
        val jaTem = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val jaPerguntou = try { repository.getPreferencia(CHAVE_PERGUNTADO) != null } catch (_: Exception) { true }
        mutableStateOf(!jaTem && !jaPerguntou)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun registrarQuePerguntou() {
        mostrar = false
        try { repository.salvarPreferencia(CHAVE_PERGUNTADO, "1") } catch (_: Exception) {}
    }

    if (!mostrar) return
    AppAlertDialog(
        onDismissRequest = { registrarQuePerguntou() },
        containerColor = AppColors.Surface,
        icon = { Icon(Icons.Default.Notifications, null, tint = AppColors.Primary, modifier = Modifier.size(36.dp)) },
        title = {
            Text("Ativar avisos", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Text(
                "O app avisa quando algum lançamento ainda não foi enviado ao escritório e quando chega mensagem da empresa.",
                fontSize = 14.sp, textAlign = TextAlign.Center, color = AppColors.TextSecondary, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        registrarQuePerguntou()
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Ativar", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = { registrarQuePerguntou() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Agora não") }
            }
        }
    )
}
