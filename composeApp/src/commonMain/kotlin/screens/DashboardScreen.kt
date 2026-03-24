package screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import database.AppRepository
import kotlinx.coroutines.delay
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import sync.OfflineManager
import sync.SyncState
import sync.SyncStatus
import ui.AppColors
import util.LogWriter

// ===============================
// TELA PRINCIPAL — ROTEAMENTO
// ===============================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: AppRepository,
    onLogout: () -> Unit
) {
    // Back stack serializado como String para sobreviver quando a Activity
    // recria ao voltar da câmera. remember{} simples perde tudo nesse momento.
    // Formato: "INICIAR_VIAGEM" ou "INICIAR_VIAGEM,ADICIONAR_COMBUSTIVEL"
    var backStackStr by rememberSaveable { mutableStateOf("") }

    fun parseBackStack(): List<Screen> =
        if (backStackStr.isBlank()) emptyList()
        else backStackStr.split(",").mapNotNull { name ->
            Screen.entries.firstOrNull { it.name == name }
        }

    fun setBackStack(list: List<Screen>) {
        backStackStr = list.joinToString(",") { it.name }
    }

    val telaAtual by remember { derivedStateOf { parseBackStack().lastOrNull() } }

    // Intercepta botão voltar do Android
    PlatformBackHandler(enabled = parseBackStack().isNotEmpty()) {
        setBackStack(parseBackStack().dropLast(1))
    }

    fun navegar(tela: Screen) {
        setBackStack(parseBackStack() + tela)
    }

    fun voltar() {
        setBackStack(parseBackStack().dropLast(1))
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // Dashboard sempre vivo embaixo
        DashboardContent(
            repository = repository,
            onLogout = onLogout,
            onNavigate = { navegar(it) },
            telaAtual = telaAtual
        )

        // Animação de transição entre telas
        // SlideIn da direita ao abrir, SlideOut para direita ao fechar
        AnimatedVisibility(
            visible = telaAtual != null,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(250))
        ) {
        if (telaAtual != null) {
            key(telaAtual) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Background)
                ) {
                    when (telaAtual) {
                        Screen.INICIAR_VIAGEM -> IniciarViagemScreen(
                            repository = repository,
                            onVoltar = {
                                voltar()
                                AppEvents.emitir(AppEvent.ViagemAtualizada)
                            },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.ViagemAtualizada)
                            }
                        )
                        Screen.MINHAS_VIAGENS -> MinhasViagensScreen(
                            repository = repository,
                            onVoltar = { voltar() }
                        )
                        Screen.ADICIONAR_COMBUSTIVEL -> AdicionarCombustivelScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        Screen.ADICIONAR_ARLA -> AdicionarArlaScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        Screen.FINALIZAR_VIAGEM -> FinalizarViagemScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.ViagemFinalizada)
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        Screen.MANUTENCAO -> ManutencaoScreen(
                            repository = repository,
                            onVoltar = { voltar() }
                        )
                        Screen.MINHAS_MANUTENCOES -> MinhasManutencoesScreen(
                            repository = repository,
                            onVoltar = { voltar() }
                        )
                        Screen.RELATORIO_VIAGENS -> RelatorioViagensScreen(
                            repository = repository,
                            onVoltar = { voltar() }
                        )
                        Screen.ADICIONAR_DESCARGA -> AdicionarDescargaScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        Screen.OUTRAS_DESPESAS -> OutrasDespesasScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        Screen.CHECKLIST_PRE_VIAGEM -> ChecklistPreViagemScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        Screen.CHECKLIST_POS_VIAGEM -> ChecklistPosViagemScreen(
                            repository = repository,
                            onVoltar = { voltar() },
                            onSucesso = {
                                voltar()
                                AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            }
                        )
                        else -> {}
                    }
                }
            }
        }
        }
    }
}

// ===============================
// CONTEÚDO DO DASHBOARD
// ===============================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    repository: AppRepository,
    onLogout: () -> Unit,
    onNavigate: (Screen) -> Unit,
    telaAtual: Screen?
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val offlineManager = remember { OfflineManager(repository) }

    var pendentes by remember { mutableStateOf(repository.countTotalPendentes()) }
    var syncStatus by remember { mutableStateOf(SyncStatus()) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var tipoMensagem by remember { mutableStateOf(TipoMensagem.INFO) }
    var verificandoViagem by remember { mutableStateOf(true) }
    var modoOffline by remember { mutableStateOf(false) }
    var viagemAtualInfo by remember { mutableStateOf<String?>(null) }
    var viagemAtualId by remember { mutableStateOf<Long?>(null) }
    var temInternet by remember { mutableStateOf(true) }
    val sincronizando = syncStatus.state == SyncState.SYNCING
    val scope = rememberCoroutineScope()

    var mostrarDialogoLogout by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    // Inicializar OfflineManager com escopo de coroutine
    LaunchedEffect(Unit) {
        offlineManager.init(scope)
    }

    fun mostrarSucesso(msg: String) {
        mensagem = msg; tipoMensagem = TipoMensagem.SUCESSO
    }

    fun mostrarAviso(msg: String) {
        mensagem = msg; tipoMensagem = TipoMensagem.AVISO
    }

    fun verificarViagem() {
        scope.launch {
            offlineManager.verificarViagemAtual(
                motorista = motorista,
                onLoading = { verificandoViagem = it },
                onModoOffline = { modoOffline = it },
                onViagemEncontrada = { id, destino -> viagemAtualId = id; viagemAtualInfo = destino },
                onSemViagem = { viagemAtualId = null; viagemAtualInfo = null }
            )
        }
    }

    // CORREÇÃO C1: Usar OfflineManager.syncPendentes() em vez de SyncManager.sincronizar()
    // O OfflineManager tem lock de concorrência (isSyncing) que impede dois syncs
    // simultâneos. Antes, o Dashboard chamava SyncManager direto, ignorando esse lock.
    fun executarSync(silencioso: Boolean) {
        offlineManager.syncPendentes(
            motorista = motorista,
            silencioso = silencioso,
            onStatusUpdate = { status ->
                syncStatus = status
                pendentes = repository.countTotalPendentes()
                if (status.state == SyncState.SUCCESS) {
                    LogWriter.log("Sync OK - re-buscando viagem...")
                    verificarViagem()
                }
            }
        )
    }

    fun sincronizarManual() {
        if (syncStatus.state == SyncState.SYNCING) {
            mostrarAviso("Sincronização em andamento...")
            return
        }
        executarSync(silencioso = false)
    }

    LaunchedEffect(Unit) { temInternet = AppEvents.online }

    LaunchedEffect(mensagem) {
        if (mensagem != null && tipoMensagem == TipoMensagem.SUCESSO) {
            delay(4000)
            mensagem = null
        }
    }

    LaunchedEffect(syncStatus.state) {
        if (syncStatus.state == SyncState.SUCCESS) {
            delay(4000)
            syncStatus = syncStatus.copy(state = SyncState.IDLE, message = "")
        } else if (syncStatus.state == SyncState.ERROR || syncStatus.state == SyncState.NO_INTERNET) {
            delay(6000)
            syncStatus = syncStatus.copy(state = SyncState.IDLE, message = "")
        }
    }

    LaunchedEffect(Unit) {
        offlineManager.verificarViagemAtual(
            motorista = motorista,
            onLoading = { verificandoViagem = it },
            onModoOffline = { modoOffline = it },
            onViagemEncontrada = { id, destino -> viagemAtualId = id; viagemAtualInfo = destino },
            onSemViagem = { viagemAtualId = null; viagemAtualInfo = null }
        )
    }

    // Atualiza viagem e pendentes ao voltar para o dashboard
    LaunchedEffect(telaAtual) {
        if (telaAtual == null) {
            verificarViagem()
            pendentes = repository.countTotalPendentes()
        }
    }

    LaunchedEffect(Unit) {
        AppEvents.eventos.collect { evento ->
            when (evento) {
                is AppEvent.Conectividade -> {
                    temInternet = evento.online
                    if (evento.online && modoOffline && viagemAtualId != null) {
                        scope.launch {
                            offlineManager.verificarViagemAtual(
                                motorista = motorista,
                                onLoading = { verificandoViagem = it },
                                onModoOffline = { modoOffline = it },
                                onViagemEncontrada = { id, destino ->
                                    viagemAtualId = id; viagemAtualInfo = destino
                                    mostrarSucesso("Reconectado! Dados atualizados.")
                                },
                                onSemViagem = { viagemAtualId = null; viagemAtualInfo = null }
                            )
                        }
                    }
                    if (evento.online && pendentes > 0 && syncStatus.state == SyncState.IDLE) {
                        executarSync(silencioso = true)
                    }
                }
                is AppEvent.ViagemFinalizada -> {
                    viagemAtualId = null; viagemAtualInfo = null; modoOffline = false
                    pendentes = repository.countTotalPendentes()
                }
                is AppEvent.ViagemAtualizada -> {
                    verificarViagem(); pendentes = repository.countTotalPendentes()
                }
                is AppEvent.DadosPendentesAlterados -> {
                    pendentes = repository.countTotalPendentes()
                    if (temInternet && pendentes > 0 && syncStatus.state == SyncState.IDLE) {
                        executarSync(silencioso = true)
                    }
                }
                is AppEvent.TokenExpirado -> {
                    // Token JWT expirou — forçar re-login
                    mensagem = "Sessão expirada. Faça login novamente."
                    tipoMensagem = TipoMensagem.ERRO
                    repository.logout()
                    onLogout()
                }
            }
        }
    }

    if (mostrarDialogoLogout) {
        AlertDialog(
            onDismissRequest = { mostrarDialogoLogout = false },
            containerColor = AppColors.Surface,
            icon = {
                Icon(Icons.Default.Logout, null, tint = AppColors.Error, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Sair do aplicativo", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Text(
                    "Ao sair, seus dados locais serão removidos e você precisará fazer login novamente.\n\nDados pendentes de sincronização serão perdidos.",
                    fontSize = 14.sp, textAlign = TextAlign.Center, color = AppColors.TextSecondary, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { mostrarDialogoLogout = false; repository.logout(); onLogout() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Error)
                ) { Text("Sair", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { mostrarDialogoLogout = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                nomeMotorista = motorista?.nome?.split(" ")?.firstOrNull() ?: "",
                pendentes = pendentes,
                sincronizando = sincronizando,
                onHome = {
                    verificarViagem()
                    pendentes = repository.countTotalPendentes()
                    if (temInternet && pendentes > 0 && syncStatus.state == SyncState.IDLE) {
                        executarSync(silencioso = true)
                    }
                    mostrarSucesso("Dados atualizados!")
                },
                onSync = { sincronizarManual() },
                onLogout = { mostrarDialogoLogout = true }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                repository = repository,
                viagemAtualId = viagemAtualId,
                telaAtual = telaAtual,
                onNavigate = onNavigate,
                onMessage = { mensagem = it; tipoMensagem = TipoMensagem.AVISO }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                scope.launch {
                    verificarViagem()
                    pendentes = repository.countTotalPendentes()
                    if (temInternet && pendentes > 0 && syncStatus.state == SyncState.IDLE) {
                        executarSync(silencioso = true)
                    }
                    delay(500)
                    isRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            if (syncStatus.state != SyncState.IDLE) {
                SyncStatusCard(syncStatus)
                Spacer(Modifier.height(12.dp))
            }

            if (verificandoViagem) {
                VerificandoViagemCard()
            } else if (viagemAtualInfo != null) {
                val viagemLocal = remember(viagemAtualId) { repository.getViagemAtual() }
                viagemAtualInfo?.let { info -> viagemAtualId?.let { id ->
                    ViagemStatusCard(
                        viagemInfo = info,
                        viagemId = id,
                        modoOffline = modoOffline,
                        temInternet = temInternet,
                        kmInicio = viagemLocal?.km_inicio ?: "",
                        dataInicio = viagemLocal?.data_inicio ?: ""
                    )
                } }
            } else {
                SemViagemCard()
            }

            Spacer(Modifier.height(12.dp))

            if (!mensagem.isNullOrBlank()) {
                mensagem?.let { msg -> MensagemCard(mensagem = msg, tipo = tipoMensagem) }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))

            DashboardMenuItems(
                scope = scope,
                onNavigate = onNavigate,
                onMensagem = { mensagem = it; tipoMensagem = TipoMensagem.ERRO }
            )

            Spacer(Modifier.height(24.dp))
        }
        }
    }

}

// ===============================
// TOP BAR
// ===============================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    nomeMotorista: String,
    pendentes: Long,
    sincronizando: Boolean,
    onHome: () -> Unit,
    onSync: () -> Unit,
    onLogout: () -> Unit
) {
    // Capturar estado do tema no contexto Composable
    val currentlyDark = ui.isDarkState
    val warningColor = AppColors.Warning

    TopAppBar(
        title = {
            Column {
                Text("Olá, $nomeMotorista", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (pendentes > 0) {
                    Text("$pendentes pendente${if (pendentes > 1) "s" else ""}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onHome) {
                Icon(Icons.Default.Home, contentDescription = "Início", tint = Color.White)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        modifier = if (currentlyDark) {
            Modifier.background(Color(0xFF1A1A2E))
        } else {
            Modifier.background(Brush.horizontalGradient(listOf(ui.AppColorsLight.Primary, ui.AppColorsLight.Secondary)))
        },
        actions = {
            // Botão de tema (claro/escuro)
            IconButton(onClick = {
                ui.ThemeManager.themeMode = when (ui.ThemeManager.themeMode) {
                    ui.ThemeMode.LIGHT -> ui.ThemeMode.DARK
                    ui.ThemeMode.DARK -> ui.ThemeMode.LIGHT
                    ui.ThemeMode.SYSTEM -> if (currentlyDark) ui.ThemeMode.LIGHT else ui.ThemeMode.DARK
                }
            }) {
                Icon(
                    if (currentlyDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Alternar tema",
                    tint = Color.White
                )
            }
            BadgedBox(badge = {
                if (pendentes > 0) {
                    Badge(containerColor = warningColor, contentColor = Color.White) {
                        Text(pendentes.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }) {
                IconButton(onClick = onSync, enabled = !sincronizando) {
                    if (sincronizando) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, "Sincronizar", tint = Color.White)
                    }
                }
            }
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.Logout, "Sair", tint = Color.White)
            }
        }
    )
}

// ===============================
// ITENS DE MENU
// ===============================
@Composable
private fun DashboardMenuItems(
    scope: kotlinx.coroutines.CoroutineScope,
    onNavigate: (Screen) -> Unit,
    onMensagem: (String?) -> Unit
) {
    // CORREÇÃO A1: Permitir acesso mesmo offline.
    // Antes, se não tivesse internet, o motorista era bloqueado completamente.
    // Agora navega sempre e tenta sync em background. As telas internas
    // devem lidar com dados desatualizados mostrando aviso.
    MenuButton(Icons.Default.ListAlt, "Minhas Viagens", AppColors.Purple) {
        scope.launch {
            onMensagem(null)
            try { ApiClient.syncDados() } catch (_: Exception) { }
            onNavigate(Screen.MINHAS_VIAGENS)
        }
    }
    Spacer(Modifier.height(12.dp))
    MenuButton(Icons.Default.Handyman, "Minhas Manutenções", Color(0xFF8B5CF6)) {
        scope.launch {
            onMensagem(null)
            try { ApiClient.syncDados() } catch (_: Exception) { }
            onNavigate(Screen.MINHAS_MANUTENCOES)
        }
    }
    Spacer(Modifier.height(12.dp))
    MenuButton(Icons.Default.Assessment, "Relatório de Viagens", AppColors.Secondary) {
        scope.launch {
            onMensagem(null)
            try { ApiClient.syncDados() } catch (_: Exception) { }
            onNavigate(Screen.RELATORIO_VIAGENS)
        }
    }
    Spacer(Modifier.height(12.dp))
    MenuButton(Icons.Default.Checklist, "Checklist Pré-Viagem", Color(0xFF0097A7)) {
        scope.launch { onMensagem(null); onNavigate(Screen.CHECKLIST_PRE_VIAGEM) }
    }
    Spacer(Modifier.height(12.dp))
    MenuButton(Icons.Default.FactCheck, "Checklist Pós-Viagem", Color(0xFF10B981)) {
        scope.launch { onMensagem(null); onNavigate(Screen.CHECKLIST_POS_VIAGEM) }
    }
}
