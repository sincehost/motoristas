package screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import ui.AppColors
import util.formatarKmExibicao
import util.kmParaDouble
import util.normalizarKmVeiculoParaEnvio

@Composable
expect fun AdicionarCombustivelScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
)

/**
 * Sugestões de postos já usados neste celular, abaixo do campo "Nome do
 * posto" (abastecimento e ARLA). Com o campo vazio mostra os mais recentes;
 * digitando, só os que combinam. Um toque preenche. Some quando o texto já é
 * igual a uma sugestão. Compartilhado entre Android e iOS.
 */
@Composable
fun SugestoesPosto(texto: String, recentes: List<String>, onEscolher: (String) -> Unit) {
    val sugestoes = recentes
        .filter { texto.isBlank() || (it.contains(texto.trim(), ignoreCase = true) && !it.equals(texto.trim(), ignoreCase = true)) }
        .take(4)
    if (sugestoes.isEmpty() || recentes.any { it.equals(texto.trim(), ignoreCase = true) }) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sugestoes.forEach { posto ->
            SuggestionChip(
                onClick = { onEscolher(posto) },
                label = { Text(posto, fontSize = 13.sp, maxLines = 1) },
                icon = { Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

/**
 * Dica abaixo do campo KM (posto de combustível / ARLA) + aviso, sem
 * bloquear, quando o KM digitado é menor que o KM de início da viagem —
 * mesmo aviso que o Iniciar Viagem já tinha. Antes o abastecimento aceitava
 * qualquer número e o erro só aparecia depois, no servidor ou no relatório.
 * Compartilhado entre Android e iOS.
 */
@Composable
fun DicaKmNoPosto(kmDigitado: String, kmInicioViagem: String?, temDecimal: Boolean, lidoPelaCamera: Boolean) {
    Text(
        when {
            lidoPelaCamera -> "Lido pela câmera. Confira com o painel antes de salvar."
            temDecimal -> "Digite como aparece no painel. Ex.: 115670.5"
            else -> "Digite como aparece no painel. Ex.: 115670"
        },
        fontSize = 12.sp,
        color = AppColors.Primary,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
    val inicio = kmParaDouble(kmInicioViagem)
    val digitado = if (kmDigitado.isBlank()) 0.0 else kmParaDouble(normalizarKmVeiculoParaEnvio(kmDigitado, temDecimal))
    if (inicio > 0.0 && digitado > 0.0 && digitado < inicio) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "KM menor que o KM de início da viagem (${formatarKmExibicao(inicio)}). Confira o painel antes de salvar.",
                fontSize = 12.sp,
                color = AppColors.Orange
            )
        }
    }
}
