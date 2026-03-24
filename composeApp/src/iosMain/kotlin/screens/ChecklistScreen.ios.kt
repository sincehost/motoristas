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

    LaunchedEffect(Unit) { viagemAtual = repository.getViagemAtual(); semViagem = viagemAtual == null; carregando = false }

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

    val itensOk = listOf(docCnhValida, docCrlvVeiculo, docAnttValida, docSeguroCarga, docOrdemColeta,
        eletFarolDianteiro, eletFarolTraseiro, eletLuzFreio, eletSetaDireita, eletSetaEsquerda, eletLuzRe, eletPainelFuncionando,
        pneuCalibragemOk, pneuEstadoConservacao, pneuEstepeOk, pneuFerramentasTroca,
        fluidoOleoMotor, fluidoAguaRadiador, fluidoFluidoFreio, fluidoArla32, fluidoCombustivel,
        segExtintorValidade, segTriangulo, segMacacoChaveRoda, segConesFaixa, segEpiCompleto,
        carrLonasCordas, carrPortasBau, carrAssoalhoEstado, carrTravasLacres,
        cabBancosCintos, cabEspelhosRetrovisores, cabLimpadorParabrisa, cabArCondicionado).count { it }

    fun salvar() { if (viagemAtual == null) return; val d = converterDataParaAPI(dataAtualFormatada()); scope.launch {
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
        } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }; salvando = false } }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }

    Scaffold(topBar = { GradientTopBar(title = "Checklist Pré-Viagem", onBackClick = onVoltar) }) { padding ->
        if (carregando) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
        else if (semViagem) { SemViagemCard(onVoltar, padding) }
        else { Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            ProgressCard(itensOk, 34)
            Spacer(Modifier.height(12.dp))
            SecaoChecklist("Documentação", Icons.Default.Description) {
                CheckItem("CNH válida", docCnhValida) { docCnhValida = it }; CheckItem("CRLV do veículo", docCrlvVeiculo) { docCrlvVeiculo = it }
                CheckItem("ANTT válida", docAnttValida) { docAnttValida = it }; CheckItem("Seguro de carga", docSeguroCarga) { docSeguroCarga = it }
                CheckItem("Ordem de coleta", docOrdemColeta) { docOrdemColeta = it } }
            SecaoChecklist("Parte Elétrica", Icons.Default.FlashOn) {
                CheckItem("Farol dianteiro", eletFarolDianteiro) { eletFarolDianteiro = it }; CheckItem("Farol traseiro", eletFarolTraseiro) { eletFarolTraseiro = it }
                CheckItem("Luz de freio", eletLuzFreio) { eletLuzFreio = it }; CheckItem("Seta direita", eletSetaDireita) { eletSetaDireita = it }
                CheckItem("Seta esquerda", eletSetaEsquerda) { eletSetaEsquerda = it }; CheckItem("Luz de ré", eletLuzRe) { eletLuzRe = it }
                CheckItem("Painel funcionando", eletPainelFuncionando) { eletPainelFuncionando = it } }
            SecaoChecklist("Pneus e Rodas", Icons.Default.TireRepair) {
                CheckItem("Calibragem OK", pneuCalibragemOk) { pneuCalibragemOk = it }; CheckItem("Estado conservação", pneuEstadoConservacao) { pneuEstadoConservacao = it }
                CheckItem("Estepe OK", pneuEstepeOk) { pneuEstepeOk = it }; CheckItem("Ferramentas troca", pneuFerramentasTroca) { pneuFerramentasTroca = it } }
            SecaoChecklist("Fluidos e Níveis", Icons.Default.WaterDrop) {
                CheckItem("Óleo motor", fluidoOleoMotor) { fluidoOleoMotor = it }; CheckItem("Água radiador", fluidoAguaRadiador) { fluidoAguaRadiador = it }
                CheckItem("Fluido freio", fluidoFluidoFreio) { fluidoFluidoFreio = it }; CheckItem("ARLA 32", fluidoArla32) { fluidoArla32 = it }
                CheckItem("Combustível", fluidoCombustivel) { fluidoCombustivel = it } }
            SecaoChecklist("Segurança", Icons.Default.Shield) {
                CheckItem("Extintor (validade)", segExtintorValidade) { segExtintorValidade = it }; CheckItem("Triângulo", segTriangulo) { segTriangulo = it }
                CheckItem("Macaco/chave roda", segMacacoChaveRoda) { segMacacoChaveRoda = it }; CheckItem("Cones/faixa", segConesFaixa) { segConesFaixa = it }
                CheckItem("EPI completo", segEpiCompleto) { segEpiCompleto = it } }
            SecaoChecklist("Carroceria / Baú", Icons.Default.Inventory) {
                CheckItem("Lonas e cordas", carrLonasCordas) { carrLonasCordas = it }; CheckItem("Portas baú", carrPortasBau) { carrPortasBau = it }
                CheckItem("Assoalho", carrAssoalhoEstado) { carrAssoalhoEstado = it }; CheckItem("Travas/lacres", carrTravasLacres) { carrTravasLacres = it } }
            SecaoChecklist("Cabine", Icons.Default.AirlineSeatReclineNormal) {
                CheckItem("Bancos/cintos", cabBancosCintos) { cabBancosCintos = it }; CheckItem("Retrovisores", cabEspelhosRetrovisores) { cabEspelhosRetrovisores = it }
                CheckItem("Limpador parabrisa", cabLimpadorParabrisa) { cabLimpadorParabrisa = it }; CheckItem("Ar condicionado", cabArCondicionado) { cabArCondicionado = it }
                CheckItem("Freio estacionamento", cabFreioEstacionamento) { cabFreioEstacionamento = it } }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(20.dp))
            BotaoSalvar("Salvar Checklist Pré-Viagem", salvando) { salvar() }
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

    LaunchedEffect(Unit) { viagemAtual = repository.getViagemAtual(); semViagem = viagemAtual == null; carregando = false }

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

    fun salvar() { if (viagemAtual == null) return; val d = converterDataParaAPI(dataAtualFormatada()); scope.launch {
        salvando = true; try { repository.salvarChecklistPos(motorista?.motorista_id ?: "", viagemAtual!!.viagem_id, d, "",
            avariaCarroceria, avariaCabine, avariaPneus, avariaEspelhos, avariaFarois, avariaDescricao.ifEmpty { null },
            posNivelOleo, posNivelAgua, posNivelCombustivel, posNivelArla,
            limpCabineLimpa, limpCarroceriaLimpa, limpBauVazio,
            funcFreiosOk, funcDirecaoOk, funcSuspensaoOk, funcMotorRuido, funcCambioOk,
            pendManutencaoUrgente, pendDescricaoManutencao.ifEmpty { null }, pendAbastecimentoNecessario, pendTrocaOleoProxima, pendKmAtual.ifEmpty { null },
            observacoes.ifEmpty { null })
        try { val r = api.ApiClient.salvarChecklistPos(api.SalvarChecklistPosRequest(motorista?.motorista_id ?: "", viagemAtual!!.viagem_id.toInt(), d, "",
            if(avariaCarroceria)1 else 0, if(avariaCabine)1 else 0, if(avariaPneus)1 else 0, if(avariaEspelhos)1 else 0, if(avariaFarois)1 else 0, avariaDescricao.ifEmpty{null},
            if(posNivelOleo)1 else 0, if(posNivelAgua)1 else 0, if(posNivelCombustivel)1 else 0, if(posNivelArla)1 else 0,
            if(limpCabineLimpa)1 else 0, if(limpCarroceriaLimpa)1 else 0, if(limpBauVazio)1 else 0,
            if(funcFreiosOk)1 else 0, if(funcDirecaoOk)1 else 0, if(funcSuspensaoOk)1 else 0, if(funcMotorRuido)1 else 0, if(funcCambioOk)1 else 0,
            if(pendManutencaoUrgente)1 else 0, pendDescricaoManutencao.ifEmpty{null}, if(pendAbastecimentoNecessario)1 else 0, if(pendTrocaOleoProxima)1 else 0, pendKmAtual.ifEmpty{null},
            observacoes.ifEmpty{null}))
        if (r.status == "ok") { repository.getChecklistsPosParaSincronizar().lastOrNull()?.let { repository.marcarChecklistPosSincronizado(it.id) } }
        } catch (_: Exception) {}
        sucessoMsg = "Checklist pós-viagem salvo!"
        } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }; salvando = false } }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }

    Scaffold(topBar = { GradientTopBar(title = "Checklist Pós-Viagem", onBackClick = onVoltar) }) { padding ->
        if (carregando) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) } }
        else if (semViagem) { SemViagemCard(onVoltar, padding) }
        else { Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SecaoChecklist("Avarias e Danos", Icons.Default.ReportProblem) {
                CheckItem("Carroceria", avariaCarroceria) { avariaCarroceria = it }; CheckItem("Cabine", avariaCabine) { avariaCabine = it }
                CheckItem("Pneus", avariaPneus) { avariaPneus = it }; CheckItem("Espelhos", avariaEspelhos) { avariaEspelhos = it }
                CheckItem("Faróis", avariaFarois) { avariaFarois = it }
                if (avariaCarroceria||avariaCabine||avariaPneus||avariaEspelhos||avariaFarois) OutlinedTextField(avariaDescricao, { avariaDescricao = it }, label = { Text("Descreva avarias") }, modifier = Modifier.fillMaxWidth().padding(top=8.dp), minLines=2, colors = ui.darkTextFieldColors(), shape=RoundedCornerShape(8.dp)) }
            SecaoChecklist("Níveis e Fluidos", Icons.Default.WaterDrop) {
                CheckItem("Óleo OK", posNivelOleo) { posNivelOleo = it }; CheckItem("Água OK", posNivelAgua) { posNivelAgua = it }
                CheckItem("Combustível OK", posNivelCombustivel) { posNivelCombustivel = it }; CheckItem("ARLA OK", posNivelArla) { posNivelArla = it } }
            SecaoChecklist("Limpeza", Icons.Default.CleaningServices) {
                CheckItem("Cabine limpa", limpCabineLimpa) { limpCabineLimpa = it }; CheckItem("Carroceria limpa", limpCarroceriaLimpa) { limpCarroceriaLimpa = it }
                CheckItem("Baú vazio", limpBauVazio) { limpBauVazio = it } }
            SecaoChecklist("Funcionamento", Icons.Default.Settings) {
                CheckItem("Freios OK", funcFreiosOk) { funcFreiosOk = it }; CheckItem("Direção OK", funcDirecaoOk) { funcDirecaoOk = it }
                CheckItem("Suspensão OK", funcSuspensaoOk) { funcSuspensaoOk = it }; CheckItem("Motor sem ruído", funcMotorRuido) { funcMotorRuido = it }
                CheckItem("Câmbio OK", funcCambioOk) { funcCambioOk = it } }
            SecaoChecklist("Pendências", Icons.Default.PendingActions) {
                CheckItem("Manutenção urgente", pendManutencaoUrgente) { pendManutencaoUrgente = it }
                if (pendManutencaoUrgente) OutlinedTextField(pendDescricaoManutencao, { pendDescricaoManutencao = it }, label={Text("Descreva")}, modifier=Modifier.fillMaxWidth().padding(top=8.dp), minLines=2, colors = ui.darkTextFieldColors(), shape=RoundedCornerShape(8.dp))
                CheckItem("Abastecimento necessário", pendAbastecimentoNecessario) { pendAbastecimentoNecessario = it }
                CheckItem("Troca óleo próxima", pendTrocaOleoProxima) { pendTrocaOleoProxima = it }
                OutlinedTextField(pendKmAtual, { pendKmAtual = it.filter { c -> c.isDigit() } }, label={Text("KM atual")}, modifier=Modifier.fillMaxWidth().padding(top=8.dp), shape=RoundedCornerShape(8.dp)) }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(observacoes, { observacoes = it }, label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth(), minLines = 3, colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
            Spacer(Modifier.height(20.dp))
            BotaoSalvar("Salvar Checklist Pós-Viagem", salvando) { salvar() }
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

@Composable private fun CheckItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable{onChange(!checked)}.padding(vertical=6.dp), verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if(checked) AppColors.Success else Color(0xFFE0E0E0)).border(2.dp, if(checked) AppColors.Success else Color(0xFFBDBDBD), CircleShape), contentAlignment=Alignment.Center) {
            if (checked) Icon(Icons.Default.Check, null, tint=Color.White, modifier=Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp)); Text(label, fontSize=15.sp, color=if(checked) AppColors.TextPrimary else AppColors.TextSecondary) } }

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
