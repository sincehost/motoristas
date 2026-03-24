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

/**
 * Diálogo modal para mensagens de ERRO
 */
@Composable
fun ErroDialog(
    mensagem: String,
    onDismiss: () -> Unit
) {
    val errorColor = AppColors.Error
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        icon = {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = errorColor,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                "Erro",
                fontWeight = FontWeight.Bold,
                color = errorColor
            )
        },
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
                colors = ButtonDefaults.buttonColors(containerColor = errorColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Diálogo modal para mensagens de SUCESSO
 */
@Composable
fun SucessoDialog(
    mensagem: String,
    onDismiss: () -> Unit
) {
    val successColor = AppColors.Success
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = successColor,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                "Sucesso",
                fontWeight = FontWeight.Bold,
                color = successColor
            )
        },
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
                colors = ButtonDefaults.buttonColors(containerColor = successColor),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        }
    )
}
