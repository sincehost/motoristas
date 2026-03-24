package screens

import androidx.compose.animation.*
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
actual fun ChecklistPosViagemScreen(
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

    // === AVARIAS E DANOS (marcado = TEM avaria) ===
    var avariaCarroceria by rememberSaveable { mutableStateOf(false) }
    var avariaCabine by rememberSaveable { mutableStateOf(false) }
    var avariaPneus by rememberSaveable { mutableStateOf(false) }
    var avariaEspelhos by rememberSaveable { mutableStateOf(false) }
    var avariaFarois by rememberSaveable { mutableStateOf(false) }
    var avariaDescricao by rememberSaveable { mutableStateOf("") }

    // === NÍVEIS E FLUIDOS ===
    var posNivelOleo by rememberSaveable { mutableStateOf(false) }
    var posNivelAgua by rememberSaveable { mutableStateOf(false) }
    var posNivelCombustivel by rememberSaveable { mutableStateOf(false) }
    var posNivelArla by rememberSaveable { mutableStateOf(false) }

    // === LIMPEZA ===
    var limpCabineLimpa by rememberSaveable { mutableStateOf(false) }
    var limpCarroceriaLimpa by rememberSaveable { mutableStateOf(false) }
    var limpBauVazio by rememberSaveable { mutableStateOf(false) }

    // === FUNCIONAMENTO ===
    var funcFreiosOk by rememberSaveable { mutableStateOf(false) }
    var funcDirecaoOk by rememberSaveable { mutableStateOf(false) }
    var funcSuspensaoOk by rememberSaveable { mutableStateOf(false) }
    var funcMotorRuido by rememberSaveable { mutableStateOf(false) }
    var funcCambioOk by rememberSaveable { mutableStateOf(false) }

    // === PENDÊNCIAS ===
    var pendManutencaoUrgente by rememberSaveable { mutableStateOf(false) }
    var pendDescricaoManutencao by rememberSaveable { mutableStateOf("") }
    var pendAbastecimentoNecessario by rememberSaveable { mutableStateOf(false) }
    var pendTrocaOleoProxima by rememberSaveable { mutableStateOf(false) }
    var pendKmAtual by rememberSaveable { mutableStateOf("") }

    var observacoes by rememberSaveable { mutableStateOf("") }

    // ★ Marcar / Desmarcar todos os itens positivos (Níveis + Limpeza + Funcionamento)
    // Avarias e Pendências NÃO são marcados automaticamente (são flags de problema)
    fun marcarItensOk(valor: Boolean) {
        posNivelOleo = valor; posNivelAgua = valor; posNivelCombustivel = valor; posNivelArla = valor
        limpCabineLimpa = valor; limpCarroceriaLimpa = valor; limpBauVazio = valor
        funcFreiosOk = valor; funcDirecaoOk = valor; funcSuspensaoOk = valor; funcMotorRuido = valor; funcCambioOk = valor
    }

    val totalItens = 21
    val itensMarcados = listOf(
        posNivelOleo, posNivelAgua, posNivelCombustivel, posNivelArla,
        limpCabineLimpa, limpCarroceriaLimpa, limpBauVazio,
        funcFreiosOk, funcDirecaoOk, funcSuspensaoOk, funcMotorRuido, funcCambioOk
    ).count { it }
    val totalAlertas = listOf(avariaCarroceria, avariaCabine, avariaPneus, avariaEspelhos, avariaFarois, pendManutencaoUrgente, pendAbastecimentoNecessario, pendTrocaOleoProxima).count { it }

    fun salvarChecklist() {
        if (viagemAtual == null) return
        val dataApi = converterDataParaAPI(dataAtualFormatada())

        scope.launch {
            salvando = true
            try {
                repository.salvarChecklistPos(
                    motoristaId = motorista?.motorista_id ?: "", viagemId = viagemAtual!!.viagem_id,
                    dataChecklist = dataApi, placa = "",
                    avariaCarroceria = avariaCarroceria, avariaCabine = avariaCabine, avariaPneus = avariaPneus,
                    avariaEspelhos = avariaEspelhos, avariaFarois = avariaFarois, avariaDescricao = avariaDescricao.ifEmpty { null },
                    posNivelOleo = posNivelOleo, posNivelAgua = posNivelAgua,
                    posNivelCombustivel = posNivelCombustivel, posNivelArla = posNivelArla,
                    limpCabineLimpa = limpCabineLimpa, limpCarroceriaLimpa = limpCarroceriaLimpa, limpBauVazio = limpBauVazio,
                    funcFreiosOk = funcFreiosOk, funcDirecaoOk = funcDirecaoOk, funcSuspensaoOk = funcSuspensaoOk,
                    funcMotorRuido = funcMotorRuido, funcCambioOk = funcCambioOk,
                    pendManutencaoUrgente = pendManutencaoUrgente, pendDescricaoManutencao = pendDescricaoManutencao.ifEmpty { null },
                    pendAbastecimentoNecessario = pendAbastecimentoNecessario, pendTrocaOleoProxima = pendTrocaOleoProxima,
                    pendKmAtual = pendKmAtual.ifEmpty { null },
                    observacoes = observacoes.ifEmpty { null }
                )

                try {
                    val resp = api.ApiClient.salvarChecklistPos(
                        api.SalvarChecklistPosRequest(
                            motorista_id = motorista?.motorista_id ?: "", viagem_id = viagemAtual!!.viagem_id.toInt(),
                            data_checklist = dataApi, placa = "",
                            avaria_carroceria = if (avariaCarroceria) 1 else 0, avaria_cabine = if (avariaCabine) 1 else 0,
                            avaria_pneus = if (avariaPneus) 1 else 0, avaria_espelhos = if (avariaEspelhos) 1 else 0,
                            avaria_farois = if (avariaFarois) 1 else 0, avaria_descricao = avariaDescricao.ifEmpty { null },
                            pos_nivel_oleo = if (posNivelOleo) 1 else 0, pos_nivel_agua = if (posNivelAgua) 1 else 0,
                            pos_nivel_combustivel = if (posNivelCombustivel) 1 else 0, pos_nivel_arla = if (posNivelArla) 1 else 0,
                            limp_cabine_limpa = if (limpCabineLimpa) 1 else 0, limp_carroceria_limpa = if (limpCarroceriaLimpa) 1 else 0,
                            limp_bau_vazio = if (limpBauVazio) 1 else 0,
                            func_freios_ok = if (funcFreiosOk) 1 else 0, func_direcao_ok = if (funcDirecaoOk) 1 else 0,
                            func_suspensao_ok = if (funcSuspensaoOk) 1 else 0, func_motor_ruido = if (funcMotorRuido) 1 else 0,
                            func_cambio_ok = if (funcCambioOk) 1 else 0,
                            pend_manutencao_urgente = if (pendManutencaoUrgente) 1 else 0, pend_descricao_manutencao = pendDescricaoManutencao.ifEmpty { null },
                            pend_abastecimento_necessario = if (pendAbastecimentoNecessario) 1 else 0,
                            pend_troca_oleo_proxima = if (pendTrocaOleoProxima) 1 else 0, pend_km_atual = pendKmAtual.ifEmpty { null },
                            observacoes = observacoes.ifEmpty { null }
                        )
                    )
                    if (resp.status == "ok") {
                        val checklists = repository.getChecklistsPosParaSincronizar()
                        checklists.lastOrNull()?.let { repository.marcarChecklistPosSincronizado(it.id) }
                    }
                } catch (_: Exception) { }

                mostrarMensagem("Checklist pós-viagem salvo com sucesso!")
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
        topBar = { GradientTopBar(title = "Checklist Pós-Viagem", onBackClick = onVoltar) }
    ) { padding ->
        if (carregando) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
        } else if (semViagemAberta) {
            Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Info, null, tint = AppColors.Orange, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhuma viagem em andamento", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Orange)
                        Spacer(Modifier.height(8.dp))
                        Text("Inicie uma viagem para preencher o checklist pós-viagem.", color = AppColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onVoltar, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary), shape = RoundedCornerShape(12.dp)) { Text("Voltar ao Dashboard") }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {
                // Header
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.08f)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FactCheck, null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Checklist Pós-Viagem", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF10B981))
                                Text(viagemAtual?.destino ?: "", fontSize = 13.sp, color = AppColors.TextSecondary)
                            }
                            if (totalAlertas > 0) {
                                Surface(color = AppColors.Error, shape = RoundedCornerShape(20.dp)) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("$totalAlertas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { itensMarcados.toFloat() / totalItens }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF10B981), trackColor = Color(0xFFE0E0E0))
                        Text("$itensMarcados de $totalItens itens verificados", fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(top = 4.dp))

                        Spacer(Modifier.height(12.dp))

                        // ★ Botão Marcar/Desmarcar Itens OK
                        val todosPositivosOk = itensMarcados == 12
                        OutlinedButton(
                            onClick = { marcarItensOk(!todosPositivosOk) },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (todosPositivosOk) AppColors.Error else Color(0xFF10B981)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (todosPositivosOk) AppColors.Error.copy(alpha = 0.5f) else Color(0xFF10B981).copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                if (todosPositivosOk) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (todosPositivosOk) "Desmarcar Itens OK" else "Marcar Itens OK",
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            "Avarias e pendências devem ser marcadas individualmente",
                            fontSize = 10.sp,
                            color = AppColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Avarias
                ChecklistSecao(titulo = "Avarias e Danos", icone = Icons.Default.ReportProblem, cor = Color(0xFFD32F2F), expandida = secaoExpandida == 0, onClick = { secaoExpandida = if (secaoExpandida == 0) -1 else 0 }, marcados = listOf(avariaCarroceria, avariaCabine, avariaPneus, avariaEspelhos, avariaFarois).count { it }, total = 5) {
                    Text("Marque os itens que apresentam avaria:", fontSize = 12.sp, color = AppColors.Error, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    ChecklistItem("Avaria na carroceria", avariaCarroceria) { avariaCarroceria = it }
                    ChecklistItem("Avaria na cabine", avariaCabine) { avariaCabine = it }
                    ChecklistItem("Avaria nos pneus", avariaPneus) { avariaPneus = it }
                    ChecklistItem("Avaria nos espelhos", avariaEspelhos) { avariaEspelhos = it }
                    ChecklistItem("Avaria nos faróis", avariaFarois) { avariaFarois = it }
                    if (listOf(avariaCarroceria, avariaCabine, avariaPneus, avariaEspelhos, avariaFarois).any { it }) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = avariaDescricao, onValueChange = { avariaDescricao = it }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), label = { Text("Descreva as avarias") }, maxLines = 3, colors = ui.darkTextFieldColors())
                    }
                }
                Spacer(Modifier.height(8.dp))

                ChecklistSecao(titulo = "Níveis e Fluidos", icone = Icons.Default.WaterDrop, cor = Color(0xFF0097A7), expandida = secaoExpandida == 1, onClick = { secaoExpandida = if (secaoExpandida == 1) -1 else 1 }, marcados = listOf(posNivelOleo, posNivelAgua, posNivelCombustivel, posNivelArla).count { it }, total = 4) {
                    ChecklistItem("Nível de óleo OK", posNivelOleo) { posNivelOleo = it }
                    ChecklistItem("Nível de água OK", posNivelAgua) { posNivelAgua = it }
                    ChecklistItem("Nível de combustível", posNivelCombustivel) { posNivelCombustivel = it }
                    ChecklistItem("Nível de ARLA 32", posNivelArla) { posNivelArla = it }
                }
                Spacer(Modifier.height(8.dp))

                ChecklistSecao(titulo = "Limpeza", icone = Icons.Default.CleaningServices, cor = Color(0xFF1565C0), expandida = secaoExpandida == 2, onClick = { secaoExpandida = if (secaoExpandida == 2) -1 else 2 }, marcados = listOf(limpCabineLimpa, limpCarroceriaLimpa, limpBauVazio).count { it }, total = 3) {
                    ChecklistItem("Cabine limpa e organizada", limpCabineLimpa) { limpCabineLimpa = it }
                    ChecklistItem("Carroceria limpa", limpCarroceriaLimpa) { limpCarroceriaLimpa = it }
                    ChecklistItem("Baú vazio e limpo", limpBauVazio) { limpBauVazio = it }
                }
                Spacer(Modifier.height(8.dp))

                ChecklistSecao(titulo = "Funcionamento", icone = Icons.Default.Settings, cor = Color(0xFF2E7D32), expandida = secaoExpandida == 3, onClick = { secaoExpandida = if (secaoExpandida == 3) -1 else 3 }, marcados = listOf(funcFreiosOk, funcDirecaoOk, funcSuspensaoOk, funcMotorRuido, funcCambioOk).count { it }, total = 5) {
                    ChecklistItem("Freios funcionando OK", funcFreiosOk) { funcFreiosOk = it }
                    ChecklistItem("Direção OK", funcDirecaoOk) { funcDirecaoOk = it }
                    ChecklistItem("Suspensão OK", funcSuspensaoOk) { funcSuspensaoOk = it }
                    ChecklistItem("Motor sem ruídos anormais", funcMotorRuido) { funcMotorRuido = it }
                    ChecklistItem("Câmbio funcionando OK", funcCambioOk) { funcCambioOk = it }
                }
                Spacer(Modifier.height(8.dp))

                ChecklistSecao(titulo = "Pendências", icone = Icons.Default.PendingActions, cor = Color(0xFFF57F17), expandida = secaoExpandida == 4, onClick = { secaoExpandida = if (secaoExpandida == 4) -1 else 4 }, marcados = listOf(pendManutencaoUrgente, pendAbastecimentoNecessario, pendTrocaOleoProxima).count { it }, total = 3) {
                    Text("Marque as pendências identificadas:", fontSize = 12.sp, color = Color(0xFFF57F17), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    ChecklistItem("Manutenção urgente necessária", pendManutencaoUrgente) { pendManutencaoUrgente = it }
                    if (pendManutencaoUrgente) {
                        OutlinedTextField(value = pendDescricaoManutencao, onValueChange = { pendDescricaoManutencao = it }, modifier = Modifier.fillMaxWidth().padding(start = 40.dp), shape = RoundedCornerShape(12.dp), label = { Text("Descreva a manutenção necessária") }, maxLines = 2, colors = ui.darkTextFieldColors())
                        Spacer(Modifier.height(4.dp))
                    }
                    ChecklistItem("Abastecimento necessário", pendAbastecimentoNecessario) { pendAbastecimentoNecessario = it }
                    ChecklistItem("Troca de óleo próxima", pendTrocaOleoProxima) { pendTrocaOleoProxima = it }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = pendKmAtual, onValueChange = { pendKmAtual = it.filter { c -> c.isDigit() } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), label = { Text("KM atual do veículo") }, leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFFF57F17)) }, placeholder = { Text("Ex: 350000") }, colors = ui.darkTextFieldColors())
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
                Button(onClick = { salvarChecklist() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))) {
                    if (salvando) { CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) }
                    else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("SALVAR CHECKLIST PÓS-VIAGEM", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
