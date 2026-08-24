package screens

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
import util.formatarKmInput
import util.formatarKmExibicao
import util.normalizarKmParaEnvio
import util.mensagemErroAmigavel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ChecklistPreViagemScreen(repository: AppRepository, onVoltar: () -> Unit, onSucesso: () -> Unit) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }
    var semViagem by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }
    // Checklist já enviado nessa viagem — trava a tela em somente-leitura.
    var jaEnviado by remember { mutableStateOf(false) }
    var dataEnvio by remember { mutableStateOf("") }

    var docCnhValida by remember { mutableStateOf(false) }; var docCrlvVeiculo by remember { mutableStateOf(false) }
    var docAnttValida by remember { mutableStateOf(false) }; var docSeguroCarga by remember { mutableStateOf(false) }
    var docOrdemColeta by remember { mutableStateOf(false) }
    var eletFarolDianteiro by remember { mutableStateOf(false) }; var eletFarolTraseiro by remember { mutableStateOf(false) }
    var eletLuzFreio by remember { mutableStateOf(false) }; var eletSetaDireita by remember { mutableStateOf(false) }
    var eletSetaEsquerda by remember { mutableStateOf(false) }; var eletLuzRe by remember { mutableStateOf(false) }
    var eletPainelFuncionando by remember { mutableStateOf(false) }
    var pneuCalibragemOk by remember { mutableStateOf(false) }; var pneuEstadoConservacao by remember { mutableStateOf(false) }
    var pneuEstepeOk by remember { mutableStateOf(false) }; var pneuFerramentasTroca by remember { mutableStateOf(false) }
    var fluidoOleoMotor by remember { mutableStateOf(false) }; var fluidoAguaRadiador by remember { mutableStateOf(false) }
    var fluidoFluidoFreio by remember { mutableStateOf(false) }; var fluidoArla32 by remember { mutableStateOf(false) }
    var fluidoCombustivel by remember { mutableStateOf(false) }
    var segExtintorValidade by remember { mutableStateOf(false) }; var segTriangulo by remember { mutableStateOf(false) }
    var segMacacoChaveRoda by remember { mutableStateOf(false) }; var segConesFaixa by remember { mutableStateOf(false) }
    var segEpiCompleto by remember { mutableStateOf(false) }
    var carrLonasCordas by remember { mutableStateOf(false) }; var carrPortasBau by remember { mutableStateOf(false) }
    var carrAssoalhoEstado by remember { mutableStateOf(false) }; var carrTravasLacres by remember { mutableStateOf(false) }
    var cabBancosCintos by remember { mutableStateOf(false) }; var cabEspelhosRetrovisores by remember { mutableStateOf(false) }
    var cabLimpadorParabrisa by remember { mutableStateOf(false) }; var cabArCondicionado by remember { mutableStateOf(false) }
    var cabFreioEstacionamento by remember { mutableStateOf(false) }
    var observacoes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val v = repository.getViagemAtual()
            viagemAtual = v
            if (v == null) {
                semViagem = true
            } else {
                val existente = repository.getChecklistPrePorViagem(v.viagem_id)
                if (existente != null) {
                    jaEnviado = true
                    dataEnvio = existente.data_checklist
                    docCnhValida = existente.doc_cnh_valida == 1L; docCrlvVeiculo = existente.doc_crlv_veiculo == 1L
                    docAnttValida = existente.doc_antt_valida == 1L; docSeguroCarga = existente.doc_seguro_carga == 1L
                    docOrdemColeta = existente.doc_ordem_coleta == 1L
                    eletFarolDianteiro = existente.elet_farol_dianteiro == 1L; eletFarolTraseiro = existente.elet_farol_traseiro == 1L
                    eletLuzFreio = existente.elet_luz_freio == 1L; eletSetaDireita = existente.elet_seta_direita == 1L
                    eletSetaEsquerda = existente.elet_seta_esquerda == 1L; eletLuzRe = existente.elet_luz_re == 1L
                    eletPainelFuncionando = existente.elet_painel_funcionando == 1L
                    pneuCalibragemOk = existente.pneu_calibragem_ok == 1L; pneuEstadoConservacao = existente.pneu_estado_conservacao == 1L
                    pneuEstepeOk = existente.pneu_estepe_ok == 1L; pneuFerramentasTroca = existente.pneu_ferramentas_troca == 1L
                    fluidoOleoMotor = existente.fluido_oleo_motor == 1L; fluidoAguaRadiador = existente.fluido_agua_radiador == 1L
                    fluidoFluidoFreio = existente.fluido_fluido_freio == 1L; fluidoArla32 = existente.fluido_arla32 == 1L
                    fluidoCombustivel = existente.fluido_combustivel == 1L
                    segExtintorValidade = existente.seg_extintor_validade == 1L; segTriangulo = existente.seg_triangulo == 1L
                    segMacacoChaveRoda = existente.seg_macaco_chave_roda == 1L; segConesFaixa = existente.seg_cones_faixa == 1L
                    segEpiCompleto = existente.seg_epi_completo == 1L
                    carrLonasCordas = existente.carr_lonas_cordas == 1L; carrPortasBau = existente.carr_portas_bau == 1L
                    carrAssoalhoEstado = existente.carr_assoalho_estado == 1L; carrTravasLacres = existente.carr_travas_lacres == 1L
                    cabBancosCintos = existente.cab_bancos_cintos == 1L; cabEspelhosRetrovisores = existente.cab_espelhos_retrovisores == 1L
                    cabLimpadorParabrisa = existente.cab_limpador_parabrisa == 1L; cabArCondicionado = existente.cab_ar_condicionado == 1L
                    cabFreioEstacionamento = existente.cab_freio_estacionamento == 1L
                    observacoes = existente.observacoes ?: ""
                }
            }
        } catch (_: Exception) {
            semViagem = true
        }
        carregando = false
    }

    val itensOk = listOf(docCnhValida, docCrlvVeiculo, docAnttValida, docSeguroCarga, docOrdemColeta,
        eletFarolDianteiro, eletFarolTraseiro, eletLuzFreio, eletSetaDireita, eletSetaEsquerda, eletLuzRe, eletPainelFuncionando,
        pneuCalibragemOk, pneuEstadoConservacao, pneuEstepeOk, pneuFerramentasTroca,
        fluidoOleoMotor, fluidoAguaRadiador, fluidoFluidoFreio, fluidoArla32, fluidoCombustivel,
        segExtintorValidade, segTriangulo, segMacacoChaveRoda, segConesFaixa, segEpiCompleto,
        carrLonasCordas, carrPortasBau, carrAssoalhoEstado, carrTravasLacres,
        cabBancosCintos, cabEspelhosRetrovisores, cabLimpadorParabrisa, cabArCondicionado, cabFreioEstacionamento).count { it }

    // Botão "Marcar/Desmarcar Todos" — Android já tinha, iOS não.
    fun marcarTodos(valor: Boolean) {
        docCnhValida = valor; docCrlvVeiculo = valor; docAnttValida = valor; docSeguroCarga = valor; docOrdemColeta = valor
        eletFarolDianteiro = valor; eletFarolTraseiro = valor; eletLuzFreio = valor; eletSetaDireita = valor; eletSetaEsquerda = valor; eletLuzRe = valor; eletPainelFuncionando = valor
        pneuCalibragemOk = valor; pneuEstadoConservacao = valor; pneuEstepeOk = valor; pneuFerramentasTroca = valor
        fluidoOleoMotor = valor; fluidoAguaRadiador = valor; fluidoFluidoFreio = valor; fluidoArla32 = valor; fluidoCombustivel = valor
        segExtintorValidade = valor; segTriangulo = valor; segMacacoChaveRoda = valor; segConesFaixa = valor; segEpiCompleto = valor
        carrLonasCordas = valor; carrPortasBau = valor; carrAssoalhoEstado = valor; carrTravasLacres = valor
        cabBancosCintos = valor; cabEspelhosRetrovisores = valor; cabLimpadorParabrisa = valor; cabArCondicionado = valor; cabFreioEstacionamento = valor
    }

    fun salvar() { if (viagemAtual == null || jaEnviado) return; val d = converterDataParaAPI(dataAtualFormatada()); scope.launch {
        salvando = true; try { repository.salvarChecklistPre(motorista?.motorista_id ?: "", viagemAtual!!.viagem_id, d, "",
            docCnhValida, docCrlvVeiculo, docAnttValida, docSeguroCarga, docOrdemColeta,
            eletFarolDianteiro, eletFarolTraseiro, eletLuzFreio, eletSetaDireita, eletSetaEsquerda, eletLuzRe, eletPainelFuncionando,
            pneuCalibragemOk, pneuEstadoConservacao, pneuEstepeOk, pneuFerramentasTroca,
            fluidoOleoMotor, fluidoAguaRadiador, fluidoFluidoFreio, fluidoArla32, fluidoCombustivel,
            segExtintorValidade, segTriangulo, segMacacoChaveRoda, segConesFaixa, segEpiCompleto,
            carrLonasCordas, carrPortasBau, carrAssoalhoEstado, carrTravasLacres,
            cabBancosCintos, cabEspelhosRetrovisores, cabLimpadorParabrisa, cabArCondicionado, cabFreioEstacionamento,
            observacoes.ifEmpty { null })
        try { val r = api.ApiClient.salvarChecklistPre(api.SalvarChecklistPreRequest(motorista?.motorista_id ?: "", viagemAtual!!.viagem_id.toInt(), d, "",
            if(docCnhValida)1 else 0, if(docCrlvVeiculo)1 else 0, if(docAnttValida)1 else 0, if(docSeguroCarga)1 else 0, if(docOrdemColeta)1 else 0,
            if(eletFarolDianteiro)1 else 0, if(eletFarolTraseiro)1 else 0, if(eletLuzFreio)1 else 0, if(eletSetaDireita)1 else 0, if(eletSetaEsquerda)1 else 0, if(eletLuzRe)1 else 0, if(eletPainelFuncionando)1 else 0,
            if(pneuCalibragemOk)1 else 0, if(pneuEstadoConservacao)1 else 0, if(pneuEstepeOk)1 else 0, if(pneuFerramentasTroca)1 else 0,
            if(fluidoOleoMotor)1 else 0, if(fluidoAguaRadiador)1 else 0, if(fluidoFluidoFreio)1 else 0, if(fluidoArla32)1 else 0, if(fluidoCombustivel)1 else 0,
            if(segExtintorValidade)1 else 0, if(segTriangulo)1 else 0, if(segMacacoChaveRoda)1 else 0, if(segConesFaixa)1 else 0, if(segEpiCompleto)1 else 0,
            if(carrLonasCordas)1 else 0, if(carrPortasBau)1 else 0, if(carrAssoalhoEstado)1 else 0, if(carrTravasLacres)1 else 0,
            if(cabBancosCintos)1 else 0, if(cabEspelhosRetrovisores)1 else 0, if(cabLimpadorParabrisa)1 else 0, if(cabArCondicionado)1 else 0, if(cabFreioEstacionamento)1 else 0,
            observacoes.ifEmpty { null }))
        if (r.status == "ok") { repository.getChecklistsPreParaSincronizar().lastOrNull()?.let { repository.marcarChecklistPreSincronizado(it.id) } }
        } catch (_: Exception) {}
        sucessoMsg = "Checklist pré-viagem salvo!"
        } catch (e: Exception) { erroMsg = "Erro: ${mensagemErroAmigavel(e.message)}" }; salvando = false } }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }

    Scaffold(topBar = { GradientTopBar(title = "Checklist Pré-Viagem", onBackClick = onVoltar) }) { padding ->
        if (carregando) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
        else if (semViagem) { SemViagemCard(onVoltar, padding) }
        else { Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ProgressCard(itensOk, 35)
            Spacer(Modifier.height(8.dp))
            if (jaEnviado) {
                BadgeJaEnviado(dataEnvio, AppColors.Secondary)
            } else run {
                val todosOk = itensOk == 35
                OutlinedButton(
                    onClick = { marcarTodos(!todosOk) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (todosOk) AppColors.Error else AppColors.Secondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (todosOk) AppColors.Error.copy(alpha = 0.5f) else AppColors.Secondary.copy(alpha = 0.5f))
                ) {
                    Icon(if (todosOk) Icons.Default.RemoveDone else Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (todosOk) "Desmarcar Todos" else "Marcar Todos", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            SecaoChecklist("Documentação", Icons.Default.Description) {
                CheckItem("CNH válida", docCnhValida, readOnly = jaEnviado) { docCnhValida = it }; CheckItem("CRLV do veículo", docCrlvVeiculo, readOnly = jaEnviado) { docCrlvVeiculo = it }
                CheckItem("ANTT válida", docAnttValida, readOnly = jaEnviado) { docAnttValida = it }; CheckItem("Seguro de carga", docSeguroCarga, readOnly = jaEnviado) { docSeguroCarga = it }
                CheckItem("Ordem de coleta", docOrdemColeta, readOnly = jaEnviado) { docOrdemColeta = it } }
            SecaoChecklist("Parte Elétrica", Icons.Default.FlashOn) {
                CheckItem("Farol dianteiro", eletFarolDianteiro, readOnly = jaEnviado) { eletFarolDianteiro = it }; CheckItem("Farol traseiro", eletFarolTraseiro, readOnly = jaEnviado) { eletFarolTraseiro = it }
                CheckItem("Luz de freio", eletLuzFreio, readOnly = jaEnviado) { eletLuzFreio = it }; CheckItem("Seta direita", eletSetaDireita, readOnly = jaEnviado) { eletSetaDireita = it }
                CheckItem("Seta esquerda", eletSetaEsquerda, readOnly = jaEnviado) { eletSetaEsquerda = it }; CheckItem("Luz de ré", eletLuzRe, readOnly = jaEnviado) { eletLuzRe = it }
                CheckItem("Painel funcionando", eletPainelFuncionando, readOnly = jaEnviado) { eletPainelFuncionando = it } }
            SecaoChecklist("Pneus e Rodas", Icons.Default.TireRepair) {
                CheckItem("Calibragem OK", pneuCalibragemOk, readOnly = jaEnviado) { pneuCalibragemOk = it }; CheckItem("Estado conservação", pneuEstadoConservacao, readOnly = jaEnviado) { pneuEstadoConservacao = it }
                CheckItem("Estepe OK", pneuEstepeOk, readOnly = jaEnviado) { pneuEstepeOk = it }; CheckItem("Ferramentas troca", pneuFerramentasTroca, readOnly = jaEnviado) { pneuFerramentasTroca = it } }
            SecaoChecklist("Fluidos e Níveis", Icons.Default.WaterDrop) {
                CheckItem("Óleo motor", fluidoOleoMotor, readOnly = jaEnviado) { fluidoOleoMotor = it }; CheckItem("Água radiador", fluidoAguaRadiador, readOnly = jaEnviado) { fluidoAguaRadiador = it }
                CheckItem("Fluido freio", fluidoFluidoFreio, readOnly = jaEnviado) { fluidoFluidoFreio = it }; CheckItem("ARLA 32", fluidoArla32, readOnly = jaEnviado) { fluidoArla32 = it }
                CheckItem("Combustível", fluidoCombustivel, readOnly = jaEnviado) { fluidoCombustivel = it } }
            SecaoChecklist("Segurança", Icons.Default.Shield) {
                CheckItem("Extintor (validade)", segExtintorValidade, readOnly = jaEnviado) { segExtintorValidade = it }; CheckItem("Triângulo", segTriangulo, readOnly = jaEnviado) { segTriangulo = it }
                CheckItem("Macaco/chave roda", segMacacoChaveRoda, readOnly = jaEnviado) { segMacacoChaveRoda = it }; CheckItem("Cones/faixa", segConesFaixa, readOnly = jaEnviado) { segConesFaixa = it }
                CheckItem("EPI completo", segEpiCompleto, readOnly = jaEnviado) { segEpiCompleto = it } }
            SecaoChecklist("Carroceria / Baú", Icons.Default.Inventory) {
                CheckItem("Lonas e cordas", carrLonasCordas, readOnly = jaEnviado) { carrLonasCordas = it }; CheckItem("Portas baú", carrPortasBau, readOnly = jaEnviado) { carrPortasBau = it }
                CheckItem("Assoalho", carrAssoalhoEstado, readOnly = jaEnviado) { carrAssoalhoEstado = it }; CheckItem("Travas/lacres", carrTravasLacres, readOnly = jaEnviado) { carrTravasLacres = it } }
            SecaoChecklist("Cabine", Icons.Default.AirlineSeatReclineNormal) {
                CheckItem("Bancos/cintos", cabBancosCintos, readOnly = jaEnviado) { cabBancosCintos = it }; CheckItem("Retrovisores", cabEspelhosRetrovisores, readOnly = jaEnviado) { cabEspelhosRetrovisores = it }
                CheckItem("Limpador parabrisa", cabLimpadorParabrisa, readOnly = jaEnviado) { cabLimpadorParabrisa = it }; CheckItem("Ar condicionado", cabArCondicionado, readOnly = jaEnviado) { cabArCondicionado = it }
                CheckItem("Freio estacionamento", cabFreioEstacionamento, readOnly = jaEnviado) { cabFreioEstacionamento = it } }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(observacoes, { observacoes = it }, readOnly = jaEnviado, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(20.dp))
            if (!jaEnviado) BotaoSalvar("Salvar Checklist Pré-Viagem", salvando) { salvar() }
            Spacer(Modifier.height(32.dp))
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ChecklistPosViagemScreen(repository: AppRepository, onVoltar: () -> Unit, onSucesso: () -> Unit) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }
    var semViagem by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }
    // Checklist já enviado nessa viagem — trava a tela em somente-leitura.
    var jaEnviado by remember { mutableStateOf(false) }
    var dataEnvio by remember { mutableStateOf("") }

    var avariaCarroceria by remember { mutableStateOf(false) }; var avariaCabine by remember { mutableStateOf(false) }
    var avariaPneus by remember { mutableStateOf(false) }; var avariaEspelhos by remember { mutableStateOf(false) }
    var avariaFarois by remember { mutableStateOf(false) }; var avariaDescricao by remember { mutableStateOf("") }
    var posNivelOleo by remember { mutableStateOf(false) }; var posNivelAgua by remember { mutableStateOf(false) }
    var posNivelCombustivel by remember { mutableStateOf(false) }; var posNivelArla by remember { mutableStateOf(false) }
    var limpCabineLimpa by remember { mutableStateOf(false) }; var limpCarroceriaLimpa by remember { mutableStateOf(false) }
    var limpBauVazio by remember { mutableStateOf(false) }
    var funcFreiosOk by remember { mutableStateOf(false) }; var funcDirecaoOk by remember { mutableStateOf(false) }
    var funcSuspensaoOk by remember { mutableStateOf(false) }; var funcMotorRuido by remember { mutableStateOf(false) }
    var funcCambioOk by remember { mutableStateOf(false) }
    var pendManutencaoUrgente by remember { mutableStateOf(false) }; var pendDescricaoManutencao by remember { mutableStateOf("") }
    var pendAbastecimentoNecessario by remember { mutableStateOf(false) }; var pendTrocaOleoProxima by remember { mutableStateOf(false) }
    var pendKmAtual by remember { mutableStateOf("") }; var observacoes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val v = repository.getViagemAtual()
            viagemAtual = v
            if (v == null) {
                semViagem = true
            } else {
                val existente = repository.getChecklistPosPorViagem(v.viagem_id)
                if (existente != null) {
                    jaEnviado = true
                    dataEnvio = existente.data_checklist
                    avariaCarroceria = existente.avaria_carroceria == 1L; avariaCabine = existente.avaria_cabine == 1L
                    avariaPneus = existente.avaria_pneus == 1L; avariaEspelhos = existente.avaria_espelhos == 1L
                    avariaFarois = existente.avaria_farois == 1L; avariaDescricao = existente.avaria_descricao ?: ""
                    posNivelOleo = existente.pos_nivel_oleo == 1L; posNivelAgua = existente.pos_nivel_agua == 1L
                    posNivelCombustivel = existente.pos_nivel_combustivel == 1L; posNivelArla = existente.pos_nivel_arla == 1L
                    limpCabineLimpa = existente.limp_cabine_limpa == 1L; limpCarroceriaLimpa = existente.limp_carroceria_limpa == 1L
                    limpBauVazio = existente.limp_bau_vazio == 1L
                    funcFreiosOk = existente.func_freios_ok == 1L; funcDirecaoOk = existente.func_direcao_ok == 1L
                    funcSuspensaoOk = existente.func_suspensao_ok == 1L; funcMotorRuido = existente.func_motor_ruido == 1L
                    funcCambioOk = existente.func_cambio_ok == 1L
                    pendManutencaoUrgente = existente.pend_manutencao_urgente == 1L
                    pendDescricaoManutencao = existente.pend_descricao_manutencao ?: ""
                    pendAbastecimentoNecessario = existente.pend_abastecimento_necessario == 1L
                    pendTrocaOleoProxima = existente.pend_troca_oleo_proxima == 1L
                    pendKmAtual = existente.pend_km_atual?.let { formatarKmExibicao(it.toDoubleOrNull() ?: 0.0) } ?: ""
                    observacoes = existente.observacoes ?: ""
                }
            }
        } catch (_: Exception) {
            semViagem = true
        }
        carregando = false
    }

    // Botão "Marcar/Desmarcar Itens OK" — Android já tinha, iOS não. Só marca
    // os itens "positivos" (Níveis+Limpeza+Funcionamento); avarias e
    // pendências são flags de problema, não entram no marcar-tudo.
    val itensPositivosOk = listOf(
        posNivelOleo, posNivelAgua, posNivelCombustivel, posNivelArla,
        limpCabineLimpa, limpCarroceriaLimpa, limpBauVazio,
        funcFreiosOk, funcDirecaoOk, funcSuspensaoOk, funcMotorRuido, funcCambioOk
    ).count { it }
    fun marcarItensOk(valor: Boolean) {
        posNivelOleo = valor; posNivelAgua = valor; posNivelCombustivel = valor; posNivelArla = valor
        limpCabineLimpa = valor; limpCarroceriaLimpa = valor; limpBauVazio = valor
        funcFreiosOk = valor; funcDirecaoOk = valor; funcSuspensaoOk = valor; funcMotorRuido = valor; funcCambioOk = valor
    }

    fun salvar() { if (viagemAtual == null || jaEnviado) return; val d = converterDataParaAPI(dataAtualFormatada()); val pendKmAtualNormalizado = if (pendKmAtual.isNotBlank()) normalizarKmParaEnvio(pendKmAtual) else null; scope.launch {
        salvando = true; try { repository.salvarChecklistPos(motorista?.motorista_id ?: "", viagemAtual!!.viagem_id, d, "",
            avariaCarroceria, avariaCabine, avariaPneus, avariaEspelhos, avariaFarois, avariaDescricao.ifEmpty { null },
            posNivelOleo, posNivelAgua, posNivelCombustivel, posNivelArla,
            limpCabineLimpa, limpCarroceriaLimpa, limpBauVazio,
            funcFreiosOk, funcDirecaoOk, funcSuspensaoOk, funcMotorRuido, funcCambioOk,
            pendManutencaoUrgente, pendDescricaoManutencao.ifEmpty { null }, pendAbastecimentoNecessario, pendTrocaOleoProxima, pendKmAtualNormalizado,
            observacoes.ifEmpty { null })
        try { val r = api.ApiClient.salvarChecklistPos(api.SalvarChecklistPosRequest(motorista?.motorista_id ?: "", viagemAtual!!.viagem_id.toInt(), d, "",
            if(avariaCarroceria)1 else 0, if(avariaCabine)1 else 0, if(avariaPneus)1 else 0, if(avariaEspelhos)1 else 0, if(avariaFarois)1 else 0, avariaDescricao.ifEmpty{null},
            if(posNivelOleo)1 else 0, if(posNivelAgua)1 else 0, if(posNivelCombustivel)1 else 0, if(posNivelArla)1 else 0,
            if(limpCabineLimpa)1 else 0, if(limpCarroceriaLimpa)1 else 0, if(limpBauVazio)1 else 0,
            if(funcFreiosOk)1 else 0, if(funcDirecaoOk)1 else 0, if(funcSuspensaoOk)1 else 0, if(funcMotorRuido)1 else 0, if(funcCambioOk)1 else 0,
            if(pendManutencaoUrgente)1 else 0, pendDescricaoManutencao.ifEmpty{null}, if(pendAbastecimentoNecessario)1 else 0, if(pendTrocaOleoProxima)1 else 0, pendKmAtualNormalizado,
            observacoes.ifEmpty{null}))
        if (r.status == "ok") { repository.getChecklistsPosParaSincronizar().lastOrNull()?.let { repository.marcarChecklistPosSincronizado(it.id) } }
        } catch (_: Exception) {}
        sucessoMsg = "Checklist pós-viagem salvo!"
        } catch (e: Exception) { erroMsg = "Erro: ${mensagemErroAmigavel(e.message)}" }; salvando = false } }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }

    Scaffold(topBar = { GradientTopBar(title = "Checklist Pós-Viagem", onBackClick = onVoltar) }) { padding ->
        if (carregando) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
        else if (semViagem) { SemViagemCard(onVoltar, padding) }
        else { Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (jaEnviado) {
                BadgeJaEnviado(dataEnvio, Color(0xFF10B981))
            } else run {
                val todosPositivosOk = itensPositivosOk == 12
                OutlinedButton(
                    onClick = { marcarItensOk(!todosPositivosOk) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (todosPositivosOk) AppColors.Error else Color(0xFF10B981)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (todosPositivosOk) AppColors.Error.copy(alpha = 0.5f) else Color(0xFF10B981).copy(alpha = 0.5f))
                ) {
                    Icon(if (todosPositivosOk) Icons.Default.RemoveDone else Icons.Default.DoneAll, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (todosPositivosOk) "Desmarcar Itens OK" else "Marcar Itens OK", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
                Text("Avarias e pendências devem ser marcadas individualmente", fontSize = 10.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            SecaoChecklist("Avarias e Danos", Icons.Default.ReportProblem) {
                CheckItem("Carroceria", avariaCarroceria, readOnly = jaEnviado) { avariaCarroceria = it }; CheckItem("Cabine", avariaCabine, readOnly = jaEnviado) { avariaCabine = it }
                CheckItem("Pneus", avariaPneus, readOnly = jaEnviado) { avariaPneus = it }; CheckItem("Espelhos", avariaEspelhos, readOnly = jaEnviado) { avariaEspelhos = it }
                CheckItem("Faróis", avariaFarois, readOnly = jaEnviado) { avariaFarois = it }
                if (avariaCarroceria||avariaCabine||avariaPneus||avariaEspelhos||avariaFarois) OutlinedTextField(avariaDescricao, { avariaDescricao = it }, readOnly = jaEnviado, label = { Text("Descreva avarias") }, modifier = Modifier.fillMaxWidth().padding(top=8.dp), minLines=2, colors = ui.darkTextFieldColors(), shape=RoundedCornerShape(8.dp)) }
            SecaoChecklist("Níveis e Fluidos", Icons.Default.WaterDrop) {
                CheckItem("Óleo OK", posNivelOleo, readOnly = jaEnviado) { posNivelOleo = it }; CheckItem("Água OK", posNivelAgua, readOnly = jaEnviado) { posNivelAgua = it }
                CheckItem("Combustível OK", posNivelCombustivel, readOnly = jaEnviado) { posNivelCombustivel = it }; CheckItem("ARLA OK", posNivelArla, readOnly = jaEnviado) { posNivelArla = it } }
            SecaoChecklist("Limpeza", Icons.Default.CleaningServices) {
                CheckItem("Cabine limpa", limpCabineLimpa, readOnly = jaEnviado) { limpCabineLimpa = it }; CheckItem("Carroceria limpa", limpCarroceriaLimpa, readOnly = jaEnviado) { limpCarroceriaLimpa = it }
                CheckItem("Baú vazio", limpBauVazio, readOnly = jaEnviado) { limpBauVazio = it } }
            SecaoChecklist("Funcionamento", Icons.Default.Settings) {
                CheckItem("Freios OK", funcFreiosOk, readOnly = jaEnviado) { funcFreiosOk = it }; CheckItem("Direção OK", funcDirecaoOk, readOnly = jaEnviado) { funcDirecaoOk = it }
                CheckItem("Suspensão OK", funcSuspensaoOk, readOnly = jaEnviado) { funcSuspensaoOk = it }; CheckItem("Motor sem ruído", funcMotorRuido, readOnly = jaEnviado) { funcMotorRuido = it }
                CheckItem("Câmbio OK", funcCambioOk, readOnly = jaEnviado) { funcCambioOk = it } }
            SecaoChecklist("Pendências", Icons.Default.PendingActions) {
                CheckItem("Manutenção urgente", pendManutencaoUrgente, readOnly = jaEnviado) { pendManutencaoUrgente = it }
                if (pendManutencaoUrgente) OutlinedTextField(pendDescricaoManutencao, { pendDescricaoManutencao = it }, readOnly = jaEnviado, label={Text("Descreva")}, modifier=Modifier.fillMaxWidth().padding(top=8.dp), minLines=2, colors = ui.darkTextFieldColors(), shape=RoundedCornerShape(8.dp))
                CheckItem("Abastecimento necessário", pendAbastecimentoNecessario, readOnly = jaEnviado) { pendAbastecimentoNecessario = it }
                CheckItem("Troca óleo próxima", pendTrocaOleoProxima, readOnly = jaEnviado) { pendTrocaOleoProxima = it }
                OutlinedTextField(pendKmAtual, { pendKmAtual = formatarKmInput(it) }, readOnly = jaEnviado, label={Text("KM atual")}, placeholder={Text("Ex.: 350000.5")}, modifier=Modifier.fillMaxWidth().padding(top=8.dp), shape=RoundedCornerShape(8.dp))
                Text("Digite como aparece no painel. Ex.: 350000.5", fontSize = 12.sp, color = AppColors.Primary, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(observacoes, { observacoes = it }, readOnly = jaEnviado, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(20.dp))
            if (!jaEnviado) BotaoSalvar("Salvar Checklist Pós-Viagem", salvando) { salvar() }
            Spacer(Modifier.height(32.dp))
        } }
    }
}

// === COMPONENTES ===
@Composable private fun SecaoChecklist(titulo: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical=6.dp), colors=CardDefaults.cardColors(containerColor=AppColors.CardBackground), shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(icon, null, tint=AppColors.Primary, modifier=Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text(titulo, fontWeight=FontWeight.Bold, fontSize=16.sp) }
            Spacer(Modifier.height(12.dp)); content() } } }

@Composable private fun CheckItem(label: String, checked: Boolean, readOnly: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().let { if (readOnly) it else it.clickable{onChange(!checked)} }.padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if(checked) AppColors.Success else Color(0xFFE0E0E0)).border(2.dp, if(checked) AppColors.Success else Color(0xFFBDBDBD), CircleShape), contentAlignment=Alignment.Center) {
            if (checked) Icon(Icons.Default.Check, null, tint=Color.White, modifier=Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp)); Text(label, fontSize=15.sp, color=if(checked) AppColors.TextPrimary else AppColors.TextSecondary) } }

private fun formatarDataChecklist(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else data
    } catch (e: Exception) {
        data
    }
}

@Composable private fun ProgressCard(ok: Int, total: Int) {
    Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=AppColors.CardBackground), shape=RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) { Text("$ok/$total itens verificados", fontWeight=FontWeight.Bold, fontSize=14.sp); Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress={ok.toFloat()/total}, modifier=Modifier.fillMaxWidth().height(8.dp), color=if(ok==total) AppColors.Success else AppColors.Primary, trackColor=Color(0xFFE0E0E0)) } } }

@Composable private fun SemViagemCard(onVoltar: () -> Unit, padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally, modifier=Modifier.padding(32.dp)) {
            Icon(Icons.Default.Warning, null, tint=AppColors.Orange, modifier=Modifier.size(64.dp)); Spacer(Modifier.height(16.dp))
            Text("Nenhuma viagem em andamento", fontWeight=FontWeight.Bold, fontSize=18.sp); Spacer(Modifier.height(8.dp))
            Text("Inicie uma viagem antes.", color=AppColors.TextSecondary, textAlign=TextAlign.Center); Spacer(Modifier.height(24.dp))
            Button(onClick=onVoltar) { Text("Voltar") } } } }

@Composable private fun BotaoSalvar(text: String, salvando: Boolean, onClick: () -> Unit) {
    Button(onClick=onClick, enabled=!salvando, modifier=Modifier.fillMaxWidth().height(56.dp), shape=RoundedCornerShape(12.dp), colors=ButtonDefaults.buttonColors(containerColor=AppColors.Primary)) {
        if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color=Color.White, strokeWidth=2.dp) else Text(text, fontWeight=FontWeight.Bold, fontSize=16.sp) } }

@Composable private fun BadgeJaEnviado(dataEnvio: String, cor: Color) {
    Row(
        Modifier.fillMaxWidth().background(cor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Lock, null, tint = cor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Checklist já enviado em ${formatarDataChecklist(dataEnvio)} — somente leitura", fontSize = 12.sp, color = cor, fontWeight = FontWeight.Medium)
    }
}
