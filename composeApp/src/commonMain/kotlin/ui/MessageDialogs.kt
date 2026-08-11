package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Substituto de `androidx.compose.material3.AlertDialog` com a MESMA assinatura,
 * implementado sobre `Dialog` para evitar os bugs de renderização do `AlertDialog`
 * no destino iOS (ver comentário em [MensagemDialog]). Use no lugar de `AlertDialog`
 * em qualquer diálogo de confirmação/aviso do app.
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = AppColors.Surface
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.86f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                icon?.invoke()
                if (icon != null) Spacer(Modifier.height(12.dp))
                if (title != null) {
                    title()
                    Spacer(Modifier.height(8.dp))
                }
                text?.invoke()
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * Diálogo modal para mensagens de ERRO
 */
@Composable
fun ErroDialog(
    mensagem: String,
    onDismiss: () -> Unit
) {
    MensagemDialog(titulo = "Erro", mensagem = mensagem, corDestaque = AppColors.Error, icone = Icons.Default.Error, onDismiss = onDismiss)
}

/**
 * Diálogo modal para mensagens de SUCESSO
 */
@Composable
fun SucessoDialog(
    mensagem: String,
    onDismiss: () -> Unit
) {
    MensagemDialog(titulo = "Sucesso", mensagem = mensagem, corDestaque = AppColors.Success, icone = Icons.Default.CheckCircle, onDismiss = onDismiss)
}

@Composable
private fun MensagemDialog(
    titulo: String,
    mensagem: String,
    corDestaque: Color,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icone, contentDescription = null, tint = corDestaque, modifier = Modifier.size(40.dp)) },
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = corDestaque) },
        text = {
            Text(
                mensagem,
                fontSize = 15.sp,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = corDestaque),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}
