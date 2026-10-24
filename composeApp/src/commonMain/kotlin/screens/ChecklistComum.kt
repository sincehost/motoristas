package screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.lfsystem.app.database.ChecklistPosViagem
import br.com.lfsystem.app.database.ChecklistPreViagem
import database.AppRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import ui.AppColors
import ui.GradientTopBar
import util.mensagemErroAmigavel

// =====================================================================
// CHECKLIST DE SAÍDA (pré-viagem) E DE CHEGADA (pós-viagem) — uma tela só,
// compartilhada entre Android e iOS.
//
// Cada item é respondido com OK ou Problema; nada vem marcado. Problema
// pede "O que está errado?" (vai pro escritório nas observações). No lugar
// do "Marcar todos" geral há "Tudo OK nesta parte", por seção. Antes: 35
// caixas e um botão que marcava tudo de uma vez — caixa desmarcada não
// dizia se era problema ou se ninguém olhou.
//
// Gravação idêntica à de antes (1/0 por campo, mesmas colunas): o servidor
// e o painel do administrador não mudam. Itens [ItemChecklist.invertido]
// são campos de avaria/pendência, onde 1 = tem problema.
// =====================================================================

enum class RespostaChecklist { OK, PROBLEMA }

data class ItemChecklist(
    val chave: String,
    val rotulo: String,
    /** Problema aqui é de segurança (freio, pneu, direção…): alerta forte na revisão. */
    val seguranca: Boolean = false,
    /** Campo de avaria/pendência: OK grava 0, Problema grava 1. */
    val invertido: Boolean = false
)

data class SecaoChecklist(val titulo: String, val icone: ImageVector, val itens: List<ItemChecklist>)

val SECOES_SAIDA = listOf(
    SecaoChecklist("Documentos", Icons.Default.Description, listOf(
        ItemChecklist("doc_cnh_valida", "CNH válida"),
        ItemChecklist("doc_crlv_veiculo", "CRLV do veículo em dia"),
        ItemChecklist("doc_antt_valida", "ANTT válida"),
        ItemChecklist("doc_seguro_carga", "Seguro da carga"),
        ItemChecklist("doc_ordem_coleta", "Ordem de coleta / manifesto")
    )),
    SecaoChecklist("Parte elétrica", Icons.Default.ElectricalServices, listOf(
        ItemChecklist("elet_farol_dianteiro", "Farol dianteiro"),
        ItemChecklist("elet_farol_traseiro", "Farol traseiro"),
        ItemChecklist("elet_luz_freio", "Luz de freio", seguranca = true),
        ItemChecklist("elet_seta_direita", "Seta direita"),
        ItemChecklist("elet_seta_esquerda", "Seta esquerda"),
        ItemChecklist("elet_luz_re", "Luz de ré"),
        ItemChecklist("elet_painel_funcionando", "Painel funcionando")
    )),
    SecaoChecklist("Pneus e rodas", Icons.Default.TireRepair, listOf(
        ItemChecklist("pneu_calibragem_ok", "Calibragem", seguranca = true),
        ItemChecklist("pneu_estado_conservacao", "Estado dos pneus", seguranca = true),
        ItemChecklist("pneu_estepe_ok", "Estepe"),
        ItemChecklist("pneu_ferramentas_troca", "Ferramentas de troca")
    )),
    SecaoChecklist("Fluidos e níveis", Icons.Default.WaterDrop, listOf(
        ItemChecklist("fluido_oleo_motor", "Óleo do motor"),
        ItemChecklist("fluido_agua_radiador", "Água do radiador"),
        ItemChecklist("fluido_fluido_freio", "Fluido de freio", seguranca = true),
        ItemChecklist("fluido_arla32", "ARLA 32"),
        ItemChecklist("fluido_combustivel", "Combustível suficiente")
    )),
    SecaoChecklist("Equipamentos de segurança", Icons.Default.HealthAndSafety, listOf(
        ItemChecklist("seg_extintor_validade", "Extintor na validade"),
        ItemChecklist("seg_triangulo", "Triângulo"),
        ItemChecklist("seg_macaco_chave_roda", "Macaco e chave de roda"),
        ItemChecklist("seg_cones_faixa", "Cones / faixa refletiva"),
        ItemChecklist("seg_epi_completo", "EPI completo")
    )),
    SecaoChecklist("Carroceria / baú", Icons.Default.Inventory2, listOf(
        ItemChecklist("carr_lonas_cordas", "Lonas e cordas"),
        ItemChecklist("carr_portas_bau", "Portas do baú"),
        ItemChecklist("carr_assoalho_estado", "Assoalho"),
        ItemChecklist("carr_travas_lacres", "Travas e lacres")
    )),
    SecaoChecklist("Cabine", Icons.Default.AirlineSeatReclineExtra, listOf(
        ItemChecklist("cab_bancos_cintos", "Bancos e cintos", seguranca = true),
        ItemChecklist("cab_espelhos_retrovisores", "Espelhos retrovisores", seguranca = true),
        ItemChecklist("cab_limpador_parabrisa", "Limpador de para-brisa"),
        ItemChecklist("cab_ar_condicionado", "Ar-condicionado"),
        ItemChecklist("cab_freio_estacionamento", "Freio de estacionamento", seguranca = true)
    ))
)

val SECOES_CHEGADA = listOf(
    SecaoChecklist("Avarias", Icons.Default.ReportProblem, listOf(
        ItemChecklist("avaria_carroceria", "Carroceria sem avaria", invertido = true),
        ItemChecklist("avaria_cabine", "Cabine sem avaria", invertido = true),
        ItemChecklist("avaria_pneus", "Pneus sem avaria", seguranca = true, invertido = true),
        ItemChecklist("avaria_espelhos", "Espelhos sem avaria", invertido = true),
        ItemChecklist("avaria_farois", "Faróis sem avaria", invertido = true)
    )),
    SecaoChecklist("Níveis", Icons.Default.WaterDrop, listOf(
        ItemChecklist("pos_nivel_oleo", "Nível de óleo"),
        ItemChecklist("pos_nivel_agua", "Nível de água"),
        ItemChecklist("pos_nivel_combustivel", "Nível de combustível"),
        ItemChecklist("pos_nivel_arla", "Nível de ARLA 32")
    )),
    SecaoChecklist("Limpeza", Icons.Default.CleaningServices, listOf(
        ItemChecklist("limp_cabine_limpa", "Cabine limpa e organizada"),
        ItemChecklist("limp_carroceria_limpa", "Carroceria limpa"),
        ItemChecklist("limp_bau_vazio", "Baú vazio e limpo")
    )),
    SecaoChecklist("Funcionamento", Icons.Default.Settings, listOf(
        ItemChecklist("func_freios_ok", "Freios", seguranca = true),
        ItemChecklist("func_direcao_ok", "Direção", seguranca = true),
        ItemChecklist("func_suspensao_ok", "Suspensão"),
        ItemChecklist("func_motor_ruido", "Motor sem ruídos anormais"),
        ItemChecklist("func_cambio_ok", "Câmbio")
    )),
    SecaoChecklist("Pendências", Icons.Default.Build, listOf(
        ItemChecklist("pend_manutencao_urgente", "Sem manutenção urgente", invertido = true),
        ItemChecklist("pend_abastecimento_necessario", "Não precisa abastecer", invertido = true),
        ItemChecklist("pend_troca_oleo_proxima", "Troca de óleo em dia", invertido = true)
    ))
)

private fun valoresSaida(c: ChecklistPreViagem): Map<String, Long> = mapOf(
    "doc_cnh_valida" to c.doc_cnh_valida, "doc_crlv_veiculo" to c.doc_crlv_veiculo, "doc_antt_valida" to c.doc_antt_valida,
    "doc_seguro_carga" to c.doc_seguro_carga, "doc_ordem_coleta" to c.doc_ordem_coleta,
    "elet_farol_dianteiro" to c.elet_farol_dianteiro, "elet_farol_traseiro" to c.elet_farol_traseiro, "elet_luz_freio" to c.elet_luz_freio,
    "elet_seta_direita" to c.elet_seta_direita, "elet_seta_esquerda" to c.elet_seta_esquerda, "elet_luz_re" to c.elet_luz_re,
    "elet_painel_funcionando" to c.elet_painel_funcionando,
    "pneu_calibragem_ok" to c.pneu_calibragem_ok, "pneu_estado_conservacao" to c.pneu_estado_conservacao,
    "pneu_estepe_ok" to c.pneu_estepe_ok, "pneu_ferramentas_troca" to c.pneu_ferramentas_troca,
    "fluido_oleo_motor" to c.fluido_oleo_motor, "fluido_agua_radiador" to c.fluido_agua_radiador, "fluido_fluido_freio" to c.fluido_fluido_freio,
    "fluido_arla32" to c.fluido_arla32, "fluido_combustivel" to c.fluido_combustivel,
    "seg_extintor_validade" to c.seg_extintor_validade, "seg_triangulo" to c.seg_triangulo, "seg_macaco_chave_roda" to c.seg_macaco_chave_roda,
    "seg_cones_faixa" to c.seg_cones_faixa, "seg_epi_completo" to c.seg_epi_completo,
    "carr_lonas_cordas" to c.carr_lonas_cordas, "carr_portas_bau" to c.carr_portas_bau, "carr_assoalho_estado" to c.carr_assoalho_estado,
    "carr_travas_lacres" to c.carr_travas_lacres,
    "cab_bancos_cintos" to c.cab_bancos_cintos, "cab_espelhos_retrovisores" to c.cab_espelhos_retrovisores,
    "cab_limpador_parabrisa" to c.cab_limpador_parabrisa, "cab_ar_condicionado" to c.cab_ar_condicionado,
    "cab_freio_estacionamento" to c.cab_freio_estacionamento
)

private fun valoresChegada(c: ChecklistPosViagem): Map<String, Long> = mapOf(
    "avaria_carroceria" to c.avaria_carroceria, "avaria_cabine" to c.avaria_cabine, "avaria_pneus" to c.avaria_pneus,
    "avaria_espelhos" to c.avaria_espelhos, "avaria_farois" to c.avaria_farois,
    "pos_nivel_oleo" to c.pos_nivel_oleo, "pos_nivel_agua" to c.pos_nivel_agua, "pos_nivel_combustivel" to c.pos_nivel_combustivel,
    "pos_nivel_arla" to c.pos_nivel_arla,
    "limp_cabine_limpa" to c.limp_cabine_limpa, "limp_carroceria_limpa" to c.limp_carroceria_limpa, "limp_bau_vazio" to c.limp_bau_vazio,
    "func_freios_ok" to c.func_freios_ok, "func_direcao_ok" to c.func_direcao_ok, "func_suspensao_ok" to c.func_suspensao_ok,
    "func_motor_ruido" to c.func_motor_ruido, "func_cambio_ok" to c.func_cambio_ok,
    "pend_manutencao_urgente" to c.pend_manutencao_urgente, "pend_abastecimento_necessario" to c.pend_abastecimento_necessario,
    "pend_troca_oleo_proxima" to c.pend_troca_oleo_proxima
)

private val Verde = Color(0xFF059669)
private val VerdeClaro = Color(0xFFD1FAE5)
private val Vermelho = Color(0xFFDC2626)
private val VermelhoClaro = Color(0xFFFEE2E2)

/** Tela do checklist. [chegada] false = saída (pré-viagem), true = chegada (pós-viagem). */
@Composable
fun ChecklistTela(
    repository: AppRepository,
    chegada: Boolean,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val secoes = if (chegada) SECOES_CHEGADA else SECOES_SAIDA
    val todosItens = remember(chegada) { secoes.flatMap { it.itens } }
    val titulo = if (chegada) "Checklist de chegada" else "Checklist de saída"
    val motorista = remember { repository.getMotoristaLogado() }
    val viagemAtual = remember { repository.getViagemAtual() }
    val scope = rememberCoroutineScope()

    val respostas = remember { mutableStateMapOf<String, RespostaChecklist>() }
    val descricoes = remember { mutableStateMapOf<String, String>() }
    var observacoes by remember { mutableStateOf("") }
    var kmAtual by remember { mutableStateOf("") }
    var jaEnviadoEm by remember { mutableStateOf<String?>(null) }
    var secaoAberta by remember { mutableStateOf(0) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var sucesso by remember { mutableStateOf(false) }

    // Rascunho: se o motorista sair no meio, as respostas voltam.
    val rascunho = util.rememberRascunho(repository, if (chegada) "checklist_chegada" else "checklist_saida", viagemAtual?.viagem_id?.toString() ?: "")

    // Carrega o já enviado (somente leitura) ou o rascunho.
    LaunchedEffect(Unit) {
        val v = viagemAtual ?: return@LaunchedEffect
        val existente: Pair<Map<String, Long>, String>? = if (chegada) {
            repository.getChecklistPosPorViagem(v.viagem_id)?.let { c ->
                observacoes = c.observacoes ?: ""
                kmAtual = c.pend_km_atual?.toDoubleOrNull()?.let { util.formatarKmExibicao(it) } ?: ""
                valoresChegada(c) to c.data_checklist
            }
        } else {
            repository.getChecklistPrePorViagem(v.viagem_id)?.let { c ->
                observacoes = c.observacoes ?: ""
                valoresSaida(c) to c.data_checklist
            }
        }
        if (existente != null) {
            val (valores, data) = existente
            todosItens.forEach { item ->
                val v1 = valores[item.chave] == 1L
                respostas[item.chave] = if (v1 != item.invertido) RespostaChecklist.OK else RespostaChecklist.PROBLEMA
            }
            jaEnviadoEm = data
            secaoAberta = -1
        } else {
            rascunho.valores.forEach { (k, valor) ->
                when {
                    k.startsWith("r:") -> RespostaChecklist.entries.firstOrNull { it.name == valor }?.let { respostas[k.removePrefix("r:")] = it }
                    k.startsWith("d:") -> descricoes[k.removePrefix("d:")] = valor
                    k == "obs" -> observacoes = valor
                    k == "km" -> kmAtual = valor
                }
            }
            secaoAberta = secoes.indexOfFirst { s -> s.itens.any { it.chave !in respostas } }.coerceAtLeast(0)
        }
    }
    val somenteLeitura = jaEnviadoEm != null

    if (!somenteLeitura) {
        util.SalvarRascunhoAutomatico(
            rascunho,
            *(respostas.map { (k, r) -> "r:$k" to r.name } +
                descricoes.map { (k, d) -> "d:$k" to d } +
                listOf("obs" to observacoes, "km" to kmAtual)).toTypedArray()
        )
    }

    val respondidos = todosItens.count { it.chave in respostas }
    val problemas = todosItens.filter { respostas[it.chave] == RespostaChecklist.PROBLEMA }
    val semDescricao = problemas.filter { descricoes[it.chave].isNullOrBlank() }
    val faltam = todosItens.size - respondidos

    fun responder(item: ItemChecklist, r: RespostaChecklist) {
        if (somenteLeitura) return
        respostas[item.chave] = r
        // Parte completa e sem problema pendente de descrição: fecha e abre a próxima.
        val idx = secoes.indexOfFirst { s -> s.itens.any { it.chave == item.chave } }
        val secao = secoes[idx]
        val completa = secao.itens.all { it.chave in respostas }
        val semPendencia = secao.itens.none { respostas[it.chave] == RespostaChecklist.PROBLEMA && descricoes[it.chave].isNullOrBlank() }
        if (completa && semPendencia && r == RespostaChecklist.OK) {
            secaoAberta = secoes.indices.firstOrNull { i -> i > idx && secoes[i].itens.any { it.chave !in respostas } }
                ?: secoes.indices.firstOrNull { i -> secoes[i].itens.any { it.chave !in respostas } } ?: -1
        }
    }

    fun enviar() {
        val v = viagemAtual ?: return
        if (faltam > 0 || semDescricao.isNotEmpty() || salvando) return
        val linhasProblema = problemas.map { "• ${it.rotulo}: ${descricoes[it.chave].orEmpty().trim()}" }
        fun ok(chave: String): Boolean {
            val item = todosItens.first { it.chave == chave }
            return (respostas[chave] == RespostaChecklist.OK) != item.invertido
        }
        scope.launch {
            salvando = true
            try {
                // Hoje no formato da API (yyyy-MM-dd), no fuso do celular.
                val dataApi = kotlinx.datetime.Clock.System.now()
                    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
                if (!chegada) {
                    val obsFinal = listOfNotNull(
                        linhasProblema.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Problemas encontrados:\n"),
                        observacoes.trim().ifEmpty { null }
                    ).joinToString("\n\n").ifEmpty { null }
                    repository.salvarChecklistPre(
                        motoristaId = motorista?.motorista_id ?: "", viagemId = v.viagem_id, dataChecklist = dataApi, placa = v.placa,
                        docCnhValida = ok("doc_cnh_valida"), docCrlvVeiculo = ok("doc_crlv_veiculo"), docAnttValida = ok("doc_antt_valida"),
                        docSeguroCarga = ok("doc_seguro_carga"), docOrdemColeta = ok("doc_ordem_coleta"),
                        eletFarolDianteiro = ok("elet_farol_dianteiro"), eletFarolTraseiro = ok("elet_farol_traseiro"),
                        eletLuzFreio = ok("elet_luz_freio"), eletSetaDireita = ok("elet_seta_direita"), eletSetaEsquerda = ok("elet_seta_esquerda"),
                        eletLuzRe = ok("elet_luz_re"), eletPainelFuncionando = ok("elet_painel_funcionando"),
                        pneuCalibragemOk = ok("pneu_calibragem_ok"), pneuEstadoConservacao = ok("pneu_estado_conservacao"),
                        pneuEstepeOk = ok("pneu_estepe_ok"), pneuFerramentasTroca = ok("pneu_ferramentas_troca"),
                        fluidoOleoMotor = ok("fluido_oleo_motor"), fluidoAguaRadiador = ok("fluido_agua_radiador"),
                        fluidoFluidoFreio = ok("fluido_fluido_freio"), fluidoArla32 = ok("fluido_arla32"), fluidoCombustivel = ok("fluido_combustivel"),
                        segExtintorValidade = ok("seg_extintor_validade"), segTriangulo = ok("seg_triangulo"),
                        segMacacoChaveRoda = ok("seg_macaco_chave_roda"), segConesFaixa = ok("seg_cones_faixa"), segEpiCompleto = ok("seg_epi_completo"),
                        carrLonasCordas = ok("carr_lonas_cordas"), carrPortasBau = ok("carr_portas_bau"),
                        carrAssoalhoEstado = ok("carr_assoalho_estado"), carrTravasLacres = ok("carr_travas_lacres"),
                        cabBancosCintos = ok("cab_bancos_cintos"), cabEspelhosRetrovisores = ok("cab_espelhos_retrovisores"),
                        cabLimpadorParabrisa = ok("cab_limpador_parabrisa"), cabArCondicionado = ok("cab_ar_condicionado"),
                        cabFreioEstacionamento = ok("cab_freio_estacionamento"),
                        observacoes = obsFinal
                    )
                } else {
                    // Avarias e manutenção urgente têm campos de descrição próprios.
                    fun desc(chaves: List<String>) = chaves.filter { respostas[it] == RespostaChecklist.PROBLEMA }
                        .joinToString("; ") { k -> "${todosItens.first { it.chave == k }.rotulo.removeSuffix(" sem avaria")}: ${descricoes[k].orEmpty().trim()}" }
                        .ifEmpty { null }
                    val chavesAvaria = SECOES_CHEGADA.first().itens.map { it.chave }
                    val outrosProblemas = problemas.filter { it.chave !in chavesAvaria && it.chave != "pend_manutencao_urgente" }
                        .map { "• ${it.rotulo}: ${descricoes[it.chave].orEmpty().trim()}" }
                    val obsFinal = listOfNotNull(
                        outrosProblemas.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Problemas encontrados:\n"),
                        observacoes.trim().ifEmpty { null }
                    ).joinToString("\n\n").ifEmpty { null }
                    repository.salvarChecklistPos(
                        motoristaId = motorista?.motorista_id ?: "", viagemId = v.viagem_id, dataChecklist = dataApi, placa = v.placa,
                        avariaCarroceria = ok("avaria_carroceria"), avariaCabine = ok("avaria_cabine"), avariaPneus = ok("avaria_pneus"),
                        avariaEspelhos = ok("avaria_espelhos"), avariaFarois = ok("avaria_farois"), avariaDescricao = desc(chavesAvaria),
                        posNivelOleo = ok("pos_nivel_oleo"), posNivelAgua = ok("pos_nivel_agua"),
                        posNivelCombustivel = ok("pos_nivel_combustivel"), posNivelArla = ok("pos_nivel_arla"),
                        limpCabineLimpa = ok("limp_cabine_limpa"), limpCarroceriaLimpa = ok("limp_carroceria_limpa"), limpBauVazio = ok("limp_bau_vazio"),
                        funcFreiosOk = ok("func_freios_ok"), funcDirecaoOk = ok("func_direcao_ok"), funcSuspensaoOk = ok("func_suspensao_ok"),
                        funcMotorRuido = ok("func_motor_ruido"), funcCambioOk = ok("func_cambio_ok"),
                        pendManutencaoUrgente = ok("pend_manutencao_urgente"),
                        pendDescricaoManutencao = descricoes["pend_manutencao_urgente"]?.trim()?.takeIf { respostas["pend_manutencao_urgente"] == RespostaChecklist.PROBLEMA && it.isNotEmpty() },
                        pendAbastecimentoNecessario = ok("pend_abastecimento_necessario"), pendTrocaOleoProxima = ok("pend_troca_oleo_proxima"),
                        pendKmAtual = kmAtual.takeIf { it.isNotBlank() }?.let { util.normalizarKmParaEnvio(it) },
                        observacoes = obsFinal
                    )
                }
                rascunho.limpar()
                sucesso = true
            } catch (e: Exception) {
                erro = "Não foi possível salvar: ${mensagemErroAmigavel(e.message)}"
            }
            salvando = false
        }
    }

    if (erro != null) ui.ErroDialog(mensagem = erro!!, onDismiss = { erro = null })
    if (sucesso) {
        ui.SucessoDialog(
            mensagem = if (problemas.isEmpty()) "Checklist salvo! Tudo OK."
                else "Checklist salvo! O escritório vai receber ${if (problemas.size == 1) "o problema" else "os ${problemas.size} problemas"} anotado${if (problemas.size == 1) "" else "s"}.",
            onDismiss = { sucesso = false; onSucesso() }
        )
    }

    Scaffold(topBar = { GradientTopBar(title = titulo, onBackClick = onVoltar) }) { padding ->
        if (viagemAtual == null) {
            Column(
                Modifier.fillMaxSize().padding(padding).background(AppColors.Background).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Info, null, tint = AppColors.Orange, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Nenhuma viagem em andamento", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("O checklist é feito dentro de uma viagem. Inicie a viagem primeiro.", fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onVoltar, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) { Text("Voltar ao início") }
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background)) {
            CabecalhoProgresso(respondidos, todosItens.size, problemas.size, viagemAtual.placa)

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                jaEnviadoEm?.let { data ->
                    Row(
                        Modifier.fillMaxWidth().background(VerdeClaro, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, null, tint = Verde, modifier = Modifier.size(18.dp))
                        Text("Checklist enviado em ${dataBR(data)}. Só para consulta.", fontSize = 13.sp, color = Verde, fontWeight = FontWeight.Medium)
                    }
                }

                // Com tudo respondido: resumo antes de enviar.
                if (!somenteLeitura && faltam == 0) {
                    ResumoChecklist(todosItens.size - problemas.size, problemas, descricoes)
                }

                secoes.forEachIndexed { i, secao ->
                    CartaoSecao(
                        secao = secao,
                        aberta = secaoAberta == i,
                        respostas = respostas,
                        descricoes = descricoes,
                        somenteLeitura = somenteLeitura,
                        onAbrir = { secaoAberta = if (secaoAberta == i) -1 else i },
                        onResponder = { item, r -> responder(item, r) },
                        onDescrever = { item, texto -> descricoes[item.chave] = texto },
                        onTudoOk = {
                            secao.itens.filter { it.chave !in respostas }.forEach { respostas[it.chave] = RespostaChecklist.OK }
                            secaoAberta = secoes.indices.firstOrNull { j -> secoes[j].itens.any { it.chave !in respostas } } ?: -1
                        }
                    )
                }

                if (chegada) {
                    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("KM atual do veículo (opcional)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                            OutlinedTextField(
                                value = kmAtual, onValueChange = { kmAtual = util.formatarKmInput(it) }, readOnly = somenteLeitura,
                                placeholder = { Text("Ex.: 350000.5") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ui.darkTextFieldColors()
                            )
                        }
                    }
                }

                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Observações (opcional)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = observacoes, onValueChange = { observacoes = it }, readOnly = somenteLeitura,
                            placeholder = { Text("Algo mais que o escritório precise saber") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), shape = RoundedCornerShape(12.dp),
                            maxLines = 5, colors = ui.darkTextFieldColors()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (!somenteLeitura) {
                Surface(color = AppColors.Surface, shadowElevation = 6.dp) {
                    Button(
                        onClick = { enviar() },
                        enabled = faltam == 0 && semDescricao.isEmpty() && !salvando,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        if (salvando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(
                            when {
                                faltam > 0 -> if (faltam == 1) "Falta 1 item" else "Faltam $faltam itens"
                                semDescricao.isNotEmpty() -> "Descreva ${if (semDescricao.size == 1) "o problema" else "os ${semDescricao.size} problemas"}"
                                else -> "Enviar checklist"
                            },
                            fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoProgresso(respondidos: Int, total: Int, problemas: Int, placa: String) {
    Column(Modifier.fillMaxWidth().background(AppColors.Surface).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$respondidos de $total conferidos", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(
                if (problemas > 0) (if (problemas == 1) "1 problema" else "$problemas problemas") else placa,
                fontSize = 13.sp, fontWeight = if (problemas > 0) FontWeight.Bold else FontWeight.Normal,
                color = if (problemas > 0) Vermelho else AppColors.TextSecondary
            )
        }
        Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(7.dp)).background(AppColors.Divider)) {
            val ok = (respondidos - problemas).coerceAtLeast(0)
            if (ok > 0) Box(Modifier.weight(ok.toFloat()).fillMaxHeight().background(Verde))
            if (problemas > 0) Box(Modifier.weight(problemas.toFloat()).fillMaxHeight().background(Vermelho))
            val falta = total - respondidos
            if (falta > 0) Spacer(Modifier.weight(falta.toFloat()))
        }
    }
}

@Composable
private fun CartaoSecao(
    secao: SecaoChecklist,
    aberta: Boolean,
    respostas: Map<String, RespostaChecklist>,
    descricoes: Map<String, String>,
    somenteLeitura: Boolean,
    onAbrir: () -> Unit,
    onResponder: (ItemChecklist, RespostaChecklist) -> Unit,
    onDescrever: (ItemChecklist, String) -> Unit,
    onTudoOk: () -> Unit
) {
    val respondidos = secao.itens.count { it.chave in respostas }
    val problemas = secao.itens.count { respostas[it.chave] == RespostaChecklist.PROBLEMA }
    val completa = respondidos == secao.itens.size
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (aberta) 3.dp else 1.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onAbrir).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (fundo, cor, icone) = when {
                    problemas > 0 -> Triple(VermelhoClaro, Vermelho, Icons.Default.PriorityHigh)
                    completa -> Triple(VerdeClaro, Verde, Icons.Default.Check)
                    else -> Triple(Color(0xFF0097A7).copy(alpha = 0.1f), Color(0xFF0097A7), secao.icone)
                }
                Box(Modifier.size(36.dp).background(fundo, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp))
                }
                Text(secao.titulo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
                Text(
                    when {
                        problemas > 0 -> if (problemas == 1) "1 problema" else "$problemas problemas"
                        completa -> "${secao.itens.size} de ${secao.itens.size} OK"
                        else -> "$respondidos de ${secao.itens.size}"
                    },
                    fontSize = 13.sp, fontWeight = if (problemas > 0 || completa) FontWeight.Bold else FontWeight.Normal,
                    color = when { problemas > 0 -> Vermelho; completa -> Verde; else -> AppColors.TextSecondary }
                )
                Icon(if (aberta) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = AppColors.TextSecondary)
            }
            AnimatedVisibility(visible = aberta) {
                Column {
                    if (!somenteLeitura && !completa) {
                        Box(
                            Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp).fillMaxWidth().height(40.dp)
                                .border(1.dp, Verde.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp)).clickable(onClick = onTudoOk),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓ Tudo OK nesta parte", color = Verde, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    secao.itens.forEach { item ->
                        LinhaItem(
                            item = item,
                            resposta = respostas[item.chave],
                            descricao = descricoes[item.chave].orEmpty(),
                            somenteLeitura = somenteLeitura,
                            onResponder = { onResponder(item, it) },
                            onDescrever = { onDescrever(item, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaItem(
    item: ItemChecklist,
    resposta: RespostaChecklist?,
    descricao: String,
    somenteLeitura: Boolean,
    onResponder: (RespostaChecklist) -> Unit,
    onDescrever: (String) -> Unit
) {
    val problema = resposta == RespostaChecklist.PROBLEMA
    Column(Modifier.fillMaxWidth().background(if (problema) Vermelho.copy(alpha = 0.04f) else Color.Transparent)) {
        HorizontalDivider(color = AppColors.Divider)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.rotulo, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, AppColors.Divider, RoundedCornerShape(10.dp))) {
                BotaoResposta("OK", Icons.Default.Check, resposta == RespostaChecklist.OK, Verde, !somenteLeitura) { onResponder(RespostaChecklist.OK) }
                Box(Modifier.width(1.dp).height(38.dp).background(AppColors.Divider))
                BotaoResposta("Problema", null, problema, Vermelho, !somenteLeitura) { onResponder(RespostaChecklist.PROBLEMA) }
            }
        }
        if (problema) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = descricao, onValueChange = onDescrever, readOnly = somenteLeitura,
                    placeholder = { Text("O que está errado?") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Vermelho, focusedBorderColor = Vermelho),
                    maxLines = 3
                )
                if (!somenteLeitura) {
                    Text(
                        if (item.seguranca) "Item de segurança. Avise o escritório antes de sair." else "O escritório recebe essa descrição.",
                        fontSize = 12.sp, color = Vermelho, fontWeight = if (item.seguranca) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun BotaoResposta(texto: String, icone: ImageVector?, marcado: Boolean, cor: Color, habilitado: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.height(38.dp).background(if (marcado) cor else Color.Transparent)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icone != null) Icon(icone, null, tint = if (marcado) Color.White else AppColors.TextSecondary, modifier = Modifier.size(15.dp))
        Text(texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (marcado) Color.White else AppColors.TextSecondary)
    }
}

@Composable
private fun ResumoChecklist(totalOk: Int, problemas: List<ItemChecklist>, descricoes: Map<String, String>) {
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, AppColors.Divider)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumeroResumo(totalOk, "OK", Verde, VerdeClaro, Modifier.weight(1f))
                NumeroResumo(problemas.size, if (problemas.size == 1) "com problema" else "com problema", Vermelho, VermelhoClaro, Modifier.weight(1f))
            }
            if (problemas.isEmpty()) {
                Text("Tudo certo. Confira e envie.", fontSize = 14.sp, color = AppColors.TextSecondary)
            } else {
                problemas.forEach { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).background(VermelhoClaro, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PriorityHigh, null, tint = Vermelho, modifier = Modifier.size(14.dp))
                        }
                        Column {
                            Text(p.rotulo, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                            val d = descricoes[p.chave].orEmpty()
                            Text(d.ifBlank { "Falta descrever" }, fontSize = 13.sp, color = if (d.isBlank()) Vermelho else AppColors.TextSecondary)
                        }
                    }
                }
                val seguranca = problemas.filter { it.seguranca }
                if (seguranca.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Text(
                            "${seguranca.joinToString(", ") { it.rotulo }}: item de segurança. Avise o escritório antes de sair com o caminhão.",
                            fontSize = 13.sp, color = Color(0xFF92400E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumeroResumo(valor: Int, rotulo: String, cor: Color, fundo: Color, modifier: Modifier) {
    Column(modifier.background(fundo, RoundedCornerShape(10.dp)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cor)
        Text(rotulo, fontSize = 12.sp, color = cor)
    }
}

/** "2026-10-20" → "20/10/2026". Outro formato: como veio. */
private fun dataBR(data: String): String {
    val p = data.trim().split(" ").first().split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else data
}
