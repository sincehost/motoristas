package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import ui.AppAlertDialog
import ui.AppColors
import util.BiometricAuth

/**
 * Trava biométrica mostrada ao reabrir o app já logado (sessão local
 * persistente — o motorista não passa pela LoginScreen de novo depois do
 * primeiro login, só faz logout explícito). Se o aparelho não tem biometria
 * configurada, libera direto — não é bloqueante pra quem não tem a opção.
 */
@Composable
fun BiometricLockScreen(
    repository: AppRepository,
    onUnlocked: () -> Unit,
    onLogout: () -> Unit
) {
    var erro by remember { mutableStateOf<String?>(null) }
    var autenticando by remember { mutableStateOf(false) }
    var mostrarConfirmacaoSair by remember { mutableStateOf(false) }

    fun tentarAutenticar() {
        autenticando = true
        erro = null
        BiometricAuth.authenticate(
            title = "Verificação de identidade",
            subtitle = "Use sua biometria para continuar",
            onSuccess = { autenticando = false; onUnlocked() },
            onError = { msg -> autenticando = false; erro = msg },
            onNotAvailable = { autenticando = false; onUnlocked() }
        )
    }

    LaunchedEffect(Unit) { tentarAutenticar() }

    // Mesmo diálogo/aviso do logout real do Dashboard — se o motorista não
    // conseguir desbloquear (ex.: sensor com problema), a única saída não
    // pode ser um bypass silencioso que finge que nada aconteceu.
    if (mostrarConfirmacaoSair) {
        AppAlertDialog(
            onDismissRequest = { mostrarConfirmacaoSair = false },
            containerColor = AppColors.Surface,
            icon = {
                Icon(Icons.Default.Logout, null, tint = AppColors.Error, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Sair do aplicativo", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "Ao sair, seus dados locais serão removidos e você precisará fazer login novamente.\n\nDados pendentes de sincronização serão perdidos.",
                    fontSize = 14.sp, textAlign = TextAlign.Center, color = AppColors.TextSecondary, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { mostrarConfirmacaoSair = false; repository.logout(); onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) { Text("Sair", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarConfirmacaoSair = false }) { Text("Cancelar") }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Fingerprint, null, tint = AppColors.Primary, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text("App bloqueado", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Confirme sua identidade para continuar",
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            if (erro != null) {
                Text(erro ?: "", color = AppColors.Error, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }
            Button(
                onClick = { tentarAutenticar() },
                enabled = !autenticando,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                if (autenticando) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Tentar novamente")
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { mostrarConfirmacaoSair = true }) {
                Text("Sair e entrar com outra conta", color = AppColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}
