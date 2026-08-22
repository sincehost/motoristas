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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppAlertDialog
import ui.AppColors
import ui.GradientTopBar
import util.formatarKmInput
import util.normalizarKmParaEnvio
import util.formatarKmExibicao
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*
import platform.CoreGraphics.*
import platform.Foundation.*

// Estados de navegação
private sealed class TelaViagem {
    object Lista : TelaViagem()
    data class Editar(val viagemId: Int) : TelaViagem()
    data class Resumo(val viagemId: Int) : TelaViagem()
    data class Despesas(val viagemId: Int) : TelaViagem()
    data class EditarCombustivel(val abastecimentoId: Int, val viagemId: Int) : TelaViagem()
    data class EditarArla(val arlaId: Int, val viagemId: Int) : TelaViagem()
    data class EditarDescarga(val descargaId: Int, val viagemId: Int) : TelaViagem()
    data class EditarOutraDespesa(val item: OutraDespesaItem, val viagemId: Int) : TelaViagem()
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
            onVoltar = { telaAtual = TelaViagem.Lista },
            onEditarCombustivel = { abastId, viagId -> telaAtual = TelaViagem.EditarCombustivel(abastId, viagId) },
            onEditarArla = { arlaId, viagId -> telaAtual = TelaViagem.EditarArla(arlaId, viagId) },
            onEditarDescarga = { descId, viagId -> telaAtual = TelaViagem.EditarDescarga(descId, viagId) },
            onEditarOutra = { item, viagId -> telaAtual = TelaViagem.EditarOutraDespesa(item, viagId) }
        )
        is TelaViagem.EditarCombustivel -> EditarCombustivelScreen(
            repository = repository,
            abastecimentoId = tela.abastecimentoId,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Despesas(tela.viagemId) }
        )
        is TelaViagem.EditarArla -> EditarArlaScreen(
            repository = repository,
            arlaId = tela.arlaId,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Despesas(tela.viagemId) }
        )
        is TelaViagem.EditarDescarga -> EditarDescargaScreen(
            repository = repository,
            descargaId = tela.descargaId,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Despesas(tela.viagemId) }
        )
        is TelaViagem.EditarOutraDespesa -> EditarOutraDespesaScreen(
            repository = repository,
            item = tela.item,
            viagemId = tela.viagemId,
            onVoltar = { telaAtual = TelaViagem.Despesas(tela.viagemId) }
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
                    ExcluirViagemRequest(viagem_id = viagem.id, motorista_id = motorista?.motorista_id ?: "")
                )
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
        AppAlertDialog(
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
        AppAlertDialog(
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
    var veiculoId by remember { mutableStateOf(-1) }
    var implemento1Id by remember { mutableStateOf(-1) }
    var implemento1Placa by remember { mutableStateOf("") }
    var implemento2Id by remember { mutableStateOf(-1) }
    var implemento2Placa by remember { mutableStateOf("") }
    var dataViagem by remember { mutableStateOf("") }
    var dataChegada by remember { mutableStateOf("") }
    var kmInicio by remember { mutableStateOf(TextFieldValue("")) }
    var kmChegada by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf("") }
    var pesocarga by remember { mutableStateOf(TextFieldValue("")) }
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
            val response = ApiClient.detalheViagem(ViagemDetalheRequest(viagem_id = viagemId, motorista_id = motorista?.motorista_id ?: ""))
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
                veiculoId = response.viagem.veiculo_id ?: -1
                implemento1Id = response.viagem.implemento1_id ?: -1
                implemento1Placa = response.viagem.implemento1_placa ?: ""
                implemento2Id = response.viagem.implemento2_id ?: -1
                implemento2Placa = response.viagem.implemento2_placa ?: ""
                dataViagem = response.viagem.data_viagem
                dataChegada = response.viagem.data_chegada
                // Servidor devolve decimal puro do banco DECIMAL(10,1) ("115676.0")
                // — normaliza pro padrão canônico "XXXXXX.X" antes de exibir.
                val kmInicioTexto = response.viagem.km_inicio.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: response.viagem.km_inicio
                kmInicio = TextFieldValue(kmInicioTexto, selection = TextRange(kmInicioTexto.length))
                kmChegada = response.viagem.km_chegada.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: response.viagem.km_chegada
                kmPosto = response.viagem.km_posto.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: response.viagem.km_posto
                // Servidor devolve decimal puro ("20000.00") — filtrar só
                // dígitos concatenava a parte decimal junto (virava
                // "2000000"), estourando o milhar errado ("2.000.000").
                // Precisa parsear como número primeiro, peso não tem casa
                // decimal nesse app.
                val pesoDigitos = response.viagem.pesocarga.toDoubleOrNull()?.toLong()?.toString()
                    ?: response.viagem.pesocarga.filter { it.isDigit() }
                val pesocargaTexto = formatarPesoView(pesoDigitos)
                pesocarga = TextFieldValue(pesocargaTexto, selection = TextRange(pesocargaTexto.length))
                pesocargaretorno = response.viagem.pesocargaretorno
                // Servidor devolve decimal puro ("17000.00") — converte pra
                // máscara BR ("17.000,00") igual todo campo de valor do app,
                // senão reenviar sem editar manda formato errado de volta pro
                // endpoint de atualizar viagem (que espera BR nesse campo).
                valorfrete = decimalParaMascaraBRViagem(response.viagem.valorfrete)
                valorfreteretorno = decimalParaMascaraBRViagem(response.viagem.valorfreteretorno)
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
                    motorista_id = motorista?.motorista_id ?: "",
                    numerobd = numerobd, numerobd2 = numerobd2, cte = cte, cte2 = cte2,
                    destino_id = destinoId, placa = placa,
                    veiculo_id = veiculoId.takeIf { it > 0 },
                    implemento1_id = implemento1Id.takeIf { it > 0 },
                    implemento2_id = implemento2Id.takeIf { it > 0 },
                    data_viagem = dataViagem, data_chegada = dataChegada,
                    km_inicio = normalizarKmParaEnvio(kmInicio.text),
                    km_chegada = if (kmChegada.isNotBlank()) normalizarKmParaEnvio(kmChegada) else kmChegada,
                    km_posto = if (kmPosto.isNotBlank()) normalizarKmParaEnvio(kmPosto) else kmPosto,
                    pesocarga = pesocarga.text.filter { it.isDigit() }, pesocargaretorno = pesocargaretorno, valorfrete = valorfrete, valorfreteretorno = valorfreteretorno,
                    ordem_retorno = ordemRetorno, cte_retorno = cteRetorno, descricao = descricao
                ))
                if (response.status == "ok") {
                    // Se essa é a viagem em andamento, atualiza o cache local
                    // (ViagemAtual) também — senão o Dashboard e a tela
                    // Finalizar Viagem continuam mostrando o KM antigo até a
                    // viagem ser finalizada, mesmo já tendo salvo o novo no
                    // servidor.
                    val viagemAtualCache = repository.getViagemAtual()
                    if (viagemAtualCache != null && viagemAtualCache.viagem_id == viagemId.toLong()) {
                        repository.salvarViagemAtual(
                            viagemId = viagemAtualCache.viagem_id,
                            destino = viagemAtualCache.destino,
                            dataInicio = viagemAtualCache.data_inicio,
                            kmInicio = normalizarKmParaEnvio(kmInicio.text),
                            kmRota = viagemAtualCache.km_rota
                        )
                    }
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
    var implemento1Expandida by remember { mutableStateOf(false) }
    var implemento2Expandida by remember { mutableStateOf(false) }
    // Sem categoria classificada ainda (migração não rodou nesse tenant), a
    // API devolve tudo com categoria == null — nesse caso mantém a lista
    // completa no seletor principal em vez de deixar sem opção nenhuma.
    val veiculosEdit = remember(equipamentos) {
        equipamentos.filter { it.categoria == "veiculo" || it.categoria == null }
    }
    val implementosEdit = remember(equipamentos) {
        equipamentos.filter { it.categoria == "implemento" }
    }

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
                        Text("Veículo", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ui.AppDropdownField(
                            label = "",
                            selectedText = placa,
                            expanded = placaExpandida,
                            onExpandedChange = { placaExpandida = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            veiculosEdit.forEach { eq ->
                                ui.AppDropdownMenuItem(text = { Text(eq.placa) }, onClick = { placa = eq.placa; veiculoId = eq.id; placaExpandida = false })
                            }
                        }

                        if (implementosEdit.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text("Implemento 1 (opcional)", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            ui.AppDropdownField(
                                label = "",
                                selectedText = implemento1Placa,
                                expanded = implemento1Expandida,
                                onExpandedChange = { implemento1Expandida = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ui.AppDropdownMenuItem(text = { Text("Nenhum") }, onClick = { implemento1Id = -1; implemento1Placa = ""; implemento1Expandida = false })
                                implementosEdit.filter { it.id != implemento2Id }.forEach { eq ->
                                    ui.AppDropdownMenuItem(text = { Text(eq.placa) }, onClick = { implemento1Id = eq.id; implemento1Placa = eq.placa; implemento1Expandida = false })
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Implemento 2 (opcional)", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            ui.AppDropdownField(
                                label = "",
                                selectedText = implemento2Placa,
                                expanded = implemento2Expandida,
                                onExpandedChange = { implemento2Expandida = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ui.AppDropdownMenuItem(text = { Text("Nenhum") }, onClick = { implemento2Id = -1; implemento2Placa = ""; implemento2Expandida = false })
                                implementosEdit.filter { it.id != implemento1Id }.forEach { eq ->
                                    ui.AppDropdownMenuItem(text = { Text(eq.placa) }, onClick = { implemento2Id = eq.id; implemento2Placa = eq.placa; implemento2Expandida = false })
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("Rota", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ui.AppDropdownField(
                            label = "",
                            selectedText = destinos.find { it.id == destinoId }?.nome ?: "",
                            expanded = destinoExpandido,
                            onExpandedChange = { destinoExpandido = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            destinos.forEach { d ->
                                ui.AppDropdownMenuItem(text = { Text(d.nome) }, onClick = { destinoId = d.id; destinoExpandido = false })
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // KM/Data de chegada e dados de retorno não aparecem aqui: só é
                        // possível editar a viagem enquanto ela está em andamento (antes de
                        // finalizar), então esses campos ainda não existem nesse momento.
                        // Continuam sendo carregados e reenviados sem alteração (ver LaunchedEffect
                        // e salvar()) para não apagar nada caso já existam no servidor.
                        Text("KM de Início:", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = kmInicio,
                            onValueChange = { newValue ->
                                val formatted = formatarKmInput(newValue.text)
                                kmInicio = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            placeholder = { Text("Ex.: 115670.5") },
                            singleLine = true
                        )
                        Text(
                            "Digite como aparece no painel. Ex.: 115670.5",
                            fontSize = 12.sp,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        CampoTexto("Data de Início:", dataViagem, { dataViagem = it })
                        CampoTexto("Ordem de Frete:", numerobd, { numerobd = it })
                        CampoTexto("Ordem de Frete 2:", numerobd2, { numerobd2 = it })
                        CampoTexto("CTE:", cte, { cte = it })
                        CampoTexto("CTE 2:", cte2, { cte2 = it })
                        Text("Peso da Carga:", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pesocarga,
                            onValueChange = { newValue ->
                                val digits = newValue.text.filter { c -> c.isDigit() }.take(5)
                                val formatted = formatarPesoView(digits)
                                pesocarga = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(Modifier.height(16.dp))
                        CampoTexto("Valor do Frete:", valorfrete, { valorfrete = it }, KeyboardType.Decimal)

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
    var outrasDespesas by remember { mutableStateOf<List<OutraDespesaItem>>(emptyList()) }
    val scrollState = rememberScrollState()

    suspend fun carregarResumo() {
        loading = true
        erro = null
        try {
            val response = ApiClient.resumoViagem(ResumoRequest(viagem_id = viagemId, motorista_id = motorista?.motorista_id ?: ""))
            if (response.status == "ok") resumo = response.resumo else erro = response.mensagem
            try {
                val responseDespesas = ApiClient.despesasViagem(DespesasRequest(viagem_id = viagemId, motorista_id = motorista?.motorista_id ?: ""))
                if (responseDespesas.status == "ok") {
                    outrasDespesas = responseDespesas.outras_despesas
                }
            } catch (_: Exception) {}
        } catch (e: Exception) { erro = "Erro: ${e.message}" }
        loading = false
    }

    LaunchedEffect(viagemId) { carregarResumo() }
    val scopeRetry = rememberCoroutineScope()

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
            erro != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { scopeRetry.launch { carregarResumo() } }) { Text("Tentar Novamente") }
                }
            }
            resumo != null -> Column(modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Motorista: ${resumo!!.motorista_nome}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        LinhaResumo("Rota:", resumo!!.destino_nome)
                        LinhaResumo("Ordem de Frete:", resumo!!.numerobd)
                        LinhaResumo("Peso da Carga:", formatarPesoView(resumo!!.pesocarga.toDoubleOrNull()?.toLong()?.toString() ?: resumo!!.pesocarga.filter { it.isDigit() }) + " kg")
                        LinhaResumo("Data Início:", formatarData(resumo!!.data_viagem))
                        LinhaResumoDataChegada(resumo!!.data_chegada)
                        LinhaResumo("KM Início:", formatarKmExibicao(resumo!!.km_inicio))
                        LinhaResumo("KM Chegada:", formatarKmExibicao(resumo!!.km_chegada))
                        LinhaResumo("KM da Rota:", formatarKmExibicao(resumo!!.km_da_rota) + " km")
                        LinhaResumo("KM Percorridos:", formatarKmExibicao(resumo!!.km_percorridos) + " km")
                        LinhaResumoDestaque(
                            "KM Ultrapassados",
                            formatarKmExibicao(resumo!!.km_ultrapassados) + " km",
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

                        if (outrasDespesas.isNotEmpty()) {
                            val totalOutras = outrasDespesas.sumOf { it.valor }
                            val porTipo = outrasDespesas.groupBy { it.tipo }
                            porTipo.forEach { (tipo, itens) ->
                                LinhaResumo("$tipo (${itens.size}x):", formatarMoeda(itens.sumOf { it.valor }))
                            }
                            LinhaResumoDestaque("Total Outras:", formatarMoeda(totalOutras), Color(0xFFFF6F00))
                        }

                        LinhaResumo("Total Despesas:", formatarMoeda(resumo!!.total_despesas + outrasDespesas.sumOf { it.valor }))

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))

                        LinhaResumo("Valor Frete:", formatarMoeda(resumo!!.valor_frete))

                        // Dados de retorno só aparecem se realmente houver carga de
                        // retorno cadastrada nessa viagem — senão fica tudo vazio/zerado
                        // poluindo o resumo à toa.
                        val temRetorno = resumo!!.pesocarga_retorno.toDoubleOrNull()?.let { it > 0 } == true ||
                            resumo!!.ordem_retorno.isNotBlank() ||
                            resumo!!.cte_retorno.isNotBlank() ||
                            resumo!!.valor_frete_retorno > 0
                        if (temRetorno) {
                            Spacer(Modifier.height(8.dp))
                            Text("Retorno", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextSecondary)
                            if (resumo!!.pesocarga_retorno.isNotBlank()) {
                                LinhaResumo(
                                    "Peso da Carga de Retorno:",
                                    formatarPesoView(resumo!!.pesocarga_retorno.toDoubleOrNull()?.toLong()?.toString() ?: resumo!!.pesocarga_retorno.filter { it.isDigit() }) + " kg"
                                )
                            }
                            if (resumo!!.ordem_retorno.isNotBlank()) {
                                LinhaResumo("Ordem de Frete de Retorno:", resumo!!.ordem_retorno)
                            }
                            if (resumo!!.cte_retorno.isNotBlank()) {
                                LinhaResumo("CT-e de Retorno:", resumo!!.cte_retorno)
                            }
                            LinhaResumo("Frete Retorno:", formatarMoeda(resumo!!.valor_frete_retorno))
                        }

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
                // Botão Exportar PDF — Android já tinha, iOS não.
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { exportarResumoPdfIos(resumo!!, outrasDespesas, viagemId) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar PDF", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(32.dp))
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
    onVoltar: () -> Unit,
    onEditarCombustivel: (Int, Int) -> Unit,
    onEditarArla: (Int, Int) -> Unit,
    onEditarDescarga: (Int, Int) -> Unit,
    onEditarOutra: (OutraDespesaItem, Int) -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var viagemAberta by remember { mutableStateOf(false) }
    var abastecimentos by remember { mutableStateOf<List<AbastecimentoItem>>(emptyList()) }
    var arla by remember { mutableStateOf<List<ArlaItem>>(emptyList()) }
    var descargas by remember { mutableStateOf<List<DescargaItem>>(emptyList()) }
    var outrasDespesas by remember { mutableStateOf<List<OutraDespesaItem>>(emptyList()) }
    val scrollState = rememberScrollState()

    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) erroMsg = mensagem else sucessoMsg = mensagem
    }

    // Estados para diálogo de confirmação de exclusão
    var mostrarDialogoExcluir by remember { mutableStateOf(false) }
    var tipoExclusao by remember { mutableStateOf("") }
    var idExclusao by remember { mutableStateOf(0) }
    var excluindo by remember { mutableStateOf(false) }

    fun carregarDados() {
        scope.launch {
            loading = true
            erro = null
            try {
                val response = ApiClient.despesasViagem(DespesasRequest(viagem_id = viagemId, motorista_id = motorista?.motorista_id ?: ""))
                if (response.status == "ok") {
                    viagemAberta = response.viagem_aberta
                    abastecimentos = response.abastecimentos
                    arla = response.arla
                    descargas = response.descargas
                    outrasDespesas = response.outras_despesas
                } else erro = response.mensagem
            } catch (e: Exception) { erro = "Erro: ${e.message}" }
            loading = false
        }
    }

    fun executarExclusao() {
        scope.launch {
            excluindo = true
            try {
                val response = when (tipoExclusao) {
                    "abastecimento" -> ApiClient.excluirAbastecimento(
                        ExcluirDespesaRequest(motorista_id = motorista?.motorista_id ?: "", id = idExclusao)
                    )
                    "arla" -> ApiClient.excluirArla(
                        ExcluirDespesaRequest(motorista_id = motorista?.motorista_id ?: "", id = idExclusao)
                    )
                    "descarga" -> ApiClient.excluirDescarga(
                        ExcluirDescargaRequest(motorista_id = motorista?.motorista_id ?: "", descarga_id = idExclusao)
                    )
                    "outra_despesa" -> ApiClient.excluirOutraDespesa(
                        ExcluirOutraDespesaRequest(despesa_id = idExclusao, motorista_id = motorista?.motorista_id ?: "")
                    )
                    else -> ExcluirDespesaResponse("erro", "Tipo inválido")
                }
                if (response.status == "ok") {
                    mostrarMensagem(response.mensagem ?: "Registro excluído com sucesso!")
                    carregarDados()
                } else {
                    mostrarMensagem(response.mensagem ?: "Erro ao excluir", isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro: ${e.message}", isErro = true)
            }
            excluindo = false
            mostrarDialogoExcluir = false
        }
    }

    LaunchedEffect(viagemId) {
        carregarDados()
    }

    // Diálogo de confirmação de exclusão
    if (mostrarDialogoExcluir) {
        AppAlertDialog(
            onDismissRequest = { if (!excluindo) mostrarDialogoExcluir = false },
            icon = { Icon(Icons.Default.Warning, null, tint = AppColors.Error) },
            title = { Text("Confirmar Exclusão", fontWeight = FontWeight.Bold) },
            text = { Text("Tem certeza que deseja excluir este registro? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(onClick = { executarExclusao() }, enabled = !excluindo, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)) {
                    if (excluindo) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Excluir")
                }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogoExcluir = false }, enabled = !excluindo) { Text("Cancelar") } }
        )
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null }

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
                            Text("Viagem em andamento - você pode editar ou excluir registros", color = Color(0xFF10B981), fontSize = 13.sp)
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
                            cor = AppColors.Primary,
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarCombustivel(item.id, viagemId) },
                            onExcluir = { tipoExclusao = "abastecimento"; idExclusao = item.id; mostrarDialogoExcluir = true }
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
                            cor = Color(0xFF06B6D4),
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarArla(item.id, viagemId) },
                            onExcluir = { tipoExclusao = "arla"; idExclusao = item.id; mostrarDialogoExcluir = true }
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
                            valor = formatarMoeda(item.valor),
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarDescarga(item.id, viagemId) },
                            onExcluir = { tipoExclusao = "descarga"; idExclusao = item.id; mostrarDialogoExcluir = true }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SecaoHeader("Outras Despesas", Icons.Default.MoreHoriz, Color(0xFFFF6F00))
                if (outrasDespesas.isEmpty()) {
                    CardVazio("Nenhuma outra despesa")
                } else {
                    outrasDespesas.forEach { item ->
                        CardDespesaOutra(
                            item = item,
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarOutra(item, viagemId) },
                            onExcluir = { tipoExclusao = "outra_despesa"; idExclusao = item.id; mostrarDialogoExcluir = true }
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
    cor: Color,
    viagemAberta: Boolean = false,
    onEditar: () -> Unit = {},
    onExcluir: () -> Unit = {}
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
            if (viagemAberta) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = cor)
                        Spacer(Modifier.width(4.dp))
                        Text("Editar", color = cor)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onExcluir) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = AppColors.Error)
                        Spacer(Modifier.width(4.dp))
                        Text("Excluir", color = AppColors.Error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardDespesaDescarga(
    data: String,
    valor: String,
    viagemAberta: Boolean = false,
    onEditar: () -> Unit = {},
    onExcluir: () -> Unit = {}
) {
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
            if (viagemAberta) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = AppColors.Orange)
                        Spacer(Modifier.width(4.dp))
                        Text("Editar", color = AppColors.Orange)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onExcluir) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = AppColors.Error)
                        Spacer(Modifier.width(4.dp))
                        Text("Excluir", color = AppColors.Error)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardDespesaOutra(
    item: OutraDespesaItem,
    viagemAberta: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    val corTipo = when (item.tipo.lowercase()) {
        "pedágio", "pedagio" -> Color(0xFF6366F1)
        "refeição", "refeicao" -> Color(0xFF10B981)
        "hospedagem" -> Color(0xFF8B5CF6)
        "lavagem" -> Color(0xFF06B6D4)
        else -> Color(0xFFFF6F00)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = corTipo.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                        Text(item.tipo, color = corTipo, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    if (item.local.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Text(item.local, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
                Text(formatarMoeda(item.valor), fontWeight = FontWeight.Bold, color = corTipo)
            }
            if (item.descricao.isNotEmpty() && item.descricao != item.tipo) {
                Spacer(Modifier.height(4.dp))
                Text(item.descricao, fontSize = 13.sp, color = AppColors.TextSecondary)
            }
            Spacer(Modifier.height(4.dp))
            Text(formatarData(item.data), fontSize = 12.sp, color = AppColors.TextSecondary)

            if (viagemAberta) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = corTipo)
                        Spacer(Modifier.width(4.dp))
                        Text("Editar", color = corTipo)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onExcluir) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = AppColors.Error)
                        Spacer(Modifier.width(4.dp))
                        Text("Excluir", color = AppColors.Error)
                    }
                }
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
/** Peso da carga: só milhar, sem decimal. "15000" -> "15.000" */
private fun formatarPesoView(digits: String): String {
    if (digits.isEmpty()) return ""
    return digits.reversed().chunked(3).joinToString(".").reversed()
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

/**
 * Converte um decimal puro vindo do servidor ("17000.00") pra máscara BR
 * ("17.000,00") igual todo campo de valor do app. Usado no Editar Viagem:
 * reenviar sem converter manda formato errado de volta pro endpoint de
 * atualizar viagem (que espera BR nesse campo), corrompendo o valor salvo.
 */
private fun decimalParaMascaraBRViagem(valorServidor: String?): String {
    if (valorServidor.isNullOrBlank()) return ""
    val numero = valorServidor.replace(",", ".").toDoubleOrNull() ?: return ""
    val negativo = numero < 0
    val centavosTotal = kotlin.math.round(kotlin.math.abs(numero) * 100).toLong()
    val reais = centavosTotal / 100
    val centavos = centavosTotal % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (negativo) "-" else "") + "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}

// ===============================
// EXPORTAR PDF (iOS) — Android já tinha isso via android.graphics.pdf.PdfDocument;
// aqui o equivalente nativo é UIGraphicsPDFRenderer (CoreGraphics/UIKit),
// desenhando o mesmo conteúdo, seção por seção, na mesma ordem do Android.
// ===============================

@OptIn(ExperimentalForeignApi::class)
private fun corHexPdf(hex: String): UIColor {
    val h = hex.removePrefix("#")
    val r = h.substring(0, 2).toInt(16).toDouble() / 255.0
    val g = h.substring(2, 4).toInt(16).toDouble() / 255.0
    val b = h.substring(4, 6).toInt(16).toDouble() / 255.0
    return UIColor(red = r, green = g, blue = b, alpha = 1.0)
}

@OptIn(ExperimentalForeignApi::class)
private fun exportarResumoPdfIos(res: ResumoViagem, outrasDespesas: List<OutraDespesaItem>, viagemId: Int) {
    try {
        val left = 40.0
        val right = 555.0
        var y = 50.0

        val pageRect = CGRectMake(0.0, 0.0, 595.0, 842.0)
        val renderer = UIGraphicsPDFRenderer(bounds = pageRect)

        fun desenharTexto(texto: String, x: Double, tamanho: Double, negrito: Boolean, cor: UIColor) {
            val fonte = if (negrito) UIFont.boldSystemFontOfSize(tamanho) else UIFont.systemFontOfSize(tamanho)
            val atributos = mapOf<Any?, Any?>(
                NSFontAttributeName to fonte,
                NSForegroundColorAttributeName to cor
            )
            (texto as NSString).drawAtPoint(CGPointMake(x, y), withAttributes = atributos)
        }

        fun desenharLinha() {
            val path = UIBezierPath()
            path.moveToPoint(CGPointMake(left, y))
            path.addLineToPoint(CGPointMake(right, y))
            path.lineWidth = 1.0
            corHexPdf("#CCCCCC").setStroke()
            path.stroke()
        }

        fun drawItem(label: String, value: String) {
            desenharTexto(label, left, 11.0, false, corHexPdf("#555555"))
            desenharTexto(value, 300.0, 11.0, true, corHexPdf("#1A202C"))
            y += 18.0
        }

        fun drawSection(title: String, cor: String) {
            y += 6.0
            desenharTexto(title, left, 13.0, true, corHexPdf(cor))
            y += 20.0
        }

        val data = renderer.PDFDataWithActions { context ->
            context?.beginPage()

            desenharTexto("Resumo da Viagem", left, 20.0, true, corHexPdf("#07275A")); y += 28.0
            desenharTexto("Motorista: ${res.motorista_nome}", left, 14.0, false, corHexPdf("#666666")); y += 18.0
            desenharTexto("Rota: ${res.destino_nome}", left, 14.0, false, corHexPdf("#666666")); y += 18.0
            desenharTexto("Ordem: ${res.numerobd}", left, 14.0, false, corHexPdf("#666666")); y += 12.0
            y += 8.0
            desenharLinha(); y += 16.0

            drawSection("Dados da Viagem", "#07275A")
            drawItem("Peso da Carga:", formatarPesoView(res.pesocarga.toDoubleOrNull()?.toLong()?.toString() ?: res.pesocarga.filter { it.isDigit() }) + " kg")
            drawItem("KM Início:", formatarKmExibicao(res.km_inicio))
            drawItem("KM Chegada:", formatarKmExibicao(res.km_chegada))
            drawItem("KM da Rota:", "${formatarKmExibicao(res.km_da_rota)} km")
            drawItem("KM Percorridos:", "${formatarKmExibicao(res.km_percorridos)} km")
            drawItem("KM Ultrapassados:", "${formatarKmExibicao(res.km_ultrapassados)} km")

            drawSection("Combustíveis", "#F59E0B")
            drawItem("Diesel Caminhão:", "${formatarNumero(res.litros_diesel_caminhao)} L")
            drawItem("Diesel Aparelho:", "${formatarNumero(res.litros_diesel_aparelho)} L")
            drawItem("ARLA:", "${formatarNumero(res.litros_arla)} L")

            drawSection("Médias", "#10B981")
            drawItem("Média Real:", "${formatarNumero(res.media_consumo)} KM/L")
            drawItem("Média Pedida:", "${formatarNumero(res.media_rota)} KM/L")
            drawItem("Média ARLA:", "${formatarNumero(res.media_arla)} KM/L")
            drawItem("Horas Aparelho:", "${formatarNumero(res.soma_horas)}h — ${formatarNumero(res.media_aparelho)} h/l")

            drawSection("Valores", "#8B5CF6")
            drawItem("Diesel Caminhão:", formatarMoeda(res.valor_diesel_caminhao))
            drawItem("Diesel Aparelho:", formatarMoeda(res.valor_diesel_aparelho))
            drawItem("ARLA:", formatarMoeda(res.valor_arla))
            drawItem("Descarga:", formatarMoeda(res.valor_descarga))
            drawItem("Comissão:", formatarMoeda(res.comissao))

            if (outrasDespesas.isNotEmpty()) {
                drawSection("Outras Despesas", "#FF6F00")
                val porTipo = outrasDespesas.groupBy { it.tipo }
                porTipo.forEach { (tipo, itens) ->
                    drawItem("$tipo (${itens.size}x):", formatarMoeda(itens.sumOf { it.valor }))
                }
                drawItem("Total Outras:", formatarMoeda(outrasDespesas.sumOf { it.valor }))
            }

            y += 4.0
            desenharLinha(); y += 16.0
            drawItem("Total Despesas:", formatarMoeda(res.total_despesas + outrasDespesas.sumOf { it.valor }))

            drawSection("Faturamento", "#07275A")
            drawItem("Valor Frete:", formatarMoeda(res.valor_frete))
            val temRetornoPdf = res.pesocarga_retorno.toDoubleOrNull()?.let { it > 0 } == true ||
                res.ordem_retorno.isNotBlank() ||
                res.cte_retorno.isNotBlank() ||
                res.valor_frete_retorno > 0
            if (temRetornoPdf) {
                if (res.pesocarga_retorno.isNotBlank()) {
                    drawItem("Peso Carga Retorno:", formatarPesoView(res.pesocarga_retorno.toDoubleOrNull()?.toLong()?.toString() ?: res.pesocarga_retorno.filter { it.isDigit() }) + " kg")
                }
                if (res.ordem_retorno.isNotBlank()) drawItem("Ordem de Retorno:", res.ordem_retorno)
                if (res.cte_retorno.isNotBlank()) drawItem("CT-e de Retorno:", res.cte_retorno)
                drawItem("Frete Retorno:", formatarMoeda(res.valor_frete_retorno))
            }
            drawItem("Total Frete:", formatarMoeda(res.saldo_frete))

            y += 8.0
            val corSaldo = if (res.saldo_viagem >= 0) "#10B981" else "#EF4444"
            desenharTexto("SALDO: ${formatarMoeda(res.saldo_viagem)}", left, 16.0, true, corHexPdf(corSaldo))
        }

        val nomeArquivo = "resumo_viagem_${viagemId}_${res.destino_nome.replace(" ", "_").take(20)}.pdf"
        val caminho = NSTemporaryDirectory() + nomeArquivo
        val sucesso = data.writeToFile(caminho, atomically = true)
        if (!sucesso) return

        compartilharPdfIos(caminho)
    } catch (e: Exception) {
        // Falha silenciosa — mesmo espírito do catch do Android, que só mostra
        // um Toast; aqui não temos acesso direto a mostrarMensagem() deste escopo.
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun compartilharPdfIos(caminho: String) {
    val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val url = NSURL.fileURLWithPath(caminho)
    val activityVC = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    // iPad exige sourceView/sourceRect no popover, senão crasha ao apresentar.
    activityVC.popoverPresentationController?.sourceView = viewController.view
    activityVC.popoverPresentationController?.sourceRect = CGRectMake(0.0, 0.0, 1.0, 1.0)
    viewController.presentViewController(activityVC, true, null)
}