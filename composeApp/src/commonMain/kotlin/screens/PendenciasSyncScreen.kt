package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import ui.AppAlertDialog
import ui.AppColors
import ui.GradientTopBar

/** Representação unificada de um lançamento pendente, pra listar os 9 tipos com o mesmo card. */
private data class PendenciaItem(
    val tipo: String,
    val descricao: String,
    val erro: String?,
    val onDescartar: () -> Unit
)

/**
 * Lista tudo que ainda não sincronizou (viagem, abastecimento, ARLA, descarga,
 * manutenção, outras despesas, checklists), com o erro do servidor quando
 * houver, e permite descartar manualmente um item travado — antes disso, a
 * única forma de "sumir" com um lançamento que nunca sincroniza (ex.: viagem
 * com KM menor que o registrado) era desinstalar o app.
 */
@Composable
fun PendenciasSyncScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    var refreshTrigger by remember { mutableStateOf(0) }
    var itemParaDescartar by remember { mutableStateOf<PendenciaItem?>(null) }
    val scrollState = rememberScrollState()

    val itens = remember(refreshTrigger) {
        buildList {
            repository.getViagensParaSincronizar().forEach { v ->
                add(PendenciaItem(
                    tipo = "Viagem",
                    descricao = "${v.placa.ifBlank { "Sem placa" }} — ${v.destino_nome} (${v.data_viagem})",
                    erro = v.ultimo_erro,
                    onDescartar = { repository.excluirViagemLocalComDependentes(v.id) }
                ))
            }
            repository.getAbastecimentosParaSincronizar().forEach { a ->
                add(PendenciaItem(
                    tipo = "Abastecimento",
                    descricao = "${a.posto.ifBlank { "Sem posto" }} — R$ ${a.valor}",
                    erro = a.ultimo_erro,
                    onDescartar = { repository.excluirAbastecimentoLocal(a.id) }
                ))
            }
            repository.getArlasParaSincronizar().forEach { ar ->
                add(PendenciaItem(
                    tipo = "ARLA",
                    descricao = "${ar.posto.ifBlank { "Sem posto" }} — R$ ${ar.valor} (${ar.litros} L)",
                    erro = ar.ultimo_erro,
                    onDescartar = { repository.excluirArlaLocal(ar.id) }
                ))
            }
            repository.getDescargasParaSincronizar().forEach { d ->
                add(PendenciaItem(
                    tipo = "Descarga",
                    descricao = "${d.placa.ifBlank { "Sem placa" }} — Ordem ${d.ordem_descarga}",
                    erro = d.ultimo_erro,
                    onDescartar = { repository.excluirDescargaLocal(d.id) }
                ))
            }
            repository.getManutencoesParaSincronizar().forEach { m ->
                add(PendenciaItem(
                    tipo = "Manutenção",
                    descricao = "${m.placa.ifBlank { "Sem placa" }} — ${m.servico}",
                    erro = m.ultimo_erro,
                    onDescartar = { repository.excluirManutencaoLocal(m.id) }
                ))
            }
            repository.getFinalizacoesParaSincronizar().forEach { f ->
                add(PendenciaItem(
                    tipo = "Finalização de Viagem",
                    descricao = "KM chegada: ${f.km_chegada} (${f.data_chegada})",
                    erro = f.ultimo_erro,
                    onDescartar = { repository.excluirFinalizacaoLocal(f.id) }
                ))
            }
            repository.getOutrasDespesasParaSincronizar().forEach { o ->
                add(PendenciaItem(
                    tipo = "Outra Despesa",
                    descricao = "${o.tipo} — R$ ${o.valor}",
                    erro = o.ultimo_erro,
                    onDescartar = { repository.excluirOutraDespesaLocal(o.id) }
                ))
            }
            repository.getChecklistsPreParaSincronizar().forEach { c ->
                add(PendenciaItem(
                    tipo = "Checklist Pré-Viagem",
                    descricao = "${c.placa.ifBlank { "Sem placa" }} (${c.data_checklist})",
                    erro = c.ultimo_erro,
                    onDescartar = { repository.excluirChecklistPreLocal(c.id) }
                ))
            }
            repository.getChecklistsPosParaSincronizar().forEach { c ->
                add(PendenciaItem(
                    tipo = "Checklist Pós-Viagem",
                    descricao = "${c.placa.ifBlank { "Sem placa" }} (${c.data_checklist})",
                    erro = c.ultimo_erro,
                    onDescartar = { repository.excluirChecklistPosLocal(c.id) }
                ))
            }
        }.sortedByDescending { it.erro != null } // itens com erro (travados) aparecem primeiro
    }

    Scaffold(
        topBar = {
            GradientTopBar(title = "Pendências de Sincronização", onBackClick = onVoltar)
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).padding(16.dp)
        ) {
            if (itens.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudDone, null, tint = AppColors.Secondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Tudo sincronizado!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nenhum lançamento pendente no momento.",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            } else {
                Text(
                    "${itens.size} lançamento${if (itens.size == 1) "" else "s"} aguardando sincronizar. " +
                        "Itens com erro ficam tentando de novo a cada sincronização — se um deles está travado " +
                        "há muito tempo, você pode descartar manualmente.",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itens.forEach { item ->
                        PendenciaCard(item = item, onDescartarClick = { itemParaDescartar = item })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    val item = itemParaDescartar
    if (item != null) {
        AppAlertDialog(
            onDismissRequest = { itemParaDescartar = null },
            containerColor = AppColors.Surface,
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = AppColors.Error, modifier = Modifier.size(40.dp))
            },
            title = {
                Text("Descartar lançamento?", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            },
            text = {
                val avisoCascata = if (item.tipo == "Viagem")
                    "\n\nComo é uma viagem, tudo que foi lançado dentro dela (abastecimento, ARLA, descarga, manutenção, despesas, checklists) também será descartado."
                else ""
                Text(
                    "${item.tipo}: ${item.descricao}\n\nEsse lançamento nunca foi enviado ao servidor. " +
                        "Ao descartar, ele é apagado só do celular e não pode ser recuperado.$avisoCascata",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        item.onDescartar()
                        itemParaDescartar = null
                        refreshTrigger++
                        AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemParaDescartar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PendenciaCard(item: PendenciaItem, onDescartarClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.tipo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                Spacer(Modifier.height(2.dp))
                Text(item.descricao, fontSize = 14.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                if (item.erro != null) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(item.erro, fontSize = 12.sp, color = AppColors.Error)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Aguardando sincronizar", fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
            }
            IconButton(onClick = onDescartarClick) {
                Icon(Icons.Default.DeleteOutline, "Descartar", tint = AppColors.Error)
            }
        }
    }
}
