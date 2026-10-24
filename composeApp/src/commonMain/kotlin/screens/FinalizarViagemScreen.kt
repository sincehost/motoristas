package screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import kotlinx.coroutines.withTimeoutOrNull
import ui.AppColors
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
expect fun FinalizarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
)

// ===============================
// RESUMO DA VIAGEM NA CONFIRMAÇÃO DO FINALIZAR — litros, média e gastos,
// pro motorista conferir (e pegar erro de digitação) antes de fechar.
// Compartilhado entre Android e iOS.
// ===============================

data class ResumoFinalizacao(
    val litrosDiesel: Double,
    val valorCombustivel: Double,
    val valorArla: Double,
    val valorDescarga: Double,
    val totalDespesas: Double,
    /** Sem internet numa viagem que já subiu: só o que ainda está no celular. */
    val parcial: Boolean
)

/**
 * Servidor (o que já foi enviado, via viagem/resumo.php) + o que ainda está
 * no celular esperando envio desta viagem. Nunca lança exceção.
 */
suspend fun carregarResumoParaFinalizar(repository: AppRepository, viagemId: Long, motoristaId: String): ResumoFinalizacao {
    val servidor = if (viagemId > 0) try {
        withTimeoutOrNull(8_000) {
            api.ApiClient.resumoViagem(api.ResumoRequest(viagem_id = viagemId.toInt(), motorista_id = motoristaId))
        }?.takeIf { it.status == "ok" }?.resumo
    } catch (_: Exception) { null } else null

    val alvo = repository.idViagemNoServidor(viagemId)
    fun daViagem(id: Long) = id == viagemId || repository.idViagemNoServidor(id) == alvo

    val abastecimentos = try { repository.getAbastecimentosParaSincronizar().filter { daViagem(it.viagem_id) } } catch (_: Exception) { emptyList() }
    val arlas = try { repository.getArlasParaSincronizar().filter { daViagem(it.viagem_id) } } catch (_: Exception) { emptyList() }
    val descargas = try { repository.getDescargasParaSincronizar().filter { daViagem(it.viagem_id) } } catch (_: Exception) { emptyList() }
    val outras = try { repository.getOutrasDespesasParaSincronizar().filter { daViagem(it.viagem_id) } } catch (_: Exception) { emptyList() }

    // Litros "de caminhão" (sem horas) — os de aparelho/refrigeração não
    // entram na média km/L, igual o resumo do servidor.
    val requerHoras = try {
        repository.getAllTiposCombustivel().filter { it.requer_horas == 1L }.map { it.nome }.toSet()
    } catch (_: Exception) { emptySet() }
    val locaisCaminhao = abastecimentos.filter { it.tipo_combustivel !in requerHoras }

    val localCombustivel = abastecimentos.sumOf { valorBrParaDouble(it.valor) }
    val localArla = arlas.sumOf { valorBrParaDouble(it.valor) }
    val localDescarga = descargas.sumOf { valorBrParaDouble(it.valor) }
    val localOutras = outras.sumOf { valorBrParaDouble(it.valor) }

    return ResumoFinalizacao(
        litrosDiesel = (servidor?.litros_diesel_caminhao ?: 0.0) + locaisCaminhao.sumOf { valorBrParaDouble(it.litros) },
        valorCombustivel = (servidor?.let { it.valor_diesel_caminhao + it.valor_diesel_aparelho } ?: 0.0) + localCombustivel,
        valorArla = (servidor?.valor_arla ?: 0.0) + localArla,
        valorDescarga = (servidor?.valor_descarga ?: 0.0) + localDescarga,
        totalDespesas = (servidor?.total_despesas ?: 0.0) + localCombustivel + localArla + localDescarga + localOutras,
        parcial = viagemId > 0 && servidor == null
    )
}

/** Bloco do resumo dentro da janela "Confirmar Finalização". null = carregando. */
@Composable
fun ResumoViagemFinalizar(resumo: ResumoFinalizacao?, kmPercorrido: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(vertical = 6.dp))
        Text("Resumo da viagem", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        if (resumo == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                Text("Somando os lançamentos…", fontSize = 13.sp, color = AppColors.TextSecondary)
            }
            return@Column
        }
        if (resumo.litrosDiesel > 0) {
            Text("Diesel: ${formatarDecimalBR(resumo.litrosDiesel, 1)} L · ${formatarReais(resumo.valorCombustivel)}", fontSize = 13.sp, color = AppColors.TextSecondary)
            if (kmPercorrido > 0) {
                Text("Média: ${formatarDecimalBR(kmPercorrido / resumo.litrosDiesel, 2)} km/L", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
            }
        } else if (resumo.valorCombustivel > 0) {
            Text("Combustível: ${formatarReais(resumo.valorCombustivel)}", fontSize = 13.sp, color = AppColors.TextSecondary)
        }
        if (resumo.valorArla > 0) Text("ARLA: ${formatarReais(resumo.valorArla)}", fontSize = 13.sp, color = AppColors.TextSecondary)
        if (resumo.valorDescarga > 0) Text("Descarga: ${formatarReais(resumo.valorDescarga)}", fontSize = 13.sp, color = AppColors.TextSecondary)
        Text("Total de despesas: ${formatarReais(resumo.totalDespesas)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        if (resumo.parcial) {
            Text("Sem internet: mostra só o que ainda está no celular.", fontSize = 12.sp, color = AppColors.Orange)
        }
    }
}

/** "1.234,56" (campo em formato BR) → 1234.56. Vazio/inválido → 0.0. */
private fun valorBrParaDouble(texto: String): Double =
    texto.trim().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0

/** 1234.5 → "R$ 1.234,50". */
fun formatarReais(valor: Double): String = "R$ " + formatarDecimalBR(valor, 2)

/** 1234.567 com 2 casas → "1.234,57". */
fun formatarDecimalBR(valor: Double, casas: Int): String {
    var fator = 1L
    repeat(casas) { fator *= 10 }
    val total = (abs(valor) * fator).roundToLong()
    val inteiro = (total / fator).toString().reversed().chunked(3).joinToString(".").reversed()
    val sinal = if (valor < 0 && total != 0L) "-" else ""
    if (casas == 0) return sinal + inteiro
    val decimal = (total % fator).toString().padStart(casas, '0')
    return "$sinal$inteiro,$decimal"
}
