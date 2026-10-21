package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.VersaoAppResponse

/**
 * Ação do botão "Atualizar". Android: fluxo de atualização do Google Play
 * dentro do app (cai pra página da loja se não der). iOS: abre a App Store.
 */
@Composable
expect fun rememberAbrirAtualizacao(urlLoja: String): () -> Unit

/** Tela cheia que trava o app — build instalado abaixo do build_minimo. */
@Composable
fun AtualizacaoObrigatoriaScreen(info: VersaoAppResponse) {
    val abrir = rememberAbrirAtualizacao(info.url_loja)
    Box(
        modifier = Modifier.fillMaxSize().background(AppColors.Background).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.SystemUpdate, null, tint = AppColors.Primary, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "Atualização obrigatória",
                fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = AppColors.TextPrimary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                info.mensagem.ifBlank {
                    "Esta versão do app não é mais aceita. Atualize para continuar registrando suas viagens."
                },
                fontSize = 15.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Seus dados salvos no celular não são perdidos ao atualizar.",
                fontSize = 13.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = abrir,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (info.versao_nome.isNotBlank()) "ATUALIZAR PARA ${info.versao_nome}" else "ATUALIZAR AGORA",
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
        }
    }
}

/** Aviso que pode ser adiado — existe build mais novo, mas o atual ainda é aceito. */
@Composable
fun AtualizacaoDisponivelDialog(info: VersaoAppResponse, onDepois: () -> Unit) {
    val abrir = rememberAbrirAtualizacao(info.url_loja)
    AppAlertDialog(
        onDismissRequest = onDepois,
        containerColor = AppColors.Surface,
        icon = { Icon(Icons.Default.SystemUpdate, null, tint = AppColors.Primary, modifier = Modifier.size(36.dp)) },
        title = {
            Text(
                "Nova versão disponível",
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                info.mensagem.ifBlank {
                    "Uma nova versão${if (info.versao_nome.isNotBlank()) " (${info.versao_nome})" else ""} do app está disponível com melhorias e correções."
                },
                fontSize = 14.sp, textAlign = TextAlign.Center, color = AppColors.TextSecondary, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDepois(); abrir() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Atualizar", fontWeight = FontWeight.Bold) }
                OutlinedButton(
                    onClick = onDepois,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Depois") }
            }
        }
    )
}
