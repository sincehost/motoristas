package screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.dataAtualFormatada
import util.converterDataParaAPI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ChecklistPreViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }
    var secaoExpandida by remember { mutableStateOf(0) }

    // Mensagens de erro/sucesso (dialog modal)
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    fun mostrarMensagem(mensagem: String, erro: Boolean = false) {
        if (erro) erroMsg = mensagem else sucessoMsg = mensagem
    }

    LaunchedEffect(Unit) {
        carregando = true
        viagemAtual = repository.getViagemAtual()
        if (viagemAtual == null) semViagemAberta = true
        carregando = false
    }

    // === DOCUMENTAÇÃO ===
    var docCnhValida by rememberSaveable { mutableStateOf(false) }
    var docCrlvVeiculo by rememberSaveable { mutableStateOf(false) }
    var docAnttValida by rememberSaveable { mutableStateOf(false) }
    var docSeguroCarga by rememberSaveable { mutableStateOf(false) }
    var docOrdemColeta by rememberSaveable { mutableStateOf(false) }

    // === PARTE ELÉTRICA ===
    var eletFarolDianteiro by rememberSaveable { mutableStateOf(false) }
    var eletFarolTraseiro by rememberSaveable { mutableStateOf(false) }
    var eletLuzFreio by rememberSaveable { mutableStateOf(false) }
    var eletSetaDireita by rememberSaveable { mutableStateOf(false) }
    var eletSetaEsquerda by rememberSaveable { mutableStateOf(false) }
    var eletLuzRe by rememberSaveable { mutableStateOf(false) }
    var eletPainelFuncionando by rememberSaveable { mutableStateOf(false) }

    // === PNEUS E RODAS ===
    var pneuCalibragemOk by rememberSaveable { mutableStateOf(false) }
    var pneuEstadoConservacao by rememberSaveable { mutableStateOf(false) }
    var pneuEstepeOk by rememberSaveable { mutableStateOf(false) }
    var pneuFerramentasTroca by rememberSaveable { mutableStateOf(false) }

    // === FLUIDOS E NÍVEIS ===
    var fluidoOleoMotor by rememberSaveable { mutableStateOf(false) }
    var fluidoAguaRadiador by rememberSaveable { mutableStateOf(false) }
    var fluidoFluidoFreio by rememberSaveable { mutableStateOf(false) }
    var fluidoArla32 by rememberSaveable { mutableStateOf(false) }
    var fluidoCombustivel by rememberSaveable { mutableStateOf(false) }

    // === EQUIPAMENTOS DE SEGURANÇA ===
    var segExtintorValidade by rememberSaveable { mutableStateOf(false) }
    var segTriangulo by rememberSaveable { mutableStateOf(false) }
    var segMacacoChaveRoda by rememberSaveable { mutableStateOf(false) }
    var segConesFaixa by rememberSaveable { mutableStateOf(false) }
    var segEpiCompleto by rememberSaveable { mutableStateOf(false) }

    // === CARROCERIA / BAÚ ===
    var carrLonasCordas by rememberSaveable { mutableStateOf(false) }
    var carrPortasBau by rememberSaveable { mutableStateOf(false) }
    var carrAssoalhoEstado by rememberSaveable { mutableStateOf(false) }
    var carrTravasLacres by rememberSaveable { mutableStateOf(false) }

    // === CABINE ===
    var cabBancosCintos by rememberSaveable { mutableStateOf(false) }
    var cabEspelhosRetrovisores by rememberSaveable { mutableStateOf(false) }
    var cabLimpadorParabrisa by rememberSaveable { mutableStateOf(false) }
    var cabArCondicionado by rememberSaveable { mutableStateOf(false) }
    var cabFreioEstacionamento by rememberSaveable { mutableStateOf(false) }

    var observacoes by rememberSaveable { mutableStateOf("") }

    // ★ Marcar / Desmarcar Todos
    fun marcarTodos(valor: Boolean) {
        docCnhValida = valor; docCrlvVeiculo = valor; docAnttValida = valor; docSeguroCarga = valor; docOrdemColeta = valor
        eletFarolDianteiro = valor; eletFarolTraseiro = valor; eletLuzFreio = valor; eletSetaDireita = valor; eletSetaEsquerda = valor; eletLuzRe = valor; eletPainelFuncionando = valor
        pneuCalibragemOk = valor; pneuEstadoConservacao = valor; pneuEstepeOk = valor; pneuFerramentasTroca = valor
        fluidoOleoMotor = valor; fluidoAguaRadiador = valor; fluidoFluidoFreio = valor; fluidoArla32 = valor; fluidoCombustivel = valor
        segExtintorValidade = valor; segTriangulo = valor; segMacacoChaveRoda = valor; segConesFaixa = valor; segEpiCompleto = valor
        carrLonasCordas = valor; carrPortasBau = valor; carrAssoalhoEstado = valor; carrTravasLacres = valor
        cabBancosCintos = valor; cabEspelhosRetrovisores = valor; cabLimpadorParabrisa = valor; cabArCondicionado = valor; cabFreioEstacionamento = valor
    }

    val totalItens = 35
    val itensMarcados = listOf(
        docCnhValida, docCrlvVeiculo, docAnttValida, docSeguroCarga, docOrdemColeta,
        eletFarolDianteiro, eletFarolTraseiro, eletLuzFreio, eletSetaDireita, eletSetaEsquerda, eletLuzRe, eletPainelFuncionando,
        pneuCalibragemOk, pneuEstadoConservacao, pneuEstepeOk, pneuFerramentasTroca,
        fluidoOleoMotor, fluidoAguaRadiador, fluidoFluidoFreio, fluidoArla32, fluidoCombustivel,
        segExtintorValidade, segTriangulo, segMacacoChaveRoda, segConesFaixa, segEpiCompleto,
        carrLonasCordas, carrPortasBau, carrAssoalhoEstado, carrTravasLacres,
        cabBancosCintos, cabEspelhosRetrovisores, cabLimpadorParabrisa, cabArCondicionado, cabFreioEstacionamento
    ).count { it }

    fun salvarChecklist() {
        if (viagemAtual == null) return
        val dataApi = converterDataParaAPI(dataAtualFormatada())

        scope.launch {
            salvando = true
            try {
                repository.salvarChecklistPre(
                    motoristaId = motorista?.motorista_id ?: "", viagemId = viagemAtual!!.viagem_id,
                    dataChecklist = dataApi, placa = "",
                    docCnhValida = docCnhValida, docCrlvVeiculo = docCrlvVeiculo, docAnttValida = docAnttValida,
                    docSeguroCarga = docSeguroCarga, docOrdemColeta = docOrdemColeta,
                    eletFarolDianteiro = eletFarolDianteiro, eletFarolTraseiro = eletFarolTraseiro,
                    eletLuzFreio = eletLuzFreio, eletSetaDireita = eletSetaDireita, eletSetaEsquerda = eletSetaEsquerda,
                    eletLuzRe = eletLuzRe, eletPainelFuncionando = eletPainelFuncionando,
                    pneuCalibragemOk = pneuCalibragemOk, pneuEstadoConservacao = pneuEstadoConservacao,
                    pneuEstepeOk = pneuEstepeOk, pneuFerramentasTroca = pneuFerramentasTroca,
                    fluidoOleoMotor = fluidoOleoMotor, fluidoAguaRadiador = fluidoAguaRadiador,
                    fluidoFluidoFreio = fluidoFluidoFreio, fluidoArla32 = fluidoArla32, fluidoCombustivel = fluidoCombustivel,
                    segExtintorValidade = segExtintorValidade, segTriangulo = segTriangulo,
                    segMacacoChaveRoda = segMacacoChaveRoda, segConesFaixa = segConesFaixa, segEpiCompleto = segEpiCompleto,
                    carrLonasCordas = carrLonasCordas, carrPortasBau = carrPortasBau,
                    carrAssoalhoEstado = carrAssoalhoEstado, carrTravasLacres = carrTravasLacres,
                    cabBancosCintos = cabBancosCintos, cabEspelhosRetrovisores = cabEspelhosRetrovisores,
                    cabLimpadorParabrisa = cabLimpadorParabrisa, cabArCondicionado = cabArCondicionado,
                    cabFreioEstacionamento = cabFreioEstacionamento,
                    observacoes = observacoes.ifEmpty { null }
                )

                try {
                    val resp = api.ApiClient.salvarChecklistPre(
                        api.SalvarChecklistPreRequest(
                            motorista_id = motorista?.motorista_id ?: "", viagem_id = viagemAtual!!.viagem_id.toInt(),
                            data_checklist = dataApi, placa = "",
                            doc_cnh_valida = if (docCnhValida) 1 else 0, doc_crlv_veiculo = if (docCrlvVeiculo) 1 else 0,
                            doc_antt_valida = if (docAnttValida) 1 else 0, doc_seguro_carga = if (docSeguroCarga) 1 else 0,
                            doc_ordem_coleta = if (docOrdemColeta) 1 else 0,
                            elet_farol_dianteiro = if (eletFarolDianteiro) 1 else 0, elet_farol_traseiro = if (eletFarolTraseiro) 1 else 0,
                            elet_luz_freio = if (eletLuzFreio) 1 else 0, elet_seta_direita = if (eletSetaDireita) 1 else 0,
                            elet_seta_esquerda = if (eletSetaEsquerda) 1 else 0, elet_luz_re = if (eletLuzRe) 1 else 0,
                            elet_painel_funcionando = if (eletPainelFuncionando) 1 else 0,
                            pneu_calibragem_ok = if (pneuCalibragemOk) 1 else 0, pneu_estado_conservacao = if (pneuEstadoConservacao) 1 else 0,
                            pneu_estepe_ok = if (pneuEstepeOk) 1 else 0, pneu_ferramentas_troca = if (pneuFerramentasTroca) 1 else 0,
                            fluido_oleo_motor = if (fluidoOleoMotor) 1 else 0, fluido_agua_radiador = if (fluidoAguaRadiador) 1 else 0,
                            fluido_fluido_freio = if (fluidoFluidoFreio) 1 else 0, fluido_arla32 = if (fluidoArla32) 1 else 0,
                            fluido_combustivel = if (fluidoCombustivel) 1 else 0,
                            seg_extintor_validade = if (segExtintorValidade) 1 else 0, seg_triangulo = if (segTriangulo) 1 else 0,
                            seg_macaco_chave_roda = if (segMacacoChaveRoda) 1 else 0, seg_cones_faixa = if (segConesFaixa) 1 else 0,
                            seg_epi_completo = if (segEpiCompleto) 1 else 0,
                            carr_lonas_cordas = if (carrLonasCordas) 1 else 0, carr_portas_bau = if (carrPortasBau) 1 else 0,
                            carr_assoalho_estado = if (carrAssoalhoEstado) 1 else 0, carr_travas_lacres = if (carrTravasLacres) 1 else 0,
                            cab_bancos_cintos = if (cabBancosCintos) 1 else 0, cab_espelhos_retrovisores = if (cabEspelhosRetrovisores) 1 else 0,
                            cab_limpador_parabrisa = if (cabLimpadorParabrisa) 1 else 0, cab_ar_condicionado = if (cabArCondicionado) 1 else 0,
                            cab_freio_estacionamento = if (cabFreioEstacionamento) 1 else 0,
                            observacoes = observacoes.ifEmpty { null }
                        )
                    )
                    if (resp.status == "ok") {
                        val checklists = repository.getChecklistsPreParaSincronizar()
                        checklists.lastOrNull()?.let { repository.marcarChecklistPreSincronizado(it.id) }
                    }
                } catch (_: Exception) { }

                mostrarMensagem("Checklist pré-viagem salvo com sucesso!")
            } catch (e: Exception) {
                mostrarMensagem("Erro ao salvar: ${e.message}", erro = true)
            }
            salvando = false
        }
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) {
        ui.ErroDialog(mensagem = erroMsg!!, onDismiss = { erroMsg = null })
    }
    if (sucessoMsg != null) {
        ui.SucessoDialog(mensagem = sucessoMsg!!, onDismiss = { sucessoMsg = null; onSucesso() })
    }

    Scaffold(
        topBar = { GradientTopBar(title = "Checklist Pré-Viagem", onBackClick = onVoltar) }
    ) { padding ->
        if (carregando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else if (semViagemAberta) {
            Column(
                Modifier.fillMaxSize().padding(padding).background(AppColors.Background).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, null, tint = AppColors.Orange, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhuma viagem em andamento", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Orange)
                        Spacer(Modifier.height(8.dp))
                        Text("Inicie uma viagem para preencher o checklist.", color = AppColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onVoltar, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary), shape = RoundedCornerShape(12.dp)) { Text("Voltar ao Dashboard") }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {
                // Header com progresso
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Primary.copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Checklist, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Checklist Pré-Viagem", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Primary)
                                Text(viagemAtual?.destino ?: "", fontSize = 13.sp, color = AppColors.TextSecondary)
                            }
                            Surface(color = AppColors.Primary, shape = RoundedCornerShape(20.dp)) {
                                Text("$itensMarcados/$totalItens", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { itensMarcados.toFloat() / totalItens }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = if (itensMarcados == totalItens) AppColors.Secondary else AppColors.Primary, trackColor = Color(0xFFE0E0E0))

                        Spacer(Modifier.height(12.dp))

                        // ★ Botão Marcar/Desmarcar Todos
                        val todosOk = itensMarcados == totalItens
                        OutlinedButton(
                            onClick = { marcarTodos(!todosOk) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (todosOk) AppColors.Error else AppColors.Secondary
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (todosOk) AppColors.Error.copy(alpha = 0.5f) else AppColors.Secondary.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                if (todosOk) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (todosOk) "Desmarcar Todos" else "Marcar Todos",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                ChecklistSecao(titulo = "Documentação", icone = Icons.Default.Description, cor = Color(0xFF1565C0), expandida = secaoExpandida == 0, onClick = { secaoExpandida = if (secaoExpandida == 0) -1 else 0 }, marcados = listOf(docCnhValida, docCrlvVeiculo, docAnttValida, docSeguroCarga, docOrdemColeta).count { it }, total = 5) {
                    ChecklistItem("CNH válida e dentro da validade", docCnhValida) { docCnhValida = it }
                    ChecklistItem("CRLV do veículo em dia", docCrlvVeiculo) { docCrlvVeiculo = it }
                    ChecklistItem("ANTT válida", docAnttValida) { docAnttValida = it }
                    ChecklistItem("Seguro da carga", docSeguroCarga) { docSeguroCarga = it }
                    ChecklistItem("Ordem de coleta / manifesto", docOrdemColeta) { docOrdemColeta = it }
                }
                Spacer(Modifier.height(8.dp))
                ChecklistSecao(titulo = "Parte Elétrica", icone = Icons.Default.ElectricalServices, cor = Color(0xFFF57F17), expandida = secaoExpandida == 1, onClick = { secaoExpandida = if (secaoExpandida == 1) -1 else 1 }, marcados = listOf(eletFarolDianteiro, eletFarolTraseiro, eletLuzFreio, eletSetaDireita, eletSetaEsquerda, eletLuzRe, eletPainelFuncionando).count { it }, total = 7) {
                    ChecklistItem("Farol dianteiro", eletFarolDianteiro) { eletFarolDianteiro = it }
                    ChecklistItem("Farol traseiro", eletFarolTraseiro) { eletFarolTraseiro = it }
                    ChecklistItem("Luz de freio", eletLuzFreio) { eletLuzFreio = it }
                    ChecklistItem("Seta direita", eletSetaDireita) { eletSetaDireita = it }
                    ChecklistItem("Seta esquerda", eletSetaEsquerda) { eletSetaEsquerda = it }
                    ChecklistItem("Luz de ré", eletLuzRe) { eletLuzRe = it }
                    ChecklistItem("Painel funcionando", eletPainelFuncionando) { eletPainelFuncionando = it }
                }
                Spacer(Modifier.height(8.dp))
                ChecklistSecao(titulo = "Pneus e Rodas", icone = Icons.Default.TireRepair, cor = Color(0xFF2E7D32), expandida = secaoExpandida == 2, onClick = { secaoExpandida = if (secaoExpandida == 2) -1 else 2 }, marcados = listOf(pneuCalibragemOk, pneuEstadoConservacao, pneuEstepeOk, pneuFerramentasTroca).count { it }, total = 4) {
                    ChecklistItem("Calibragem OK", pneuCalibragemOk) { pneuCalibragemOk = it }
                    ChecklistItem("Estado de conservação OK", pneuEstadoConservacao) { pneuEstadoConservacao = it }
                    ChecklistItem("Estepe em condições", pneuEstepeOk) { pneuEstepeOk = it }
                    ChecklistItem("Ferramentas de troca", pneuFerramentasTroca) { pneuFerramentasTroca = it }
                }
                Spacer(Modifier.height(8.dp))
                ChecklistSecao(titulo = "Fluidos e Níveis", icone = Icons.Default.WaterDrop, cor = Color(0xFF0097A7), expandida = secaoExpandida == 3, onClick = { secaoExpandida = if (secaoExpandida == 3) -1 else 3 }, marcados = listOf(fluidoOleoMotor, fluidoAguaRadiador, fluidoFluidoFreio, fluidoArla32, fluidoCombustivel).count { it }, total = 5) {
                    ChecklistItem("Óleo do motor", fluidoOleoMotor) { fluidoOleoMotor = it }
                    ChecklistItem("Água do radiador", fluidoAguaRadiador) { fluidoAguaRadiador = it }
                    ChecklistItem("Fluido de freio", fluidoFluidoFreio) { fluidoFluidoFreio = it }
                    ChecklistItem("ARLA 32", fluidoArla32) { fluidoArla32 = it }
                    ChecklistItem("Combustível suficiente", fluidoCombustivel) { fluidoCombustivel = it }
                }
                Spacer(Modifier.height(8.dp))
                ChecklistSecao(titulo = "Equipamentos de Segurança", icone = Icons.Default.HealthAndSafety, cor = Color(0xFFD32F2F), expandida = secaoExpandida == 4, onClick = { secaoExpandida = if (secaoExpandida == 4) -1 else 4 }, marcados = listOf(segExtintorValidade, segTriangulo, segMacacoChaveRoda, segConesFaixa, segEpiCompleto).count { it }, total = 5) {
                    ChecklistItem("Extintor na validade", segExtintorValidade) { segExtintorValidade = it }
                    ChecklistItem("Triângulo de sinalização", segTriangulo) { segTriangulo = it }
                    ChecklistItem("Macaco e chave de roda", segMacacoChaveRoda) { segMacacoChaveRoda = it }
                    ChecklistItem("Cones / faixa refletiva", segConesFaixa) { segConesFaixa = it }
                    ChecklistItem("EPI completo", segEpiCompleto) { segEpiCompleto = it }
                }
                Spacer(Modifier.height(8.dp))
                ChecklistSecao(titulo = "Carroceria / Baú", icone = Icons.Default.Inventory2, cor = Color(0xFF6A1B9A), expandida = secaoExpandida == 5, onClick = { secaoExpandida = if (secaoExpandida == 5) -1 else 5 }, marcados = listOf(carrLonasCordas, carrPortasBau, carrAssoalhoEstado, carrTravasLacres).count { it }, total = 4) {
                    ChecklistItem("Lonas e cordas", carrLonasCordas) { carrLonasCordas = it }
                    ChecklistItem("Portas do baú", carrPortasBau) { carrPortasBau = it }
                    ChecklistItem("Assoalho em bom estado", carrAssoalhoEstado) { carrAssoalhoEstado = it }
                    ChecklistItem("Travas e lacres", carrTravasLacres) { carrTravasLacres = it }
                }
                Spacer(Modifier.height(8.dp))
                ChecklistSecao(titulo = "Cabine", icone = Icons.Default.AirlineSeatReclineExtra, cor = Color(0xFF00695C), expandida = secaoExpandida == 6, onClick = { secaoExpandida = if (secaoExpandida == 6) -1 else 6 }, marcados = listOf(cabBancosCintos, cabEspelhosRetrovisores, cabLimpadorParabrisa, cabArCondicionado, cabFreioEstacionamento).count { it }, total = 5) {
                    ChecklistItem("Bancos e cintos de segurança", cabBancosCintos) { cabBancosCintos = it }
                    ChecklistItem("Espelhos retrovisores", cabEspelhosRetrovisores) { cabEspelhosRetrovisores = it }
                    ChecklistItem("Limpador de para-brisa", cabLimpadorParabrisa) { cabLimpadorParabrisa = it }
                    ChecklistItem("Ar condicionado", cabArCondicionado) { cabArCondicionado = it }
                    ChecklistItem("Freio de estacionamento", cabFreioEstacionamento) { cabFreioEstacionamento = it }
                }

                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Observações", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = observacoes, onValueChange = { observacoes = it }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp), placeholder = { Text("Observações adicionais (opcional)", color = Color(0xFF9CA3AF)) }, maxLines = 4, colors = ui.darkTextFieldColors())
                    }
                }

                Spacer(Modifier.height(20.dp))
                Button(onClick = { salvarChecklist() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    if (salvando) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) }
                    else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("SALVAR CHECKLIST PRÉ-VIAGEM", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ===============================
// COMPONENTES REUTILIZÁVEIS
// ===============================
@Composable
fun ChecklistSecao(titulo: String, icone: ImageVector, cor: Color, expandida: Boolean, onClick: () -> Unit, marcados: Int, total: Int, content: @Composable ColumnScope.() -> Unit) {
    val todosOk = marcados == total
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = if (expandida) 4.dp else 1.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(cor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icone, null, tint = cor, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextPrimary)
                    Text("$marcados de $total itens verificados", fontSize = 12.sp, color = if (todosOk) AppColors.Secondary else AppColors.TextSecondary)
                }
                if (todosOk) { Icon(Icons.Default.CheckCircle, null, tint = AppColors.Secondary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
                Icon(if (expandida) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = AppColors.TextSecondary)
            }
            AnimatedVisibility(visible = expandida) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { HorizontalDivider(color = Color(0xFFF0F0F0)); Spacer(Modifier.height(8.dp)); content() }
            }
        }
    }
}

@Composable
fun ChecklistItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, colors = CheckboxDefaults.colors(checkedColor = AppColors.Secondary, uncheckedColor = Color(0xFFBDBDBD)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 14.sp, color = if (checked) AppColors.TextPrimary else AppColors.TextSecondary, fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal)
        if (checked) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.Check, null, tint = AppColors.Secondary, modifier = Modifier.size(18.dp)) }
    }
}
