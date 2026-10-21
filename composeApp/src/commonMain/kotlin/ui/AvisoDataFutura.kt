package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Aviso (não bloqueia) quando a data digitada ("DD/MM/AAAA") é depois de
 * hoje — erro comum de digitação (ano ou mês trocado) que só aparecia
 * depois, no relatório do escritório.
 */
@Composable
fun AvisoDataFutura(dataBR: String) {
    val dataApi = util.dataBRParaApi(dataBR) ?: return
    val hoje = try { Clock.System.todayIn(TimeZone.currentSystemDefault()).toString() } catch (_: Exception) { return }
    // "yyyy-MM-dd" compara certo como texto.
    if (dataApi <= hoje) return
    Row(modifier = Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("A data está no futuro. Confira antes de salvar.", fontSize = 12.sp, color = AppColors.Orange)
    }
}
