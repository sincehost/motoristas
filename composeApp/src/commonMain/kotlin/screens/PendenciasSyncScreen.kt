package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import br.com.lfsystem.app.database.Abastecimento
import br.com.lfsystem.app.database.Arla
import br.com.lfsystem.app.database.ChecklistPosViagem
import br.com.lfsystem.app.database.ChecklistPreViagem
import br.com.lfsystem.app.database.Descarga
import br.com.lfsystem.app.database.FinalizacaoViagem
import br.com.lfsystem.app.database.Manutencao
import br.com.lfsystem.app.database.OutraDespesa
import br.com.lfsystem.app.database.Viagem
import database.AppRepository
import ui.AppAlertDialog
import ui.AppColors
import ui.GradientTopBar

/** Representação unificada de um lançamento pendente, pra listar os 9 tipos com o mesmo card.
 *
 * [onDescartar] é a exclusão normal de UM item (id conhecido). [onExcluirTodos] só é usado
 * quando uma categoria inteira falhou ao carregar (ver [PendenciasSyncScreen]) e não há como
 * saber o id de cada linha individualmente — nesse caso é a única forma de destravar sem
 * reinstalar o app.
 */
private data class PendenciaItem(
    val tipo: String,
    val descricao: String,
    val erro: String?,
    val detalhes: List<Pair<String, String>> = emptyList(),
    val fotos: List<Pair<String, String>> = emptyList(),
    val onDescartar: (() -> Unit)? = null,
    val onExcluirTodos: (() -> Unit)? = null
)

private fun itemErroAoExibir(tipo: String, e: Throwable, onDescartar: () -> Unit): PendenciaItem = PendenciaItem(
    tipo = tipo,
    descricao = "⚠️ Não foi possível exibir este item (dado inesperado)",
    erro = "Erro técnico: ${e.message}",
    onDescartar = onDescartar
)

private fun itemFalhaDeCategoria(tipo: String, e: Throwable, onExcluirTodos: () -> Unit): PendenciaItem = PendenciaItem(
    tipo = tipo,
    descricao = "Não foi possível carregar os lançamentos deste tipo agora. Os dados continuam salvos no aparelho — nada foi perdido.",
    erro = "Erro técnico: ${e.message}",
    onExcluirTodos = onExcluirTodos
)

// ===============================
// DETALHES POR TIPO (campos + fotos que o motorista preencheu)
// ===============================

private fun detalhesViagem(v: Viagem): List<Pair<String, String>> = listOf(
    "Nº BD" to v.numerobd.ifBlank { "-" },
    "CT-e" to v.cte.ifBlank { "-" },
    "Destino" to v.destino_nome,
    "Placa" to v.placa.ifBlank { "-" },
    "Data da viagem" to v.data_viagem,
    "KM início" to v.km_inicio,
    "Peso da carga" to v.pesocarga,
    "Valor do frete" to (v.valorfrete?.takeIf { it.isNotBlank() } ?: "-"),
    "Salvo no aparelho em" to v.data_criacao
)
private fun fotosViagem(v: Viagem): List<Pair<String, String>> =
    listOfNotNull(v.foto_painel_saida?.takeIf { it.isNotBlank() }?.let { "Foto do painel (saída)" to it })

private fun detalhesAbastecimento(a: Abastecimento): List<Pair<String, String>> = listOf(
    "Data" to a.data_,
    "Posto" to a.posto.ifBlank { "-" },
    "Combustível" to a.tipo_combustivel,
    "Litros" to a.litros,
    "Valor/litro" to a.valor_litro,
    "Valor total" to a.valor,
    "KM do painel" to a.km_posto,
    "Forma de pagamento" to a.tipo_pagamento,
    "Horas (se aplicável)" to (a.horas?.takeIf { it.isNotBlank() } ?: "-")
)
private fun fotosAbastecimento(a: Abastecimento): List<Pair<String, String>> = listOfNotNull(
    a.foto?.takeIf { it.isNotBlank() }?.let { "Cupom fiscal" to it },
    a.foto_marcador?.takeIf { it.isNotBlank() }?.let { "Foto do marcador/painel" to it }
)

private fun detalhesArla(ar: Arla): List<Pair<String, String>> = listOf(
    "Data" to ar.data_,
    "Posto" to ar.posto.ifBlank { "-" },
    "Litros" to ar.litros,
    "Valor" to ar.valor,
    "KM do painel" to ar.km_posto
)
private fun fotosArla(ar: Arla): List<Pair<String, String>> =
    listOfNotNull(ar.foto?.takeIf { it.isNotBlank() }?.let { "Cupom/foto" to it })

private fun detalhesDescarga(d: Descarga): List<Pair<String, String>> = listOf(
    "Data" to d.data_,
    "Placa" to d.placa.ifBlank { "-" },
    "Ordem de descarga" to d.ordem_descarga.toString(),
    "Valor" to d.valor
)
private fun fotosDescarga(d: Descarga): List<Pair<String, String>> =
    listOfNotNull(d.foto?.takeIf { it.isNotBlank() }?.let { "Foto" to it })

private fun detalhesManutencao(m: Manutencao): List<Pair<String, String>> = listOf(
    "Data" to m.data_manutencao,
    "Placa" to m.placa.ifBlank { "-" },
    "Serviço" to m.servico,
    "Tipo" to m.tipo_manutencao,
    "Descrição" to (m.descricao_servico?.takeIf { it.isNotBlank() } ?: "-"),
    "Local" to (m.local_manutencao?.takeIf { it.isNotBlank() } ?: "-"),
    "Valor" to m.valor,
    "KM troca de óleo" to (m.km_troca_oleo?.takeIf { it.isNotBlank() } ?: "-"),
    "KM troca de pneu" to (m.km_troca_pneu?.takeIf { it.isNotBlank() } ?: "-")
)
private fun fotosManutencao(m: Manutencao): List<Pair<String, String>> = listOfNotNull(
    m.foto_comprovante1?.takeIf { it.isNotBlank() }?.let { "Comprovante 1" to it },
    m.foto_comprovante2?.takeIf { it.isNotBlank() }?.let { "Comprovante 2" to it }
)

private fun detalhesFinalizacao(f: FinalizacaoViagem): List<Pair<String, String>> = buildList {
    add("Data de chegada" to f.data_chegada)
    add("KM chegada" to f.km_chegada)
    if (f.teve_retorno == 1L) {
        add("Peso carga retorno" to (f.pesocarga_retorno?.takeIf { it.isNotBlank() } ?: "-"))
        add("Valor frete retorno" to (f.valorfrete_retorno?.takeIf { it.isNotBlank() } ?: "-"))
        add("Local de carregamento" to (f.local_carregou?.takeIf { it.isNotBlank() } ?: "-"))
        add("Ordem de retorno" to (f.ordem_retorno?.takeIf { it.isNotBlank() } ?: "-"))
        add("CT-e de retorno" to (f.cte_retorno?.takeIf { it.isNotBlank() } ?: "-"))
    }
    add("Observação" to (f.observacao?.takeIf { it.isNotBlank() } ?: "-"))
}
private fun fotosFinalizacao(f: FinalizacaoViagem): List<Pair<String, String>> =
    listOfNotNull(f.foto_painel_chegada?.takeIf { it.isNotBlank() }?.let { "Foto do painel (chegada)" to it })

private fun detalhesOutraDespesa(o: OutraDespesa): List<Pair<String, String>> = listOf(
    "Tipo" to o.tipo,
    "Descrição" to (o.descricao?.takeIf { it.isNotBlank() } ?: "-"),
    "Valor" to o.valor,
    "Data" to o.data_,
    "Local" to (o.local_?.takeIf { it.isNotBlank() } ?: "-")
)
private fun fotosOutraDespesa(o: OutraDespesa): List<Pair<String, String>> =
    listOfNotNull(o.foto_comprovante?.takeIf { it.isNotBlank() }?.let { "Comprovante" to it })

private fun detalhesChecklistPre(c: ChecklistPreViagem): List<Pair<String, String>> = listOf(
    "Data" to c.data_checklist,
    "Placa" to c.placa.ifBlank { "-" },
    "Observações" to (c.observacoes?.takeIf { it.isNotBlank() } ?: "-")
)

private fun detalhesChecklistPos(c: ChecklistPosViagem): List<Pair<String, String>> = listOf(
    "Data" to c.data_checklist,
    "Placa" to c.placa.ifBlank { "-" },
    "Observações" to (c.observacoes?.takeIf { it.isNotBlank() } ?: "-")
)

/** Monta o texto do relatório exportável — usado pelo botão "Exportar e enviar". */
private fun montarRelatorioTexto(itens: List<PendenciaItem>, nomeMotorista: String): String {
    if (itens.isEmpty()) return "Nenhum lançamento pendente de sincronização."
    val agora = try {
        kotlinx.datetime.Clock.System.now().toString()
    } catch (e: Exception) { "-" }
    val sb = StringBuilder()
    sb.appendLine("RELATÓRIO DE PENDÊNCIAS DE SINCRONIZAÇÃO — Trakvia Motorista")
    sb.appendLine("Motorista: $nomeMotorista")
    sb.appendLine("Gerado em: $agora")
    sb.appendLine("Total de itens: ${itens.size}")
    sb.appendLine("========================================")
    itens.forEachIndexed { index, item ->
        sb.appendLine()
        sb.appendLine("${index + 1}. ${item.tipo} — ${item.descricao}")
        if (item.erro != null) sb.appendLine("   Erro: ${item.erro}")
        item.detalhes.forEach { (label, valor) -> sb.appendLine("   $label: $valor") }
        if (item.fotos.isNotEmpty()) {
            sb.appendLine("   Fotos: ${item.fotos.size} (veja no app: toque no item > Ver detalhes)")
        }
        sb.appendLine("----------------------------------------")
    }
    return sb.toString()
}

/**
 * Lista tudo que ainda não sincronizou (viagem, abastecimento, ARLA, descarga,
 * manutenção, outras despesas, checklists), com o erro do servidor quando
 * houver, e permite descartar manualmente um item travado — antes disso, a
 * única forma de "sumir" com um lançamento que nunca sincroniza (ex.: viagem
 * com KM menor que o registrado) era desinstalar o app.
 *
 * Cada um dos 9 tipos é lido isoladamente (try/catch por categoria, e depois
 * por item dentro dela): se um único registro tiver um dado incompatível
 * (ex.: app antigo + API nova) e travar a leitura, só aquela categoria vira
 * um card de erro — as outras 8 continuam abrindo normalmente. Antes, uma
 * falha em QUALQUER uma das 9 consultas derrubava a tela inteira, e a única
 * saída virou reinstalar o app (perdendo tudo que estava salvo local).
 */
@Composable
fun PendenciasSyncScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    var refreshTrigger by remember { mutableStateOf(0) }
    var itemParaDescartar by remember { mutableStateOf<PendenciaItem?>(null) }
    var itemParaExcluirTodos by remember { mutableStateOf<PendenciaItem?>(null) }
    var itemParaVerDetalhes by remember { mutableStateOf<PendenciaItem?>(null) }
    val scrollState = rememberScrollState()
    val compartilharTexto = util.rememberCompartilharTexto()

    val resultado = remember(refreshTrigger) {
        try {
            val lista = buildList {
                try {
                    repository.getViagensParaSincronizar().forEach { v ->
                        try {
                            add(PendenciaItem(
                                tipo = "Viagem",
                                descricao = "${v.placa.ifBlank { "Sem placa" }} — ${v.destino_nome} (${v.data_viagem})",
                                erro = v.ultimo_erro,
                                detalhes = detalhesViagem(v),
                                fotos = fotosViagem(v),
                                onDescartar = { repository.excluirViagemLocalComDependentes(v.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Viagem", e) { repository.excluirViagemLocalComDependentes(v.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Viagem", e) { repository.excluirTodasViagensPendentes() })
                }

                try {
                    repository.getAbastecimentosParaSincronizar().forEach { a ->
                        try {
                            add(PendenciaItem(
                                tipo = "Abastecimento",
                                descricao = "${a.posto.ifBlank { "Sem posto" }} — R$ ${a.valor}",
                                erro = a.ultimo_erro,
                                detalhes = detalhesAbastecimento(a),
                                fotos = fotosAbastecimento(a),
                                onDescartar = { repository.excluirAbastecimentoLocal(a.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Abastecimento", e) { repository.excluirAbastecimentoLocal(a.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Abastecimento", e) { repository.excluirTodosAbastecimentosPendentes() })
                }

                try {
                    repository.getArlasParaSincronizar().forEach { ar ->
                        try {
                            add(PendenciaItem(
                                tipo = "ARLA",
                                descricao = "${ar.posto.ifBlank { "Sem posto" }} — R$ ${ar.valor} (${ar.litros} L)",
                                erro = ar.ultimo_erro,
                                detalhes = detalhesArla(ar),
                                fotos = fotosArla(ar),
                                onDescartar = { repository.excluirArlaLocal(ar.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("ARLA", e) { repository.excluirArlaLocal(ar.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("ARLA", e) { repository.excluirTodasArlasPendentes() })
                }

                try {
                    repository.getDescargasParaSincronizar().forEach { d ->
                        try {
                            add(PendenciaItem(
                                tipo = "Descarga",
                                descricao = "${d.placa.ifBlank { "Sem placa" }} — Ordem ${d.ordem_descarga}",
                                erro = d.ultimo_erro,
                                detalhes = detalhesDescarga(d),
                                fotos = fotosDescarga(d),
                                onDescartar = { repository.excluirDescargaLocal(d.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Descarga", e) { repository.excluirDescargaLocal(d.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Descarga", e) { repository.excluirTodasDescargasPendentes() })
                }

                try {
                    repository.getManutencoesParaSincronizar().forEach { m ->
                        try {
                            add(PendenciaItem(
                                tipo = "Manutenção",
                                descricao = "${m.placa.ifBlank { "Sem placa" }} — ${m.servico}",
                                erro = m.ultimo_erro,
                                detalhes = detalhesManutencao(m),
                                fotos = fotosManutencao(m),
                                onDescartar = { repository.excluirManutencaoLocal(m.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Manutenção", e) { repository.excluirManutencaoLocal(m.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Manutenção", e) { repository.excluirTodasManutencoesPendentes() })
                }

                try {
                    repository.getFinalizacoesParaSincronizar().forEach { f ->
                        try {
                            add(PendenciaItem(
                                tipo = "Finalização de Viagem",
                                descricao = "KM chegada: ${f.km_chegada} (${f.data_chegada})",
                                erro = f.ultimo_erro,
                                detalhes = detalhesFinalizacao(f),
                                fotos = fotosFinalizacao(f),
                                onDescartar = { repository.excluirFinalizacaoLocal(f.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Finalização de Viagem", e) { repository.excluirFinalizacaoLocal(f.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Finalização de Viagem", e) { repository.excluirTodasFinalizacoesPendentes() })
                }

                try {
                    repository.getOutrasDespesasParaSincronizar().forEach { o ->
                        try {
                            add(PendenciaItem(
                                tipo = "Outra Despesa",
                                descricao = "${o.tipo} — R$ ${o.valor}",
                                erro = o.ultimo_erro,
                                detalhes = detalhesOutraDespesa(o),
                                fotos = fotosOutraDespesa(o),
                                onDescartar = { repository.excluirOutraDespesaLocal(o.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Outra Despesa", e) { repository.excluirOutraDespesaLocal(o.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Outra Despesa", e) { repository.excluirTodasOutrasDespesasPendentes() })
                }

                try {
                    repository.getChecklistsPreParaSincronizar().forEach { c ->
                        try {
                            add(PendenciaItem(
                                tipo = "Checklist Pré-Viagem",
                                descricao = "${c.placa.ifBlank { "Sem placa" }} (${c.data_checklist})",
                                erro = c.ultimo_erro,
                                detalhes = detalhesChecklistPre(c),
                                onDescartar = { repository.excluirChecklistPreLocal(c.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Checklist Pré-Viagem", e) { repository.excluirChecklistPreLocal(c.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Checklist Pré-Viagem", e) { repository.excluirTodosChecklistsPrePendentes() })
                }

                try {
                    repository.getChecklistsPosParaSincronizar().forEach { c ->
                        try {
                            add(PendenciaItem(
                                tipo = "Checklist Pós-Viagem",
                                descricao = "${c.placa.ifBlank { "Sem placa" }} (${c.data_checklist})",
                                erro = c.ultimo_erro,
                                detalhes = detalhesChecklistPos(c),
                                onDescartar = { repository.excluirChecklistPosLocal(c.id) }
                            ))
                        } catch (e: Exception) {
                            add(itemErroAoExibir("Checklist Pós-Viagem", e) { repository.excluirChecklistPosLocal(c.id) })
                        }
                    }
                } catch (e: Exception) {
                    add(itemFalhaDeCategoria("Checklist Pós-Viagem", e) { repository.excluirTodosChecklistsPosPendentes() })
                }
            }.sortedByDescending { it.erro != null } // itens com erro (travados) aparecem primeiro
            lista to null as String?
        } catch (e: Exception) {
            // Rede de segurança final: mesmo algo totalmente inesperado fora
            // dos try/catch por categoria não derruba a tela.
            emptyList<PendenciaItem>() to e.message
        }
    }
    val itens = resultado.first
    val erroGeral = resultado.second

    Scaffold(
        topBar = {
            GradientTopBar(title = "Pendências de Sincronização", onBackClick = onVoltar)
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).padding(16.dp)
        ) {
            if (erroGeral != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.12f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Não foi possível carregar a lista de pendências agora. Nenhum dado foi apagado.",
                            fontSize = 13.sp, color = AppColors.Error, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { refreshTrigger++ }) { Text("Tentar novamente") }
                    }
                }
            }

            if (itens.isEmpty() && erroGeral == null) {
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
            } else if (itens.isNotEmpty()) {
                Text(
                    "${itens.size} lançamento${if (itens.size == 1) "" else "s"} aguardando sincronizar. " +
                        "Itens com erro ficam tentando de novo a cada sincronização — toque num item pra ver tudo " +
                        "que foi preenchido, ou descarte manualmente se estiver travado há muito tempo.",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val nome = repository.getMotoristaLogado()?.nome ?: "-"
                        val texto = montarRelatorioTexto(itens, nome)
                        val fotos = itens.flatMap { it.fotos.map { (_, base64) -> base64 } }
                        compartilharTexto("Pendências de sincronização - Trakvia", texto, fotos)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar e enviar")
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itens.forEach { item ->
                        PendenciaCard(
                            item = item,
                            onClick = { itemParaVerDetalhes = item },
                            onDescartarClick = { itemParaDescartar = item },
                            onExcluirTodosClick = { itemParaExcluirTodos = item }
                        )
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
                        item.onDescartar?.invoke()
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

    val itemTodos = itemParaExcluirTodos
    if (itemTodos != null) {
        AppAlertDialog(
            onDismissRequest = { itemParaExcluirTodos = null },
            containerColor = AppColors.Surface,
            icon = {
                Icon(Icons.Default.DeleteForever, null, tint = AppColors.Error, modifier = Modifier.size(40.dp))
            },
            title = {
                Text("Excluir todos os pendentes deste tipo?", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "Tipo: ${itemTodos.tipo}\n\nNão foi possível carregar esses lançamentos individualmente pra escolher " +
                        "um por um. Esta ação apaga TODOS os lançamentos de \"${itemTodos.tipo}\" ainda não sincronizados " +
                        "de uma vez só, e não pode ser desfeita.\n\nSe puder, use \"Exportar e enviar\" antes de continuar.",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        itemTodos.onExcluirTodos?.invoke()
                        itemParaExcluirTodos = null
                        refreshTrigger++
                        AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    Text("Excluir todos")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemParaExcluirTodos = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    val itemDetalhes = itemParaVerDetalhes
    if (itemDetalhes != null) {
        DetalhesPendenciaDialog(item = itemDetalhes, onDismiss = { itemParaVerDetalhes = null })
    }
}

@Composable
private fun PendenciaCard(
    item: PendenciaItem,
    onClick: () -> Unit,
    onDescartarClick: () -> Unit,
    onExcluirTodosClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
            if (item.onDescartar != null) {
                IconButton(onClick = onDescartarClick) {
                    Icon(Icons.Default.DeleteOutline, "Descartar", tint = AppColors.Error)
                }
            } else if (item.onExcluirTodos != null) {
                IconButton(onClick = onExcluirTodosClick) {
                    Icon(Icons.Default.DeleteForever, "Excluir todos deste tipo", tint = AppColors.Error)
                }
            }
            // Indica que o card inteiro é tocável (abre "Ver detalhes", com
            // os campos preenchidos e a foto) — sem isso, só o ícone de
            // lixeira chamava atenção e o resto do card parecia decorativo.
            Icon(
                Icons.Default.ChevronRight,
                "Ver detalhes",
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DetalhesPendenciaDialog(item: PendenciaItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
        ) {
            Column(
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(item.tipo, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                Spacer(Modifier.height(4.dp))
                Text(item.descricao, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)

                if (item.erro != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Erro ao sincronizar: ${item.erro}", fontSize = 13.sp, color = AppColors.Error)
                }

                if (item.detalhes.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Dados preenchidos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    item.detalhes.forEach { (label, valor) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(label, fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(0.45f))
                            Text(valor, fontSize = 13.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(0.55f))
                        }
                    }
                }

                if (item.fotos.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Fotos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    item.fotos.forEach { (label, base64) ->
                        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        val bitmap = remember(base64) { util.decodificarFotoBase64(base64) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = label,
                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("Não foi possível carregar esta foto.", fontSize = 12.sp, color = AppColors.Error)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (item.detalhes.isEmpty() && item.fotos.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Não há dados detalhados disponíveis para este item.",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Fechar")
                }
            }
        }
    }
}
