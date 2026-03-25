package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar

// Estados de navegação
private sealed class TelaViagem {
    object Lista : TelaViagem()
    data class Editar(val viagemId: Int) : TelaViagem()
    data class Resumo(val viagemId: Int) : TelaViagem()
    data class Despesas(val viagemId: Int) : TelaViagem()
}

@Composable
actual fun MinhasViagensScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    var telaAtual by remember { mutableStateOf<TelaViagem>(TelaViagem.Lista) }

    when (val tela = telaAtual) {
        is TelaViagem.Lista -> ListaViagensContent(
            repository = repository,
            onVoltar = onVoltar,
            onEditar = { telaAtual = TelaViagem.Editar(it) },
            onResumo = { telaAtual = TelaViagem.Resumo(it) },
            onDespesas = { telaAtual = TelaViagem.Despesas(it) }
        )
        is TelaViagem.Editar -> EditarViagemContent(
            repository = repository,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Lista }
        )
        is TelaViagem.Resumo -> ResumoViagemContent(
            repository = repository,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Lista }
        )
        is TelaViagem.Despesas -> DespesasViagemContent(
            repository = repository,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Lista }
        )
    }
}

// ==================== LISTA DE VIAGENS ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListaViagensContent(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onEditar: (Int) -> Unit,
    onResumo: (Int) -> Unit,
    onDespesas: (Int) -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    var viagens by remember { mutableStateOf<List<ViagemItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var paginaAtual by remember { mutableStateOf(1) }
    var totalPaginas by remember { mutableStateOf(1) }
    var totalViagens by remember { mutableStateOf(0) }

    var mostrarModalAcoes by remember { mutableStateOf(false) }
    var viagemSelecionada by remember { mutableStateOf<ViagemItem?>(null) }
    var mostrarConfirmacaoExcluir by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    fun carregarViagens(pagina: Int) {
        scope.launch {
            loading = true
            erro = null
            try {
                val response = ApiClient.listarViagens(
                    ListarViagensRequest(

                        motorista_id = motorista?.motorista_id ?: "",
                        page = pagina
                    )
                )
                if (response.status == "ok") {
                    // ORDENAÇÃO: Em andamento primeiro, depois por data mais recente
                    viagens = response.viagens.sortedWith(
                        compareBy<ViagemItem> { it.finalizada }
                            .thenByDescending { it.data_viagem }
                    )
                    paginaAtual = response.page
                    totalPaginas = response.total_pages
                    totalViagens = response.total
                } else {
                    erro = response.mensagem ?: "Erro ao carregar viagens"
                }
            } catch (e: Exception) {
                erro = "Erro: ${e.message}"
            }
            loading = false
        }
    }

    fun excluirViagem(viagem: ViagemItem) {
        scope.launch {
            excluindo = true
            try {
                val response = ApiClient.excluirViagem(
                    ExcluirViagemRequest(viagem_id = viagem.id)                )
                if (response.status == "ok") {
                    repository.excluirViagemLocal(viagem.id.toLong())
                    val viagemAtual = repository.getViagemAtual()
                    if (viagemAtual?.viagem_id == viagem.id.toLong()) {
                        repository.limparViagemAtual()
                        // Notificar Dashboard para remover card imediatamente
                        screens.AppEvents.emitir(screens.AppEvent.ViagemFinalizada)
                    }
                    mostrarMensagem("Viagem excluída com sucesso!")
                    mostrarConfirmacaoExcluir = false
                    viagemSelecionada = null
                    carregarViagens(paginaAtual)
                } else {
                    mostrarMensagem(response.mensagem ?: "Erro ao excluir", isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro: ${e.message}", isErro = true)
            }
            excluindo = false
        }
    }

    LaunchedEffect(Unit) {
        carregarViagens(1)
    }

    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(10000)
            if (!loading) {
                carregarViagens(paginaAtual)
            }
        }
    }

    if (mostrarModalAcoes && viagemSelecionada != null) {
        val viagemId = viagemSelecionada!!.id
        AlertDialog(
            onDismissRequest = { mostrarModalAcoes = false; viagemSelecionada = null },
            containerColor = AppColors.CardBackground,
            title = {
                Text("Ações da Viagem", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { mostrarModalAcoes = false; viagemSelecionada = null; onResumo(viagemId) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Resumo da Viagem")
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { mostrarModalAcoes = false; viagemSelecionada = null; onDespesas(viagemId) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Orange)
                    ) {
                        Icon(Icons.Default.Receipt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Visualizar Despesas")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { mostrarModalAcoes = false; viagemSelecionada = null },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Voltar")
                }
            }
        )
    }

    if (mostrarConfirmacaoExcluir && viagemSelecionada != null) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarConfirmacaoExcluir = false },
            icon = { Icon(Icons.Default.Warning, null, tint = AppColors.Error) },
            title = { Text("Excluir Viagem?", fontWeight = FontWeight.Bold) },
            text = { Text("Todos os dados relacionados serão excluídos. Esta ação não pode ser desfeita.", textAlign = TextAlign.Center) },
            confirmButton = {
                Button(onClick = { excluirViagem(viagemSelecionada!!) }, enabled = !excluindo, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)) {
                    if (excluindo) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Excluir")
                }
            },
            dismissButton = { OutlinedButton(onClick = { mostrarConfirmacaoExcluir = false }, enabled = !excluindo) { Text("Cancelar") } }
        )
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Minhas Viagens",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background)) {
            if (!loading && erro == null) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ListAlt, null, tint = AppColors.Primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Total: $totalViagens", fontWeight = FontWeight.Medium)
                        }
                        Text("Pág $paginaAtual/$totalPaginas", fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary)
                }
                erro != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { carregarViagens(paginaAtual) }) { Text("Tentar novamente") }
                    }
                }
                viagens.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nenhuma viagem encontrada", color = AppColors.TextSecondary)
                }
                else -> {
                    LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(viagens) { viagem ->
                            ViagemCardItem(
                                viagem = viagem,
                                onEditar = { onEditar(viagem.id) },
                                onExcluir = { viagemSelecionada = viagem; mostrarConfirmacaoExcluir = true },
                                onAcoes = { viagemSelecionada = viagem; mostrarModalAcoes = true }
                            )
                        }
                    }

                    if (totalPaginas > 1) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(onClick = { carregarViagens(paginaAtual - 1) }, enabled = paginaAtual > 1, modifier = Modifier.weight(1f)) {
                                Text("Anterior")
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(onClick = { carregarViagens(paginaAtual + 1) }, enabled = paginaAtual < totalPaginas, modifier = Modifier.weight(1f)) {
                                Text("Próximo")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinhaResumoDataChegada(dataChegada: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Data Chegada", color = AppColors.TextSecondary)

        if (dataChegada.isNotEmpty() && dataChegada != "0000-00-00") {
            Text(
                formatarData(dataChegada),
                fontWeight = FontWeight.Medium
            )
        } else {
            Surface(
                color = AppColors.Orange.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    "Em andamento",
                    color = AppColors.Orange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ViagemCardItem(viagem: ViagemItem, onEditar: () -> Unit, onExcluir: () -> Unit, onAcoes: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rota", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(viagem.destino_nome, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Surface(color = if (viagem.finalizada) if (ui.isDark()) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f) else AppColors.Orange.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                    Text(if (viagem.finalizada) "Finalizada" else "Em andamento", color = if (viagem.finalizada) AppColors.Secondary else AppColors.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = AppColors.Background)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Data Saída", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(formatarData(viagem.data_viagem), fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Data Chegada", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(if (viagem.finalizada && viagem.data_chegada != null) formatarData(viagem.data_chegada) else "Em andamento", fontWeight = FontWeight.Medium, color = if (viagem.finalizada) AppColors.TextPrimary else AppColors.Orange)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Placa", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(viagem.placa.ifEmpty { "-" }, fontWeight = FontWeight.Medium)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ordem", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Text(viagem.numerobd.ifEmpty { "-" }, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = AppColors.Background)
            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEditar, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontSize = 13.sp)
                }

                if (!viagem.finalizada) {
                    Button(onClick = onExcluir, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Excluir", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = onAcoes, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Purple)) {
                Icon(Icons.Default.MoreHoriz, null)
                Spacer(Modifier.width(8.dp))
                Text("Ações", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==================== EDITAR VIAGEM ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditarViagemContent(repository: AppRepository, viagemId: Int, onVoltar: () -> Unit) {
    val motorista = remember { repository.getMotoristaLogado() }
    var loading by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    var viagem by remember { mutableStateOf<ViagemDetalhe?>(null) }
    var destinos by remember { mutableStateOf<List<DestinoItem>>(emptyList()) }
    var equipamentos by remember { mutableStateOf<List<EquipamentoItem>>(emptyList()) }

    var numerobd by remember { mutableStateOf("") }
    var numerobd2 by remember { mutableStateOf("") }
    var cte by remember { mutableStateOf("") }
    var cte2 by remember { mutableStateOf("") }
    var destinoId by remember { mutableStateOf(0) }
    var placa by remember { mutableStateOf("") }
    var dataViagem by remember { mutableStateOf("") }
    var dataChegada by remember { mutableStateOf("") }
    var kmInicio by remember { mutableStateOf("") }
    var kmChegada by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf("") }
    var pesocarga by remember { mutableStateOf("") }
    var pesocargaretorno by remember { mutableStateOf("") }
    var valorfrete by remember { mutableStateOf("") }
    var valorfreteretorno by remember { mutableStateOf("") }
    var ordemRetorno by remember { mutableStateOf("") }
    var cteRetorno by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var erroMsgEditar by remember { mutableStateOf<String?>(null) }
    var sucessoMsgEditar by remember { mutableStateOf<String?>(null) }

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsgEditar = mensagem
        } else {
            sucessoMsgEditar = mensagem
        }
    }

    LaunchedEffect(viagemId) {
        try {
            val response = ApiClient.detalheViagem(ViagemDetalheRequest(viagem_id = viagemId))
            if (response.status == "ok" && response.viagem != null) {
                viagem = response.viagem
                destinos = response.destinos
                equipamentos = response.equipamentos

                numerobd = response.viagem.numerobd
                numerobd2 = response.viagem.numerobd2
                cte = response.viagem.cte
                cte2 = response.viagem.cte2
                destinoId = response.viagem.destino_id
                placa = response.viagem.placa
                dataViagem = response.viagem.data_viagem
                dataChegada = response.viagem.data_chegada
                kmInicio = response.viagem.km_inicio
                kmChegada = response.viagem.km_chegada
                kmPosto = response.viagem.km_posto
                pesocarga = response.viagem.pesocarga
                pesocargaretorno = response.viagem.pesocargaretorno
                valorfrete = response.viagem.valorfrete
                valorfreteretorno = response.viagem.valorfreteretorno
                ordemRetorno = response.viagem.ordem_retorno
                cteRetorno = response.viagem.cte_retorno
                descricao = response.viagem.descricao
            } else {
                erro = response.mensagem ?: "Erro ao carregar"
            }
        } catch (e: Exception) {
            erro = "Erro: ${e.message}"
        }
        loading = false
    }

    fun salvar() {
        scope.launch {
            salvando = true
            erro = null
            try {
                val response = ApiClient.atualizarViagem(AtualizarViagemRequest(

                    viagem_id = viagemId,
                    numerobd = numerobd, numerobd2 = numerobd2, cte = cte, cte2 = cte2,
                    destino_id = destinoId, placa = placa, data_viagem = dataViagem, data_chegada = dataChegada,
                    km_inicio = kmInicio, km_chegada = kmChegada, km_posto = kmPosto,
                    pesocarga = pesocarga, pesocargaretorno = pesocargaretorno, valorfrete = valorfrete, valorfreteretorno = valorfreteretorno,
                    ordem_retorno = ordemRetorno, cte_retorno = cteRetorno, descricao = descricao
                ))
                if (response.status == "ok") {
                    sucessoMsgEditar = "Viagem atualizada com sucesso!"
                } else {
                    mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro: ${e.message}", isErro = true)
            }
            salvando = false
        }
    }

    var destinoExpandido by remember { mutableStateOf(false) }
    var placaExpandida by remember { mutableStateOf(false) }

    // Diálogos modais de erro e sucesso
    if (erroMsgEditar != null) ui.ErroDialog(erroMsgEditar!!) { erroMsgEditar = null }
    if (sucessoMsgEditar != null) ui.SucessoDialog(sucessoMsgEditar!!) { sucessoMsgEditar = null; onVoltar() }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar Viagem",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            erro != null && viagem == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(erro!!, color = AppColors.Error)
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .verticalScroll(scrollState).padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Placa", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(expanded = placaExpandida, onExpandedChange = { placaExpandida = it }) {
                            OutlinedTextField(value = placa, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placaExpandida) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                            ExposedDropdownMenu(expanded = placaExpandida, onDismissRequest = { placaExpandida = false }) {
                                equipamentos.forEach { eq -> DropdownMenuItem(text = { Text(eq.placa) }, onClick = { placa = eq.placa; placaExpandida = false }) }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("Rota", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(expanded = destinoExpandido, onExpandedChange = { destinoExpandido = it }) {
                            OutlinedTextField(value = destinos.find { it.id == destinoId }?.nome ?: "", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinoExpandido) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                            ExposedDropdownMenu(expanded = destinoExpandido, onDismissRequest = { destinoExpandido = false }) {
                                destinos.forEach { d -> DropdownMenuItem(text = { Text(d.nome) }, onClick = { destinoId = d.id; destinoExpandido = false }) }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        CampoTexto("KM de Início:", kmInicio, { kmInicio = it }, KeyboardType.Number)
                        CampoTexto("KM de Chegada:", kmChegada, { kmChegada = it }, KeyboardType.Number)
                        CampoTexto("Data de Início:", dataViagem, { dataViagem = it })
                        CampoTexto("Data de Chegada:", dataChegada, { dataChegada = it })
                        CampoTexto("Ordem de Frete:", numerobd, { numerobd = it })
                        CampoTexto("Ordem de Frete 2:", numerobd2, { numerobd2 = it })
                        CampoTexto("CTE:", cte, { cte = it })
                        CampoTexto("CTE 2:", cte2, { cte2 = it })
                        CampoTexto("Ordem Retorno:", ordemRetorno, { ordemRetorno = it })
                        CampoTexto("CTE Retorno:", cteRetorno, { cteRetorno = it })
                        CampoTexto("Peso da Carga:", pesocarga, { pesocarga = it }, KeyboardType.Number)
                        CampoTexto("Peso Carga Retorno:", pesocargaretorno, { pesocargaretorno = it }, KeyboardType.Number)
                        CampoTexto("Valor do Frete:", valorfrete, { valorfrete = it }, KeyboardType.Decimal)
                        CampoTexto("Valor Frete Retorno:", valorfreteretorno, { valorfreteretorno = it }, KeyboardType.Decimal)

                        Text("Descrição:", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = descricao, onValueChange = { descricao = it }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(12.dp))

                        Spacer(Modifier.height(24.dp))

                        Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                            if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("SALVAR", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CampoTexto(label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text) {
    Text(label, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = keyboardType), singleLine = true)
    Spacer(Modifier.height(16.dp))
}

// ==================== RESUMO VIAGEM ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumoViagemContent(repository: AppRepository, viagemId: Int, onVoltar: () -> Unit) {
    val motorista = remember { repository.getMotoristaLogado() }
    var loading by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var resumo by remember { mutableStateOf<ResumoViagem?>(null) }
    val scrollState = rememberScrollState()

    LaunchedEffect(viagemId) {
        try {
            val response = ApiClient.resumoViagem(ResumoRequest(viagem_id = viagemId))
            if (response.status == "ok") resumo = response.resumo else erro = response.mensagem
        } catch (e: Exception) { erro = "Erro: ${e.message}" }
        loading = false
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Resumo da Viagem",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            erro != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text(erro!!, color = AppColors.Error) }
            resumo != null -> Column(modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Motorista: ${resumo!!.motorista_nome}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        LinhaResumo("Rota:", resumo!!.destino_nome)
                        LinhaResumo("Ordem de Frete:", resumo!!.numerobd)
                        LinhaResumo("Data Início:", formatarData(resumo!!.data_viagem))
                        LinhaResumoDataChegada(resumo!!.data_chegada)
                        LinhaResumo("KM Início:", formatarInteiro(resumo!!.km_inicio))
                        LinhaResumo("KM Chegada:", formatarInteiro(resumo!!.km_chegada))
                        LinhaResumo("KM da Rota:", formatarInteiro(resumo!!.km_da_rota) + " km")
                        LinhaResumo("KM Percorridos:", formatarInteiro(resumo!!.km_percorridos) + " km")
                        LinhaResumoDestaque(
                            "KM Ultrapassados",
                            formatarInteiro(resumo!!.km_ultrapassados) + " km",
                            if (resumo!!.km_ultrapassados > 0) AppColors.Error else AppColors.Secondary
                        )

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Text("Combustíveis", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))

                        LinhaResumoDestaque("Diesel Caminhão:", "${formatarNumero(resumo!!.litros_diesel_caminhao)} L", if (resumo!!.litros_diesel_caminhao > resumo!!.litros_rota) AppColors.Error else AppColors.Secondary)
                        LinhaResumo("Diesel Aparelho:", "${formatarNumero(resumo!!.litros_diesel_aparelho)} L")
                        LinhaResumo("ARLA:", "${formatarNumero(resumo!!.litros_arla)} L")
                        LinhaResumo("Litros Pedido:", "${formatarNumero(resumo!!.litros_rota)} L")

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Text("Médias", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))

                        LinhaResumoDestaque("Média Real:", "${formatarNumero(resumo!!.media_consumo)} KM/L", if (resumo!!.media_consumo < resumo!!.media_rota) AppColors.Error else AppColors.Secondary)
                        LinhaResumo("Média Pedida:", "${formatarNumero(resumo!!.media_rota)} KM/L")
                        LinhaResumo("Média ARLA:", "${formatarNumero(resumo!!.media_arla)} KM/L")
                        LinhaResumoDestaque("Média Horas Aparelho:", "(Horas: ${formatarNumero(resumo!!.soma_horas)}h) ${formatarNumero(resumo!!.media_aparelho)} h/l", AppColors.Secondary)
                        LinhaResumoDestaque("% Frete p/ Óleo:", "${formatarNumero(resumo!!.porcentagem_oleo)}%", AppColors.Orange)

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Text("Valores", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))

                        LinhaResumo("Diesel Caminhão:", formatarMoeda(resumo!!.valor_diesel_caminhao))
                        LinhaResumo("Diesel Aparelho:", formatarMoeda(resumo!!.valor_diesel_aparelho))
                        LinhaResumo("ARLA:", formatarMoeda(resumo!!.valor_arla))
                        LinhaResumo("Descarga:", formatarMoeda(resumo!!.valor_descarga))
                        LinhaResumo("Comissão:", formatarMoeda(resumo!!.comissao))
                        LinhaResumo("Total Despesas:", formatarMoeda(resumo!!.total_despesas))

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        LinhaResumo("Valor Frete:", formatarMoeda(resumo!!.valor_frete))
                        LinhaResumo("Frete Retorno:", formatarMoeda(resumo!!.valor_frete_retorno))
                        LinhaResumo("Total Frete:", formatarMoeda(resumo!!.saldo_frete))
                        LinhaResumoDestaque("Saldo Viagem:", formatarMoeda(resumo!!.saldo_viagem), if (resumo!!.saldo_viagem >= 0) AppColors.Secondary else AppColors.Error)

                        if (resumo!!.descricao.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            Text("Descrição:", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(resumo!!.descricao, color = AppColors.TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LinhaResumo(label: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AppColors.TextSecondary)
        Text(valor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LinhaResumoDestaque(label: String, valor: String, cor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AppColors.TextSecondary)
        Surface(color = cor.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
            Text(valor, color = cor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

// ==================== DESPESAS VIAGEM ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DespesasViagemContent(
    repository: AppRepository,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var viagemAberta by remember { mutableStateOf(false) }
    var abastecimentos by remember { mutableStateOf<List<AbastecimentoItem>>(emptyList()) }
    var arla by remember { mutableStateOf<List<ArlaItem>>(emptyList()) }
    var descargas by remember { mutableStateOf<List<DescargaItem>>(emptyList()) }
    val scrollState = rememberScrollState()

    fun carregarDados() {
        scope.launch {
            loading = true
            erro = null
            try {
                val response = ApiClient.despesasViagem(DespesasRequest(viagem_id = viagemId))
                if (response.status == "ok") {
                    viagemAberta = response.viagem_aberta
                    abastecimentos = response.abastecimentos
                    arla = response.arla
                    descargas = response.descargas
                } else erro = response.mensagem
            } catch (e: Exception) { erro = "Erro: ${e.message}" }
            loading = false
        }
    }

    LaunchedEffect(viagemId) {
        carregarDados()
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Despesas",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            erro != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { carregarDados() }) { Text("Tentar Novamente") }
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {

                if (viagemAberta) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFF10B981))
                            Spacer(Modifier.width(8.dp))
                            Text("Viagem em andamento", color = Color(0xFF10B981), fontSize = 13.sp)
                        }
                    }
                }

                SecaoHeader("Abastecimentos", Icons.Default.LocalGasStation, AppColors.Primary)
                if (abastecimentos.isEmpty()) {
                    CardVazio("Nenhum abastecimento")
                } else {
                    abastecimentos.forEach { item ->
                        CardDespesaSimples(
                            titulo = formatarData(item.data),
                            subtitulo = "Posto: ${item.posto}",
                            tipo = item.tipo,
                            litros = formatarNumero(item.litros),
                            valor = formatarMoeda(item.valor),
                            cor = AppColors.Primary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SecaoHeader("ARLA", Icons.Default.Water, Color(0xFF06B6D4))
                if (arla.isEmpty()) {
                    CardVazio("Nenhum registro de ARLA")
                } else {
                    arla.forEach { item ->
                        CardDespesaSimples(
                            titulo = formatarData(item.data),
                            subtitulo = "Posto: ${item.posto}",
                            tipo = null,
                            litros = formatarNumero(item.litros),
                            valor = formatarMoeda(item.valor),
                            cor = Color(0xFF06B6D4)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SecaoHeader("Descarga", Icons.Default.LocalShipping, AppColors.Orange)
                if (descargas.isEmpty()) {
                    CardVazio("Nenhum registro de descarga")
                } else {
                    descargas.forEach { item ->
                        CardDespesaDescarga(
                            data = formatarData(item.data),
                            valor = formatarMoeda(item.valor)
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CardDespesaSimples(
    titulo: String,
    subtitulo: String,
    tipo: String?,
    litros: String,
    valor: String,
    cor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(titulo, fontWeight = FontWeight.Bold)
                if (tipo != null) {
                    Text(tipo, color = cor, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(subtitulo, color = AppColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$litros L", color = AppColors.TextSecondary)
                Text(valor, fontWeight = FontWeight.Bold, color = cor)
            }
        }
    }
}

@Composable
private fun CardDespesaDescarga(data: String, valor: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(data, fontWeight = FontWeight.Bold)
                Text(valor, fontWeight = FontWeight.Bold, color = AppColors.Orange)
            }
        }
    }
}

@Composable
private fun SecaoHeader(titulo: String, icon: androidx.compose.ui.graphics.vector.ImageVector, cor: Color) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = cor)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun CardVazio(texto: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(texto, color = AppColors.TextSecondary)
        }
    }
}

private fun formatarData(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else data
    } catch (e: Exception) { data }
}

private fun formatarNumero(valor: Double): String {
    return valor.toString().let {
        val parts = it.split(".")
        if (parts.size == 2) {
            "${parts[0]},${parts[1].take(2).padEnd(2, '0')}"
        } else {
            "$it,00"
        }
    }
}

private fun formatarInteiro(valor: Double): String {
    return valor.toInt().toString()
}
private fun formatarInteiro(valor: Int): String {
    return valor.toString()
}

private fun formatarMoeda(valor: Double): String {
    val valorStr = valor.toString()
    val parts = valorStr.split(".")
    val integerPart = parts[0].reversed().chunked(3).joinToString(".").reversed()
    val decimalPart = if (parts.size > 1) parts[1].take(2).padEnd(2, '0') else "00"
    return "R$ $integerPart,$decimalPart"
}