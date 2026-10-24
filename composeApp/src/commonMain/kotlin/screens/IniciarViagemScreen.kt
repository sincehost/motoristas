package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import ui.AppColors

/**
 * Quanto o Iniciar Viagem espera o servidor antes de salvar no celular. O
 * cliente HTTP espera até 60 s (fotos grandes); com sinal fraco o motorista
 * ficava mais de um minuto olhando o botão girando. Passou disso, a viagem
 * vai pra fila — se a 1ª tentativa tiver chegado, a sincronização reconhece o
 * "já foi cadastrada" e reaproveita a viagem (SyncManager.sincronizarViagem).
 */
const val ESPERA_MAXIMA_INICIAR_VIAGEM_MS = 20_000L

@Composable
expect fun IniciarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit,
    /** Já tem viagem aberta: atalho pro Finalizar no lugar desta tela. */
    onFinalizarViagem: () -> Unit
)

/**
 * Iniciar Viagem bloqueado porque a viagem anterior já foi finalizada no
 * celular, mas a finalização ainda espera os lançamentos dela subirem — até
 * lá o servidor recusa uma viagem nova ("Você já tem uma viagem em andamento").
 * Compartilhado entre Android e iOS.
 */
@Composable
fun FinalizacaoAnteriorNaFilaAviso(modifier: Modifier = Modifier, onVoltar: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ui.Lucide.RefreshCw, null, tint = AppColors.Orange, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Viagem anterior ainda sendo enviada",
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Orange,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Você já finalizou a última viagem, mas alguns lançamentos dela ainda não foram enviados. " +
                        "A finalização sai logo depois deles e aí você pode iniciar a próxima.\n\n" +
                        "Com internet, toque em sincronizar na tela inicial. Se algum lançamento tiver problema, " +
                        "corrija em Pendências de Sincronização.",
                    color = AppColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onVoltar,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Voltar ao Início", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
