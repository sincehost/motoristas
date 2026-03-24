package screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import api.*
import database.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import java.io.File
import java.io.FileOutputStream

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

@OptIn(ExperimentalMaterial3Api::class)

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

// Função para converter mensagens técnicas em mensagens amigáveis
private fun formatarMensagemErro(erro: String): String {
    return when {
        erro.contains("Unable to resolve host", ignoreCase = true) ||
                erro.contains("no address associated with hostname", ignoreCase = true) ||
                erro.contains("UnknownHostException", ignoreCase = true) ->
            "Sem conexão com a internet. Verifique sua conexão e tente novamente."

        erro.contains("timeout", ignoreCase = true) ||
                erro.contains("timed out", ignoreCase = true) ->
            "Tempo esgotado. Sua internet pode estar lenta."

        erro.contains("connection refused", ignoreCase = true) ->
            "Não foi possível conectar ao servidor. Tente novamente em instantes."

        erro.contains("network", ignoreCase = true) ||
                erro.contains("conectividade", ignoreCase = true) ->
            "Problema de conexão. Verifique sua internet."

        erro.contains("Failed to connect", ignoreCase = true) ->
            "Falha na conexão. Verifique sua internet e tente novamente."

        erro.isEmpty() ->
            "Ocorreu um erro inesperado. Tente novamente."

        else -> erro
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

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = mensagem,
                duration = if (isErro) SnackbarDuration.Long else SnackbarDuration.Short
            )
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
                    viagens = response.viagens.sortedWith(
                        compareBy<ViagemItem> { it.finalizada }
                            .thenByDescending { it.data_viagem }
                    )
                    paginaAtual = response.page
                    totalPaginas = response.total_pages
                    totalViagens = response.total
                } else {
                    erro = formatarMensagemErro(response.mensagem ?: "Erro ao carregar viagens")
                }
            } catch (e: Exception) {
                erro = formatarMensagemErro(e.message ?: "Erro desconhecido")
            }
            loading = false
            isRefreshing = false
        }
    }

    fun excluirViagem(viagem: ViagemItem) {
        scope.launch {
            excluindo = true
            try {
                val response = ApiClient.excluirViagem(
                    ExcluirViagemRequest(viagem_id = viagem.id)
                )
                if (response.status == "ok") {
                    // Excluir do banco local também
                    repository.excluirViagemLocal(viagem.id.toLong())

                    // Limpar viagem atual se for a que está em andamento
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
                    mostrarMensagem(formatarMensagemErro(response.mensagem ?: "Erro ao excluir"), isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem(formatarMensagemErro(e.message ?: "Erro ao excluir"), isErro = true)
            }
            excluindo = false
        }
    }

    LaunchedEffect(Unit) {
        carregarViagens(1)
    }

    // Modal de Ações
    if (mostrarModalAcoes && viagemSelecionada != null) {
        Dialog(onDismissRequest = { mostrarModalAcoes = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Ações da Viagem", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { mostrarModalAcoes = false; onResumo(viagemSelecionada!!.id) },
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
                        onClick = { mostrarModalAcoes = false; onDespesas(viagemSelecionada!!.id) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Orange)
                    ) {
                        Icon(Icons.Default.Receipt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Visualizar Despesas")
                    }

                    Spacer(Modifier.height(12.dp))


                    OutlinedButton(
                        onClick = { mostrarModalAcoes = false },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Voltar")
                    }
                }
            }
        }
    }

    // Confirmação de exclusão
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

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Minhas Viagens",
                onBackClick = onVoltar
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (data.visuals.message.contains("sucesso", ignoreCase = true) ||
                        data.visuals.message.contains("excluída", ignoreCase = true))
                        AppColors.Secondary else AppColors.Error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    )
    { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                carregarViagens(paginaAtual)
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(modifier = Modifier.fillMaxSize().background(AppColors.Background)) {

            if (!loading && erro == null) {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center, fontSize = 16.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { carregarViagens(paginaAtual) }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tentar novamente")
                        }
                    }
                }
                viagens.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, null, tint = AppColors.TextSecondary, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhuma viagem encontrada", color = AppColors.TextSecondary, fontSize = 16.sp)
                    }
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
        } // PullToRefreshBox
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

            // Sempre mostra separador e botões
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = AppColors.Background)
            Spacer(Modifier.height(16.dp))

            // Editar aparece SEMPRE, Excluir só em viagens em andamento
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEditar, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontSize = 13.sp)
                }

                // Botão Excluir só aparece se NÃO finalizada
                if (!viagem.finalizada) {
                    Button(onClick = onExcluir, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Excluir", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Botão Ações - aparece em TODAS as viagens
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

    // Campos editáveis
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
    val snackbarHostState = remember { SnackbarHostState() }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = mensagem,
                duration = if (isErro) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    // Carrega dados
    LaunchedEffect(viagemId) {
        try {
            val response = ApiClient.detalheViagem(ViagemDetalheRequest(viagem_id = viagemId))
            if (response.status == "ok" && response.viagem != null) {
                viagem = response.viagem
                destinos = response.destinos
                equipamentos = response.equipamentos

                // Preenche campos
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
                erro = formatarMensagemErro(response.mensagem ?: "Erro ao carregar")
            }
        } catch (e: Exception) {
            erro = formatarMensagemErro(e.message ?: "Erro desconhecido")
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
                    mostrarMensagem("Viagem atualizada com sucesso!")
                    kotlinx.coroutines.delay(1500)
                    onVoltar()
                } else {
                    mostrarMensagem(formatarMensagemErro(response.mensagem ?: "Erro ao salvar"), isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem(formatarMensagemErro(e.message ?: "Erro ao salvar"), isErro = true)
            }
            salvando = false
        }
    }

    var destinoExpandido by remember { mutableStateOf(false) }
    var placaExpandida by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar Viagem",
                onBackClick = onVoltar
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (data.visuals.message.contains("sucesso", ignoreCase = true) ||
                        data.visuals.message.contains("atualizada", ignoreCase = true))
                        AppColors.Secondary else AppColors.Error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            erro != null && viagem == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center, fontSize = 16.sp)
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        // Placa
                        Text("Placa", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(expanded = placaExpandida, onExpandedChange = { placaExpandida = it }) {
                            OutlinedTextField(value = placa, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placaExpandida) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                            ExposedDropdownMenu(expanded = placaExpandida, onDismissRequest = { placaExpandida = false }) {
                                equipamentos.forEach { eq -> DropdownMenuItem(text = { Text(eq.placa) }, onClick = { placa = eq.placa; placaExpandida = false }) }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Rota
                        Text("Rota", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(expanded = destinoExpandido, onExpandedChange = { destinoExpandido = it }) {
                            OutlinedTextField(value = destinos.find { it.id == destinoId }?.nome ?: "", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinoExpandido) }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                            ExposedDropdownMenu(expanded = destinoExpandido, onDismissRequest = { destinoExpandido = false }) {
                                destinos.forEach { d -> DropdownMenuItem(text = { Text(d.nome) }, onClick = { destinoId = d.id; destinoExpandido = false }) }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Campos de texto
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
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    var loading by remember { mutableStateOf(true) }
    var erro by remember { mutableStateOf<String?>(null) }
    var resumo by remember { mutableStateOf<ResumoViagem?>(null) }
    var outrasDespesas by remember { mutableStateOf<List<OutraDespesaItem>>(emptyList()) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    fun carregarDados() {
        scope.launch {
            try {
                val responseResumo = ApiClient.resumoViagem(ResumoRequest(viagem_id = viagemId))
                if (responseResumo.status == "ok") resumo = responseResumo.resumo
                else erro = formatarMensagemErro(responseResumo.mensagem ?: "Erro ao carregar resumo")
                try {
                    val responseDespesas = ApiClient.despesasViagem(DespesasRequest(viagem_id = viagemId))
                    if (responseDespesas.status == "ok") {
                        outrasDespesas = responseDespesas.outras_despesas
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                erro = formatarMensagemErro(e.message ?: "Erro desconhecido")
            }
            loading = false
            isRefreshing = false
        }
    }

    // Função para exportar PDF do resumo
    fun exportarResumoPdf(res: ResumoViagem) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint().apply { isAntiAlias = true }

            var y = 50f
            val left = 40f
            val right = 555f

            paint.textSize = 20f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor("#07275A")
            canvas.drawText("Resumo da Viagem", left, y, paint); y += 28f
            paint.textSize = 14f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.parseColor("#666666")
            canvas.drawText("Motorista: ${res.motorista_nome}", left, y, paint); y += 18f
            canvas.drawText("Rota: ${res.destino_nome}", left, y, paint); y += 18f
            canvas.drawText("Ordem: ${res.numerobd}", left, y, paint); y += 12f
            y += 8f
            paint.color = android.graphics.Color.parseColor("#CCCCCC"); paint.strokeWidth = 1f
            canvas.drawLine(left, y, right, y, paint); y += 16f

            fun drawItem(label: String, value: String) {
                paint.textSize = 11f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.parseColor("#555555")
                canvas.drawText(label, left, y, paint)
                paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor("#1A202C")
                canvas.drawText(value, 300f, y, paint); y += 18f
            }

            fun drawSection(title: String, color: String) {
                y += 6f; paint.textSize = 13f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor(color)
                canvas.drawText(title, left, y, paint); y += 20f
            }

            drawSection("Dados da Viagem", "#07275A")
            drawItem("KM Início:", formatarKm(res.km_inicio))
            drawItem("KM Chegada:", formatarKm(res.km_chegada))
            drawItem("KM da Rota:", "${formatarInteiro(res.km_da_rota)} km")
            drawItem("KM Percorridos:", "${formatarInteiro(res.km_percorridos)} km")
            drawItem("KM Ultrapassados:", "${formatarInteiro(res.km_ultrapassados)} km")

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

            y += 4f
            paint.color = android.graphics.Color.parseColor("#CCCCCC"); canvas.drawLine(left, y, right, y, paint); y += 16f
            drawItem("Total Despesas:", formatarMoeda(res.total_despesas + outrasDespesas.sumOf { it.valor }))

            drawSection("Faturamento", "#07275A")
            drawItem("Valor Frete:", formatarMoeda(res.valor_frete))
            drawItem("Frete Retorno:", formatarMoeda(res.valor_frete_retorno))
            drawItem("Total Frete:", formatarMoeda(res.saldo_frete))

            y += 8f
            val saldoColor = if (res.saldo_viagem >= 0) "#10B981" else "#EF4444"
            paint.textSize = 16f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor(saldoColor)
            canvas.drawText("SALDO: ${formatarMoeda(res.saldo_viagem)}", left, y, paint)

            document.finishPage(page)

            val fileName = "resumo_viagem_${viagemId}_${res.destino_nome.replace(" ", "_").take(20)}.pdf"
            val file = File(context.cacheDir, fileName)
            document.writeTo(FileOutputStream(file))
            document.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Abrir resumo PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(viagemId) {
        carregarDados()
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Resumo da Viagem",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                carregarDados()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            erro != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center, fontSize = 16.sp)
                }
            }
            resumo != null -> Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {
                // Card principal
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
                        LinhaResumo("KM Início:", formatarKm(resumo!!.km_inicio))
                        LinhaResumo("KM Chegada:", formatarKm(resumo!!.km_chegada))
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
                        // Badge Média Horas Aparelho com ícone de relógio
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Média Aparelho:", color = AppColors.TextSecondary)
                            }
                            Surface(color = if (ui.isDark()) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = AppColors.Secondary, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${formatarNumero(resumo!!.soma_horas)}h", color = AppColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("${formatarNumero(resumo!!.media_aparelho)} h/l", color = AppColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
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

                        // Outras despesas — detalhadas por tipo com separação visual
                        if (outrasDespesas.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFFE5E7EB))
                            Spacer(Modifier.height(8.dp))
                            Text("Outras Despesas", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF6F00))
                            Spacer(Modifier.height(6.dp))
                            val totalOutras = outrasDespesas.sumOf { it.valor }
                            val porTipo = outrasDespesas.groupBy { it.tipo }
                            porTipo.forEach { (tipo, itens) ->
                                val subtotal = itens.sumOf { it.valor }
                                LinhaResumo("  $tipo (${itens.size}x):", formatarMoeda(subtotal))
                            }
                            LinhaResumoDestaque("Total Outras:", formatarMoeda(totalOutras), Color(0xFFFF6F00))
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFE5E7EB))
                        Spacer(Modifier.height(8.dp))
                        LinhaResumoDestaque("Total Despesas:", formatarMoeda(resumo!!.total_despesas + (if (outrasDespesas.isNotEmpty()) outrasDespesas.sumOf { it.valor } else 0.0)), AppColors.Error)

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

                        // Fotos do painel
                        val temFotoSaida = resumo!!.foto_painel_saida.isNotEmpty()
                        val temFotoChegada = resumo!!.foto_painel_chegada.isNotEmpty()
                        if (temFotoSaida || temFotoChegada) {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(16.dp))
                            Text("Fotos do Painel", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (temFotoSaida) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Saída", fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(4.dp))
                                        FotoBase64Card(resumo!!.foto_painel_saida)
                                    }
                                }
                                if (temFotoChegada) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Chegada", fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(4.dp))
                                        FotoBase64Card(resumo!!.foto_painel_chegada)
                                    }
                                }
                                if (temFotoSaida && !temFotoChegada) Spacer(modifier = Modifier.weight(1f))
                                if (!temFotoSaida && temFotoChegada) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                // Botão Exportar PDF
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { exportarResumoPdf(resumo!!) },
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
        } // PullToRefreshBox
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
    val snackbarHostState = remember { SnackbarHostState() }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Estados para diálogos de confirmação
    var mostrarDialogoExcluir by remember { mutableStateOf(false) }
    var tipoExclusao by remember { mutableStateOf("") }
    var idExclusao by remember { mutableStateOf(0) }
    var excluindo by remember { mutableStateOf(false) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = mensagem,
                duration = if (isErro) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

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
                    outrasDespesas = response.outras_despesas
                } else erro = formatarMensagemErro(response.mensagem ?: "Erro ao carregar despesas")
            } catch (e: Exception) {
                erro = formatarMensagemErro(e.message ?: "Erro desconhecido")
            }
            loading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(viagemId) {
        carregarDados()
    }

    // Função para excluir
    fun executarExclusao() {
        scope.launch {
            excluindo = true
            try {
                val response = when (tipoExclusao) {
                    "abastecimento" -> {
                        val request = ExcluirDespesaRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            id = idExclusao
                        )
                        ApiClient.excluirAbastecimento(request)
                    }
                    "arla" -> {
                        val request = ExcluirDespesaRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            id = idExclusao
                        )
                        ApiClient.excluirArla(request)
                    }
                    "descarga" -> {
                        val request = ExcluirDescargaRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            descarga_id = idExclusao
                        )
                        ApiClient.excluirDescarga(request)
                    }
                    "outra_despesa" -> {
                        val request = ExcluirOutraDespesaRequest(
                            despesa_id = idExclusao,
                            motorista_id = motorista?.motorista_id ?: ""
                        )
                        ApiClient.excluirOutraDespesa(request)
                    }
                    else -> ExcluirDespesaResponse("erro", "Tipo inválido")
                }

                if (response.status == "ok") {
                    mostrarMensagem(response.mensagem ?: "Registro excluído com sucesso!")
                    carregarDados()
                } else {
                    mostrarMensagem(formatarMensagemErro(response.mensagem ?: "Erro ao excluir"), isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem(formatarMensagemErro(e.message ?: "Erro ao excluir"), isErro = true)
            }
            excluindo = false
            mostrarDialogoExcluir = false
        }
    }

    // Diálogo de confirmação de exclusão
    if (mostrarDialogoExcluir) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarDialogoExcluir = false },
            icon = { Icon(Icons.Default.Warning, null, tint = AppColors.Error) },
            title = { Text("Confirmar Exclusão", fontWeight = FontWeight.Bold) },
            text = { Text("Tem certeza que deseja excluir este registro? Esta ação não pode ser desfeita.") },
            confirmButton = {
                Button(
                    onClick = { executarExclusao() },
                    enabled = !excluindo,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) {
                    if (excluindo) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Excluir")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoExcluir = false }, enabled = !excluindo) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Despesas",
                onBackClick = onVoltar
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (data.visuals.message.contains("sucesso", ignoreCase = true) ||
                        data.visuals.message.contains("excluído", ignoreCase = true))
                        AppColors.Secondary else AppColors.Error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                carregarDados()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            erro != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AppColors.Error, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(erro!!, color = AppColors.Error, textAlign = TextAlign.Center, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { carregarDados() }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tentar Novamente")
                    }
                }
            }
            else -> Column(modifier = Modifier.fillMaxSize().background(AppColors.Background).verticalScroll(scrollState).padding(16.dp)) {

                // Aviso se viagem aberta
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

                // Abastecimentos
                SecaoHeader("Abastecimentos", Icons.Default.LocalGasStation, AppColors.Primary)
                if (abastecimentos.isEmpty()) {
                    CardVazio("Nenhum abastecimento")
                } else {
                    abastecimentos.forEach { item ->
                        CardDespesaAbastecimento(
                            item = item,
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarCombustivel(item.id, viagemId) },
                            onExcluir = {
                                tipoExclusao = "abastecimento"
                                idExclusao = item.id
                                mostrarDialogoExcluir = true
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ARLA
                SecaoHeader("ARLA", Icons.Default.Water, Color(0xFF06B6D4))
                if (arla.isEmpty()) {
                    CardVazio("Nenhum registro de ARLA")
                } else {
                    arla.forEach { item ->
                        CardDespesaArla(
                            item = item,
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarArla(item.id, viagemId) },
                            onExcluir = {
                                tipoExclusao = "arla"
                                idExclusao = item.id
                                mostrarDialogoExcluir = true
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Descarga
                SecaoHeader("Descarga", Icons.Default.LocalShipping, AppColors.Orange)
                if (descargas.isEmpty()) {
                    CardVazio("Nenhum registro de descarga")
                } else {
                    descargas.forEach { item ->
                        CardDespesaDescarga(
                            item = item,
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarDescarga(item.id, viagemId) },
                            onExcluir = {
                                tipoExclusao = "descarga"
                                idExclusao = item.id
                                mostrarDialogoExcluir = true
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Outras Despesas
                SecaoHeader("Outras Despesas", Icons.Default.MoreHoriz, Color(0xFFFF6F00))
                if (outrasDespesas.isEmpty()) {
                    CardVazio("Nenhuma outra despesa")
                } else {
                    outrasDespesas.forEach { item ->
                        CardDespesaOutra(
                            item = item,
                            viagemAberta = viagemAberta,
                            onEditar = { onEditarOutra(item, viagemId) },
                            onExcluir = {
                                tipoExclusao = "outra_despesa"
                                idExclusao = item.id
                                mostrarDialogoExcluir = true
                            }
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }
        } // PullToRefreshBox
    }
}

@Composable
private fun CardDespesaAbastecimento(
    item: AbastecimentoItem,
    viagemAberta: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatarData(item.data), fontWeight = FontWeight.Bold)
                Text(item.tipo, color = AppColors.Primary, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(8.dp))
            Text("Posto: ${item.posto}", color = AppColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatarNumero(item.litros)} L", color = AppColors.TextSecondary)
                Text(formatarMoeda(item.valor), fontWeight = FontWeight.Bold, color = AppColors.Primary)
            }

            // Botões de ação (só aparecem se viagem aberta)
            if (viagemAberta) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = AppColors.Primary)
                        Spacer(Modifier.width(4.dp))
                        Text("Editar", color = AppColors.Primary)
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
private fun CardDespesaArla(
    item: ArlaItem,
    viagemAberta: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(formatarData(item.data), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Posto: ${item.posto}", color = AppColors.TextSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${formatarNumero(item.litros)} L", color = AppColors.TextSecondary)
                Text(formatarMoeda(item.valor), fontWeight = FontWeight.Bold, color = Color(0xFF06B6D4))
            }

            // Botões de ação (só aparecem se viagem aberta)
            if (viagemAberta) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFE5E7EB))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEditar) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = Color(0xFF06B6D4))
                        Spacer(Modifier.width(4.dp))
                        Text("Editar", color = Color(0xFF06B6D4))
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
    item: DescargaItem,
    viagemAberta: Boolean,
    onEditar: () -> Unit,
    onExcluir: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatarData(item.data), fontWeight = FontWeight.Bold)
                Text(formatarMoeda(item.valor), fontWeight = FontWeight.Bold, color = AppColors.Orange)
            }

            // Botões de ação (só aparecem se viagem aberta)
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
private fun FotoBase64Card(base64: String) {
    val bitmap = remember(base64) {
        try {
            val cleanBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) { null }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Foto do painel",
            modifier = Modifier.fillMaxWidth().height(160.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text("Foto indisponível", color = AppColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

// ==================== UTILITÁRIOS ====================
private fun formatarData(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else data
    } catch (e: Exception) { data }
}

private fun formatarNumero(valor: Double): String {
    return String.format("%.2f", valor).replace(".", ",")
}
private fun formatarInteiro(valor: Double): String {
    return valor.toInt().toString()
}
private fun formatarInteiro(valor: Int): String {
    return valor.toString()
}
private fun formatarKm(valor: Double): String {
    // Mostra com 1 decimal: 22423.9
    val formatted = String.format("%,.1f", valor)
    return formatted.replace(",", "X").replace(".", ",").replace("X", ".")
}

private fun formatarMoeda(valor: Double): String {
    return "R$ " + String.format("%,.2f", valor).replace(",", "X").replace(".", ",").replace("X", ".")
}